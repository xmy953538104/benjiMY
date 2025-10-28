package org.kenjinx.android.cheats

import android.app.Activity
import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/* -------- Pfade -------- */

private fun modsRootExternal(activity: Activity): File {
    // /storage/emulated/0/Android/data/<pkg>/files/sdcard/atmosphere/contents
    return File(activity.getExternalFilesDir(null), "sdcard/atmosphere/contents")
}

private fun modsTitleDir(activity: Activity, titleIdUpper: String): File {
    // TITLEID muss groß geschrieben sein
    return File(modsRootExternal(activity), titleIdUpper)
}

private fun modDir(activity: Activity, titleIdUpper: String, modName: String): File {
    return File(modsTitleDir(activity, titleIdUpper), modName)
}

/* -------- Auflisten & Löschen -------- */

fun listMods(activity: Activity, titleId: String): List<String> {
    val titleIdUpper = titleId.trim().uppercase()
    val dir = modsTitleDir(activity, titleIdUpper)
    if (!dir.exists() || !dir.isDirectory) return emptyList()

    return dir.listFiles { f -> f.isDirectory } // NAME-Ordner
        ?.map { it.name }
        ?.sortedBy { it.lowercase() }
        ?: emptyList()
}

fun deleteMod(activity: Activity, titleId: String, modName: String): Boolean {
    val target = modDir(activity, titleId.trim().uppercase(), modName)
    return target.safeDeleteRecursively()
}

private fun File.safeDeleteRecursively(): Boolean {
    if (!exists()) return true
    return try {
        walkBottomUp().forEach {
            runCatching { if (it.isDirectory) it.delete() else it.delete() }
        }
        !exists()
    } catch (_: Throwable) {
        false
    }
}

/* -------- Import ZIP -------- */

data class ImportProgress(
    val bytesRead: Long,
    val totalBytes: Long,
    val currentEntry: String = ""
) {
    val fraction: Float
        get() = if (totalBytes <= 0) 0f else (bytesRead.coerceAtMost(totalBytes).toFloat() / totalBytes.toFloat())
}

// NEU: Multi-Import. Top-Level-Ordner in der ZIP sind die Mod-Namen.
data class ImportModsResult(
    val imported: List<String>,
    val ok: Boolean
)

fun importModsZip(
    activity: Activity,
    titleId: String,
    zipUri: Uri,
    onProgress: (ImportProgress) -> Unit
): ImportModsResult {
    val titleIdUpper = titleId.trim().uppercase()
    val baseDir = modsTitleDir(activity, titleIdUpper).apply { mkdirs() }

    val (_, totalBytes) = resolveDisplayNameAndSize(activity.contentResolver, zipUri)
    var bytes = 0L
    fun bump(read: Int, entryName: String = "") {
        if (read > 0) {
            bytes += read
            onProgress(ImportProgress(bytesRead = bytes, totalBytes = totalBytes, currentEntry = entryName))
        }
    }

    // Für jeden Top-Level-Ordner (Mod-Name) einmalig vorbereiten (ggf. alten Ordner löschen).
    val preparedMods = mutableSetOf<String>()
    val importedMods = linkedSetOf<String>() // Reihenfolge stabil

    return try {
        activity.contentResolver.openInputStream(zipUri).use { raw ->
            if (raw == null) return@use
            ZipInputStream(raw).use { zis ->
                var entry = zis.nextEntry
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)

                while (entry != null) {
                    val rawName = entry.name.replace('\\', '/') // normalisieren
                    // Sicherheitsfilter & leere Namen überspringen
                    if (rawName.isBlank() || rawName.startsWith("/") || rawName.contains("..")) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    // Top-Level: erster Segment vor dem ersten '/'
                    val slash = rawName.indexOf('/')
                    val topLevel = if (slash > 0) rawName.substring(0, slash) else rawName
                    if (topLevel.isBlank()) {
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    // restlicher Pfad innerhalb des Mod-Ordners
                    val relPath = if (slash >= 0 && slash + 1 < rawName.length) rawName.substring(slash + 1) else ""

                    // Nur Einträge verarbeiten, die innerhalb eines Modordners liegen (wir wollen NAME/... Strukturen)
                    if (relPath.isBlank() && entry.isDirectory.not()) {
                        // Datei direkt im Top-Level (z.B. NAME.txt) ignorieren
                        zis.closeEntry()
                        entry = zis.nextEntry
                        continue
                    }

                    // Mod-Ordner vorbereiten (einmalig: ggf. alten Ordner entfernen)
                    if (preparedMods.add(topLevel)) {
                        val modFolder = modDir(activity, titleIdUpper, topLevel)
                        if (modFolder.exists()) modFolder.safeDeleteRecursively()
                        modFolder.mkdirs()
                        importedMods += topLevel
                    }

                    // Zielpfad: .../TITLEID/<topLevel>/<relPath>
                    val dest = if (relPath.isBlank()) {
                        // nur ein Ordner-Eintrag (NAME/ oder NAME/exefs/)
                        File(modDir(activity, titleIdUpper, topLevel), "")
                    } else {
                        File(modDir(activity, titleIdUpper, topLevel), relPath)
                    }

                    if (entry.isDirectory) {
                        dest.mkdirs()
                    } else {
                        dest.parentFile?.mkdirs()
                        dest.outputStream().use { os ->
                            var n = zis.read(buffer)
                            while (n > 0) {
                                os.write(buffer, 0, n)
                                bump(n, rawName)
                                n = zis.read(buffer)
                            }
                        }
                    }

                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        ImportModsResult(imported = importedMods.toList(), ok = importedMods.isNotEmpty())
    } catch (t: Throwable) {
        Log.w("ModFs", "importModsZip failed: ${t.message}")
        // Best effort: schon angelegte Mods sauber entfernen
        importedMods.forEach { name ->
            runCatching { modDir(activity, titleIdUpper, name).safeDeleteRecursively() }
        }
        ImportModsResult(imported = emptyList(), ok = false)
    }
}

private fun resolveDisplayNameAndSize(cr: ContentResolver, uri: Uri): Pair<String?, Long> {
    var name: String? = null
    var size: Long = -1
    try {
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (nameIdx >= 0) name = c.getString(nameIdx)
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
    } catch (_: Throwable) {}
    return name to size
}
