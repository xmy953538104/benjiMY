package org.kenjinx.android.saves

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
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

private fun isHex16(name: String): Boolean =
    name.length == 16 && name.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' }

/* ========================= Metadata & Scan ======================== */

data class SaveFolderMeta(
    val dir: File,
    val indexHex: String,     // e.g., 0000000000000008 or 000000000000000a
    val titleId: String?,     // from TITLEID.txt (lowercase) or inherited value
    val titleName: String?,   // second line from TITLEID.txt, if present
    val hasMarker: Boolean    // true if this folder itself contains TITLEID.txt
)

/**
 * Scans .../bis/user/save; assigns numbered folders (16 hex chars) via “marker inheritance”:
 * A folder without TITLEID.txt belongs to the most recent preceding folder that does contain TITLEID.txt.
 */
fun listSaveFolders(activity: Activity): List<SaveFolderMeta> {
    val root = savesRootExternal(activity)
    if (!root.exists()) return emptyList()

    val dirs = root.listFiles { f -> f.isDirectory && isHex16(f.name) }
        ?.sortedBy { it.name.lowercase(Locale.ROOT) }
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
            val tid = lines.getOrNull(0)?.lowercase(Locale.ROOT)
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

/* ===== TitleID candidates (base/update tolerant) & grouping ===== */

private fun hex16CandidatesForSaves(id: String): List<String> {
    val lc = id.trim().lowercase(Locale.ROOT)
    if (!isHex16(lc)) return listOf(lc)
    val head = lc.substring(0, 13)     // first 13 characters
    val base = head + "000"            // ...000
    val upd  = head + "800"            // ...800
    return listOf(lc, base, upd).distinct()
}

/** All save folders of a TitleID group (marker inheritance), tolerant of base/update IDs. */
private fun listSaveGroupForTitle(activity: Activity, titleId: String): List<SaveFolderMeta> {
    val candidates = hex16CandidatesForSaves(titleId)
    val metas = listSaveFolders(activity)
    return metas.filter { meta ->
        val tid = meta.titleId ?: return@filter false
        candidates.any { it.equals(tid, ignoreCase = true) }
    }
}

/** Prefer the folder with TITLEID.txt; fallback: lexicographically smallest in the group. */
private fun pickSaveDirWithMarker(activity: Activity, titleId: String): File? {
    val group = listSaveGroupForTitle(activity, titleId)
    val marker = group.firstOrNull { it.hasMarker }?.dir
    if (marker != null) return marker
    return group.minByOrNull { it.indexHex.lowercase(Locale.ROOT) }?.dir
}

/* =========================== Export ============================== */

data class ExportProgress(val bytes: Long, val total: Long, val currentPath: String)
data class ExportResult(val ok: Boolean, val error: String? = null)

private fun sanitizeFileName(s: String): String =
    s.replace(Regex("""[\\/:*?"<>|]"""), "_").trim().ifBlank { "save" }

/**
 * Writes a ZIP with the structure: the ZIP contains folder "TITLEID_UPPER/…" and inside it
 * the raw contents of folder "0" (not the folder "0" itself).
 */
fun exportSaveToZip(
    activity: Activity,
    titleId: String,
    destUri: Uri,
    onProgress: (ExportProgress) -> Unit
): ExportResult {
    val tidUpper = titleId.trim().uppercase(Locale.ROOT)
    val primary = pickSaveDirWithMarker(activity, titleId)
        ?: return ExportResult(false, "Save folder not found. Start game once.")

    val folder0 = File(primary, "0")
    if (!folder0.exists() || !folder0.isDirectory) {
        return ExportResult(false, "Missing '0' save folder.")
    }

    val files = folder0.walkTopDown().filter { it.isFile }.toList()
    val total = files.sumOf { it.length() }

    return try {
        activity.contentResolver.openOutputStream(destUri)?.use { os ->
            ZipOutputStream(os).use { zos ->
                var written = 0L
                val buf = ByteArray(DEFAULT_BUFFER_SIZE)

                fun putFile(f: File, rel: String) {
                    val entryPath = "$tidUpper/$rel"
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

/** Helper name for CreateDocument: "<Name>_save_YYYY-MM-DD.zip" (derived from marker folder) */
fun buildSuggestedExportName(activity: Activity, titleId: String): String {
    val primary = pickSaveDirWithMarker(activity, titleId)
    val displayName = if (primary != null) {
        val txt = File(primary, "TITLEID.txt")
        runCatching {
            txt.takeIf { it.exists() }?.readLines(Charset.forName("UTF-8"))?.getOrNull(1)
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Save"
    } else {
        "Save"
    }
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    return sanitizeFileName("${displayName}_save_$date") + ".zip"
}

/* =========================== Import ============================== */

data class ImportProgress(val bytes: Long, val total: Long, val currentEntry: String)
data class ImportResult(val ok: Boolean, val message: String)

/* Helpers */

private fun isHexTitleId(s: String): Boolean =
    s.length == 16 && s.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }

private fun topLevelSegment(path: String): String {
    val p = path.replace('\\', '/').trim('/')
    val idx = p.indexOf('/')
    return if (idx >= 0) p.substring(0, idx) else p
}

private fun ensureInside(base: File, child: File): Boolean =
    try {
        val basePath = base.canonicalPath
        val childPath = child.canonicalPath
        childPath.startsWith(basePath + File.separator)
    } catch (_: Throwable) { false }

private fun clearDirectory(dir: File) {
    if (!dir.isDirectory) return
    dir.listFiles()?.forEach { f ->
        if (f.isDirectory) f.deleteRecursively() else runCatching { f.delete() }
    }
}

/**
 * Expects a ZIP with structure: TITLEID_UPPER/… — writes ONLY into the folder that has TITLEID.txt.
 */
fun importSaveFromZip(
    activity: Activity,
    zipUri: Uri,
    onProgress: (ImportProgress) -> Unit
): ImportResult {
    val cr: ContentResolver = activity.contentResolver

    // Find TitleID folder inside the ZIP
    var titleIdFromZip: String? = null
    var totalBytes = 0L

    // Pass 1: determine top-level TitleID and total size
    runCatching {
        cr.openInputStream(zipUri)?.use { ins ->
            ZipInputStream(BufferedInputStream(ins)).use { zis ->
                val tops = mutableSetOf<String>()
                var ze = zis.nextEntry
                while (ze != null) {
                    val name = ze.name.replace('\\', '/')
                    if (!ze.isDirectory) tops += topLevelSegment(name)
                    ze = zis.nextEntry
                }
                titleIdFromZip = tops.firstOrNull { isHexTitleId(it) }
            }
        }
    }.onFailure {
        return ImportResult(false, "error importing save. invalid zip")
    }

    if (titleIdFromZip == null) return ImportResult(false, "error importing save. missing TITLEID folder")
    val tidZip = titleIdFromZip!!.lowercase(Locale.ROOT)

    // Sum size only for paths under /<TITLEID>/…
    runCatching {
        cr.openInputStream(zipUri)?.use { ins ->
            ZipInputStream(BufferedInputStream(ins)).use { zis ->
                var ze = zis.nextEntry
                while (ze != null) {
                    val name = ze.name.replace('\\', '/')
                    if (!ze.isDirectory && topLevelSegment(name).equals(tidZip, ignoreCase = true)) {
                        if (ze.size >= 0) totalBytes += ze.size
                    }
                    ze = zis.nextEntry
                }
            }
        }
    }

    // Target: ONLY the marker folder
    val targetRoot = pickSaveDirWithMarker(activity, tidZip)
        ?: pickSaveDirWithMarker(activity, tidZip.uppercase(Locale.ROOT))
        ?: return ImportResult(false, "error importing save. start game once.")

    // Prepare 0/1 (clear)
    val zero = File(targetRoot, "0").apply { mkdirs() }
    val one  = File(targetRoot, "1").apply { mkdirs() }
    clearDirectory(zero)
    clearDirectory(one)

    // Pass 2: extract
    var written = 0L
    val buf = ByteArray(DEFAULT_BUFFER_SIZE)

    val ok = runCatching {
        cr.openInputStream(zipUri)?.use { ins ->
            ZipInputStream(BufferedInputStream(ins)).use { zis ->
                var ze = zis.nextEntry
                while (ze != null) {
                    val entryNameRaw = ze.name.replace('\\', '/').trimStart('/')
                    val top = topLevelSegment(entryNameRaw)

                    if (!ze.isDirectory && top.equals(tidZip, ignoreCase = true)) {
                        val rel = entryNameRaw.substring(top.length).trimStart('/')
                        if (rel.isNotEmpty()) {
                            val out0 = File(zero, rel)
                            val out1 = File(one, rel)
                            out0.parentFile?.mkdirs()
                            out1.parentFile?.mkdirs()

                            if (!ensureInside(zero, out0) || !ensureInside(one, out1)) {
                                // Zip-slip protection
                                zis.closeEntry()
                                ze = zis.nextEntry
                                continue
                            }

                            // Read once, write twice
                            val os0 = out0.outputStream()
                            val os1 = out1.outputStream()
                            try {
                                var n = zis.read(buf)
                                while (n > 0) {
                                    os0.write(buf, 0, n)
                                    os1.write(buf, 0, n)
                                    written += n
                                    onProgress(ImportProgress(written, totalBytes, rel))
                                    n = zis.read(buf)
                                }
                            } finally {
                                runCatching { os0.close() }
                                runCatching { os1.close() }
                            }
                        }
                    }

                    zis.closeEntry()
                    ze = zis.nextEntry
                }
            }
        }
        true
    }.getOrElse {
        Log.w("SaveFs", "importSaveFromZip failed: ${it.message}")
        false
    }

    return if (ok) ImportResult(true, "save imported")
    else ImportResult(false, "error importing save. start game once.")
}

/* ========================= UI Helpers ============================ */

fun suggestedCreateDocNameForExport(activity: Activity, titleId: String): String =
    buildSuggestedExportName(activity, titleId)
