package org.kenjinx.android.saves

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/* ============================= Paths ============================= */

private fun savesRootExternal(activity: Activity): File {
    // /storage/emulated/0/Android/data/<pkg>/files/bis/user/save
    val base = activity.getExternalFilesDir(null)
    return File(base, "bis/user/save")
}

private fun isHex16(name: String) =
    name.length == 16 && name.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

/* ========================= Metadata & Scan ======================== */

data class SaveFolderMeta(
    val dir: File,
    val indexHex: String,     // z.B. 0000000000000008
    val titleId: String?,     // aus TITLEID.txt (lowercase) oder geerbter Wert
    val titleName: String?,   // zweite Zeile aus TITLEID.txt, falls vorhanden
    val hasMarker: Boolean    // true, wenn dieser Ordner die TITLEID.txt selbst hat
)

/**
 * Scannt .../bis/user/save; ordnet nummerierte Ordner (16 Hex Zeichen) per „Marker-Vererbung“:
 * Ein Ordner ohne TITLEID.txt gehört zum zuletzt gesehenen Ordner mit TITLEID.txt davor.
 */
fun listSaveFolders(activity: Activity): List<SaveFolderMeta> {
    val root = savesRootExternal(activity)
    if (!root.exists()) return emptyList()

    val dirs = root.listFiles { f -> f.isDirectory && isHex16(f.name) }?.sortedBy { it.name.lowercase() }
        ?: return emptyList()

    val out = ArrayList<SaveFolderMeta>(dirs.size)
    var currentTid: String? = null
    var currentName: String? = null

    for (d in dirs) {
        val marker = File(d, "TITLEID.txt")
        val has = marker.exists()
        if (has) {
            val txt = runCatching { marker.readText(Charset.forName("UTF-8")) }.getOrElse { "" }
            val lines = txt.split('\n', '\r').map { it.trim() }.filter { it.isNotEmpty() }
            val tid = lines.getOrNull(0)?.lowercase()
            val name = lines.getOrNull(1)
            if (!tid.isNullOrBlank()) {
                currentTid = tid
                currentName = name
            }
            out += SaveFolderMeta(d, d.name, currentTid, currentName, hasMarker = true)
        } else {
            out += SaveFolderMeta(d, d.name, currentTid, currentName, hasMarker = false)
        }
    }
    return out
}

/** Sucht alle Save-Ordner (oft 1–2 Stück) für eine TitleID (case-insensitive). */
fun findSaveDirsForTitle(activity: Activity, titleId: String): List<File> {
    val tidLc = titleId.trim().lowercase(Locale.ROOT)
    return listSaveFolders(activity)
        .filter { it.titleId.equals(tidLc, ignoreCase = true) }
        .map { it.dir }
}

/** Nimmt den „höchsten“ (zuletzt erstellten) Ordner als Primärordner. */
fun pickPrimarySaveDir(activity: Activity, titleId: String): File? {
    return findSaveDirsForTitle(activity, titleId).maxByOrNull { it.name.lowercase() }
}

/* =========================== Export ============================== */

data class ExportProgress(val bytes: Long, val total: Long, val currentPath: String)
data class ExportResult(val ok: Boolean, val error: String? = null)

private fun sanitizeFileName(s: String): String =
    s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "save" }

/**
 * Schreibt eine ZIP im Format: ZIP enthält Ordner "TITLEID_UPPER/…" und darin
 * den reinen Inhalt des Ordners "0" (nicht den Ordner 0 selbst).
 * destUri kommt von ACTION_CREATE_DOCUMENT("application/zip").
 */
fun exportSaveToZip(
    activity: Activity,
    titleId: String,
    destUri: Uri,
    onProgress: (ExportProgress) -> Unit
): ExportResult {
    val tidUpper = titleId.trim().uppercase(Locale.ROOT)
    val primary = pickPrimarySaveDir(activity, titleId)
        ?: return ExportResult(false, "Save folder not found. Start game once.")

    val folder0 = File(primary, "0")
    if (!folder0.exists() || !folder0.isDirectory) {
        return ExportResult(false, "Missing '0' save folder.")
    }

    // total bytes for progress
    val files = folder0.walkTopDown().filter { it.isFile }.toList()
    val total = files.sumOf { it.length() }

    return try {
        activity.contentResolver.openOutputStream(destUri)?.use { os ->
            ZipOutputStream(os).use { zos ->
                var written = 0L
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)

                fun putFile(f: File, rel: String) {
                    val entryPath = "$tidUpper/$rel" // kein führendes "/"
                    val entry = ZipEntry(entryPath)
                    zos.putNextEntry(entry)
                    FileInputStream(f).use { inp ->
                        var n = inp.read(buf)
                        while (n > 0) {
                            zos.write(buf, 0, n)
                            written += n
                            onProgress(ExportProgress(written, total, entryPath))
                            n = inp.read(buf)
                        }
                    }
                    zos.closeEntry()
                }

                // alles aus 0/* hinein – Ordnerstruktur beibehalten
                folder0.walkTopDown().forEach { f ->
                    if (f.isFile) {
                        val rel = f.relativeTo(folder0).invariantSeparatorsPath
                        putFile(f, rel)
                    }
                }
            }
        } ?: return ExportResult(false, "Failed to open destination")
        ExportResult(true, null)
    } catch (t: Throwable) {
        Log.w("SaveFs", "exportSaveToZip failed: ${t.message}")
        ExportResult(false, t.message ?: "Export failed")
    }
}

/** Hilfsname für CreateDocument: "<Name>_save_YYYY-MM-DD.zip" */
fun buildSuggestedExportName(activity: Activity, titleId: String): String {
    val meta = pickPrimarySaveDir(activity, titleId)?.let { dir ->
        val txt = File(dir, "TITLEID.txt")
        val name = runCatching {
            txt.takeIf { it.exists() }?.readLines(Charset.forName("UTF-8"))?.getOrNull(1)
        }.getOrNull()
        name ?: "Save"
    } ?: "Save"
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return sanitizeFileName("${meta}_save_$date") + ".zip"
}

/* =========================== Import ============================== */

data class ImportProgress(val bytes: Long, val total: Long, val currentEntry: String)
data class ImportResult(val ok: Boolean, val message: String)

/**
 - Erwartet ZIP mit Struktur: TITLEID_UPPER/ (beliebige Tiefe).
 - Findet zugehörigen Save-Ordner per TITLEID.txt-Zuordnung und ersetzt den Inhalt von 0 und 1 */
fun importSaveFromZip(
    activity: Activity,
    zipUri: Uri,
    onProgress: (ImportProgress) -> Unit
): ImportResult {
    val cr: ContentResolver = activity.contentResolver

    // Gesamtlänge (falls vorhanden) für Progress
    val total = runCatching {
        cr.openAssetFileDescriptor(zipUri, "r")?.use { it.length }
    }.getOrNull() ?: -1L

    // 1) Top-Level-Verzeichnis (= TITLEID) ermitteln
    var titleIdUpper: String? = null

    // Wir lesen die ZIP zweimal nicht – stattdessen merken wir uns beim Streamen das erste Top-Level.
    // Map: relPathInZip -> ByteArray? – brauchen wir nicht, wir streamen direkt in Files.

    // 2) Zielordner finden, wenn TITLEID bekannt
    fun findTargetDirs(tidUpper: String): List<File> {
        // Suche per Marker (case-insensitive)
        val dirs = findSaveDirsForTitle(activity, tidUpper)
        if (dirs.isNotEmpty()) return dirs
        // falls nicht gefunden: evtl. lower-case in TITLEID.txt gespeichert
        val dirs2 = findSaveDirsForTitle(activity, tidUpper.lowercase(Locale.ROOT))
        return dirs2
    }

    // 3) 0/1 Ordner leeren & neu füllen
    fun replaceZeroOne(rootDir: File, entries: List<Pair<String, () -> InputStream>>): Boolean {
        val zero = File(rootDir, "0").apply { mkdirs() }
        val one = File(rootDir, "1").apply { mkdirs() }

        // Inhalt löschen (nur Inhalt, nicht Ordner)
        zero.listFiles()?.forEach { it.deleteRecursively() }
        one.listFiles()?.forEach { it.deleteRecursively() }

        val buf = ByteArray(DEFAULT_BUFFER_SIZE)
        var bytes = 0L

        fun writeAll(dstRoot: File) {
            entries.forEach { (rel, open) ->
                val dst = File(dstRoot, rel)
                dst.parentFile?.mkdirs()
                open().use { ins ->
                    dst.outputStream().use { os ->
                        var n = ins.read(buf)
                        while (n > 0) {
                            os.write(buf, 0, n)
                            bytes += n
                            onProgress(ImportProgress(bytes, total, rel))
                            n = ins.read(buf)
                        }
                    }
                }
            }
        }

        writeAll(zero)
        writeAll(one)
        return true
    }

    return try {
        val collected = mutableListOf<Pair<String, () -> InputStream>>() // relPath -> lazy stream

        cr.openInputStream(zipUri).use { raw ->
            if (raw == null) return@use
            ZipInputStream(raw).use { zis ->
                var e = zis.nextEntry
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)
                val cacheChunks = ArrayList<ByteArray>(4) // kleine Chunks pro Entry

                fun cacheToSupplier(): () -> InputStream {
                    // ByteArray zusammenführen
                    val totalLen = cacheChunks.sumOf { it.size }
                    val data = ByteArray(totalLen)
                    var pos = 0
                    cacheChunks.forEach { chunk ->
                        System.arraycopy(chunk, 0, data, pos, chunk.size)
                        pos += chunk.size
                    }
                    cacheChunks.clear()
                    return { data.inputStream() }
                }

                while (e != null) {
                    if (!e.isDirectory) {
                        val name = e.name.replace('\\', '/')
                        // Determine top-level
                        val firstSlash = name.indexOf('/')
                        if (firstSlash > 0 && titleIdUpper == null) {
                            titleIdUpper = name.substring(0, firstSlash)
                        }
                        // Nur Dateien unter TITLEID/* mitnehmen (ohne führenden Ordnernamen)
                        if (firstSlash > 0) {
                            val rel = name.substring(firstSlash + 1) // Teil nach TITLEID/
                            if (rel.isNotBlank()) {
                                // Wir buffern die Entry-Daten im Speicher (sicher für mittlere Saves)
                                var n = zis.read(buf)
                                while (n > 0) {
                                    val chunk = buf.copyOf(n)
                                    cacheChunks.add(chunk)
                                    n = zis.read(buf)
                                }
                                collected += rel to cacheToSupplier()
                            }
                        } else {
                            // Dateien direkt auf Top-Level ignorieren
                            // (die Struktur MUSS TITLEID/* sein)
                            var n = zis.read(buf)
                            while (n > 0) { n = zis.read(buf) }
                        }
                    }
                    zis.closeEntry()
                    e = zis.nextEntry
                }
            }
        }

        val tidUpper = titleIdUpper?.trim()?.uppercase(Locale.ROOT)
            ?: return ImportResult(false, "Invalid ZIP: missing top-level TITLEID")

        val targets = findTargetDirs(tidUpper)
        if (targets.isEmpty()) {
            return ImportResult(false, "error importing save. start game once.")
        }

        // Ersetze in ALLEN zugehörigen Ordnern (falls ein Spiel 2 Save-Ordner hat)
        targets.forEach { replaceZeroOne(it, collected) }

        ImportResult(true, "save imported")
    } catch (t: Throwable) {
        Log.w("SaveFs", "importSaveFromZip failed: ${t.message}")
        ImportResult(false, "error importing save. start game once.")
    }
}

/* ========================= UI Helpers ============================ */

fun suggestedCreateDocNameForExport(activity: Activity, titleId: String): String =
    buildSuggestedExportName(activity, titleId)
