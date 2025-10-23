package org.kenjinx.android.cheats

import android.app.Activity
import android.util.Log
import java.io.File
import java.nio.charset.Charset
import android.net.Uri
import android.provider.OpenableColumns
import android.content.Intent

data class CheatItem(val buildId: String, val name: String) {
    val key get() = "$buildId-$name"
}

/* -------- Pfade -------- */

private fun cheatsDirExternal(activity: Activity, titleId: String): File {
    val base = activity.getExternalFilesDir(null) // /storage/emulated/0/Android/data/<pkg>/files
    return File(base, "mods/contents/$titleId/cheats")
}

private fun allCheatDirs(activity: Activity, titleId: String): List<File> {
    return listOf(cheatsDirExternal(activity, titleId))
        .distinct()
        .filter { it.exists() && it.isDirectory }
}

/* -------- Parser -------- */

private fun parseCheatNames(text: String): List<String> {
    // Trim BOM, CRLF tolerant
    val clean = text.replace("\uFEFF", "")
    val rx = Regex("""(?m)^\s*\[(.+?)\]\s*$""")
    return rx.findAll(clean)
        .map { it.groupValues[1].trim() }
        .filter { it.isNotEmpty() }
        .toList()
}

/* -------- Public: Cheats laden -------- */

fun loadCheatsFromDisk(activity: Activity, titleId: String): List<CheatItem> {
    val dirs = allCheatDirs(activity, titleId)
    if (dirs.isEmpty()) {
        Log.d("CheatFs", "No cheat dirs for $titleId (checked internal+external).")
        return emptyList()
    }

    val out = mutableListOf<CheatItem>()
    for (dir in dirs) {
        dir.listFiles { f -> f.isFile && f.name.endsWith(".txt", ignoreCase = true) }?.forEach { file ->
            val buildId = file.nameWithoutExtension
            val text = runCatching { file.readText(Charset.forName("UTF-8")) }.getOrElse { "" }
            parseCheatNames(text).forEach { nm ->
                out += CheatItem(buildId, nm)
            }
        }
    }

    return out
        .distinctBy { it.key.lowercase() }
        .sortedWith(compareBy({ it.buildId.lowercase() }, { it.name.lowercase() }))
}

/* -------- Public: Auswahl SOFORT auf Disk anwenden -------- */

fun applyCheatSelectionOnDisk(activity: Activity, titleId: String, enabledKeys: Set<String>) {
    // Wir wählen genau EINE BUILDID-Datei (die „beste“), und schalten darin Sections.
    val dirs = allCheatDirs(activity, titleId)
    val allTxt = dirs.flatMap { d ->
        d.listFiles { f -> f.isFile && f.name.endsWith(".txt", ignoreCase = true) }?.toList() ?: emptyList()
    }
    if (allTxt.isEmpty()) {
        Log.d("CheatFs", "applyCheatSelectionOnDisk: no *.txt found for $titleId")
        return
    }

    val buildFile = pickBestBuildFile(allTxt)
    val text = runCatching { buildFile.readText(Charset.forName("UTF-8")) }.getOrElse { "" }
    if (text.isEmpty()) return

    // Enabled-Set normalisieren: Keys sind "<BUILDID>-<SectionName>"
    val enabledSections = enabledKeys.asSequence()
        .mapNotNull { key ->
            val dash = key.indexOf('-')
            if (dash <= 0) null else key.substring(dash + 1).trim()
        }
        .map { it.lowercase() }
        .toSet()

    val rewritten = rewriteCheatFile(text, enabledSections)

    runCatching {
        buildFile.writeText(rewritten, Charset.forName("UTF-8"))
    }.onFailure {
        Log.w("CheatFs", "Failed to write ${buildFile.absolutePath}: ${it.message}")
    }
}

/* -------- Implementierung: Auswahl anwenden (nur ';' als Kommentar) -------- */

private fun pickBestBuildFile(files: List<File>): File {
    fun looksHexName(p: File): Boolean {
        val n = p.nameWithoutExtension
        return n.length >= 16 && n.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
    }
    return files.firstOrNull(::looksHexName)
        ?: files.maxByOrNull { runCatching { it.lastModified() }.getOrDefault(0L) }
        ?: files.first()
}

private fun isSectionHeader(line: String): Boolean {
    val t = line.trim()
    return t.length > 2 && t.first() == '[' && t.contains(']')
}

private fun sectionNameFromHeader(line: String): String {
    val t = line.trim()
    val close = t.indexOf(']')
    return if (t.startsWith("[") && close > 1) t.substring(1, close).trim() else ""
}

/**
 * Entfernt EIN führendes Kommentarzeichen (';') + optionales Leerzeichen.
 * Nur am absoluten Zeilenanfang (keine führenden Spaces erlaubt).
 */
private fun uncommentOnce(raw: String): String {
    if (raw.isEmpty()) return raw
    return if (raw.startsWith(";")) {
        raw.drop(1).let { if (it.startsWith(" ")) it.drop(1) else it }
    } else raw
}

/**
 * Kommentiert die Zeile aus, wenn sie nicht bereits mit ';' beginnt.
 * Atmosphère nutzt ';' – das verwenden wir ausschließlich.
 */
private fun commentOut(raw: String): String {
    val t = raw.trimStart()
    if (t.isEmpty()) return raw
    if (t.startsWith(";")) return raw
    return "; $raw"
}

/**
 * Schreibt die Datei neu:
 *  - Keine Marker einfügen
 *  - Pro Section den Body gemäß enabled/disabled (enabledSections) kommentieren/entkommentieren
 *  - Reine Kommentar-/Leerzeilen (nur ';') bleiben erhalten
 */
// Hilfsfunktionen: trailing Blankzeilen trimmen / Header normalisieren
private fun trimTrailingBlankLines(lines: MutableList<String>) {
    while (lines.isNotEmpty() && lines.last().trim().isEmpty()) {
        lines.removeAt(lines.lastIndex)
    }
}

private fun joinHeaderBufferOnce(header: List<String>): String {
    // Header-Zeilen unverändert, aber trailing Blanks entfernen und genau 1 Leerzeile danach
    val buf = header.toMutableList()
    trimTrailingBlankLines(buf)
    return if (buf.isEmpty()) "" else buf.joinToString("\n") + "\n\n"
}

/**
 * Schreibt die Datei neu:
 *  - Keine Marker einfügen
 *  - Pro Section den Body gemäß enabled/disabled (enabledSections) kommentieren/entkommentieren
 *  - Reine Kommentar-/Leerzeilen bleiben erhalten
 *  - Zwischen Sections genau EINE Leerzeile, am Ende genau EIN Newline.
 */
private fun rewriteCheatFile(original: String, enabledSections: Set<String>): String {
    val lines = original.replace("\uFEFF", "").lines()

    val out = StringBuilder(original.length + 1024)

    var currentSection: String? = null
    val currentBlock = ArrayList<String>()
    val headerBuffer = ArrayList<String>()
    var sawAnySection = false
    var wroteAnySection = false

    fun flushCurrent() {
        val sec = currentSection ?: return

        // trailing Blankzeilen im Block entfernen, damit keine doppelten Abstände wachsen
        trimTrailingBlankLines(currentBlock)

        val enabled = enabledSections.contains(sec.lowercase())

        // Zwischen Sections genau eine Leerzeile einfügen (aber nicht vor der ersten)
        if (wroteAnySection) out.append('\n')

        out.append('[').append(sec).append(']').append('\n')

        if (enabled) {
            // Entkommentieren (nur ein führendes ';' an Spalte 0)
            for (l in currentBlock) {
                val trimmed = l.trim()
                if (trimmed.isEmpty() || (trimmed.startsWith(";") && trimmed.length <= 1)) {
                    out.append(l).append('\n')
                } else {
                    if (l.startsWith(";")) {
                        out.append(
                            l.drop(1).let { if (it.startsWith(" ")) it.drop(1) else it }
                        ).append('\n')
                    } else {
                        out.append(l).append('\n')
                    }
                }
            }
        } else {
            // Disablen: alles, was nicht schon mit ';' beginnt und nicht leer ist, auskommentieren
            for (l in currentBlock) {
                val t = l.trim()
                if (t.isEmpty() || t.startsWith(";")) {
                    out.append(l).append('\n')
                } else {
                    out.append("; ").append(l).append('\n')
                }
            }
        }

        wroteAnySection = true
        currentSection = null
        currentBlock.clear()
    }

    for (raw in lines) {
        if (isSectionHeader(raw)) {
            flushCurrent()
            currentSection = sectionNameFromHeader(raw)
            sawAnySection = true
            continue
        }

        if (!sawAnySection) {
            headerBuffer.add(raw)
        } else {
            currentBlock.add(raw)
        }
    }
    flushCurrent()

    // Header vorn einsetzen (mit genau einer Leerzeile danach, falls vorhanden)
    val headerText = joinHeaderBufferOnce(headerBuffer)
    if (headerText.isNotEmpty()) {
        out.insert(0, headerText)
    }

    // Globale Normalisierung: 3+ Newlines -> 2, und am Ende genau EIN '\n'
    var result = out.toString()
        .replace(Regex("\n{3,}"), "\n\n") // nie mehr als 1 Leerzeile zwischen Abschnitten
        .trimEnd() + "\n"                 // genau ein Newline am Ende

    return result
}
private fun cheatsDirPreferredForWrite(activity: Activity, titleId: String): File {
    val dir = cheatsDirExternal(activity, titleId)
    if (!dir.exists()) dir.mkdirs()
    return dir
}

private fun getDisplayName(activity: Activity, uri: Uri): String? {
    return runCatching {
        val cr = activity.contentResolver
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}

private fun uniqueFile(targetDir: File, baseName: String): File {
    var name = baseName
    if (!name.lowercase().endsWith(".txt")) name += ".txt"
    var out = File(targetDir, name)
    var idx = 1
    val stem = name.substringBeforeLast(".")
    val ext = ".txt"
    while (out.exists()) {
        out = File(targetDir, "$stem ($idx)$ext")
        idx++
    }
    return out
}

/**
 * Importiert eine .txt aus einem SAF-Uri in den Cheats-Ordner des Titels.
 * Gibt das Zieldatei-Objekt zurück, wenn erfolgreich.
 */
fun importCheatTxt(activity: Activity, titleId: String, source: Uri): Result<File> {
    return runCatching {
        // Lese-Rechte ggf. dauerhaft sichern
        try {
            activity.contentResolver.takePersistableUriPermission(
                source,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Throwable) {}

        val targetDir = cheatsDirPreferredForWrite(activity, titleId)

        val display = getDisplayName(activity, source) ?: "cheats.txt"
        val target = uniqueFile(targetDir, display)

        activity.contentResolver.openInputStream(source).use { ins ->
            requireNotNull(ins) { "InputStream null" }
            target.outputStream().use { outs ->
                ins.copyTo(outs)
            }
        }

        // nach Import: optional sofort neu einlesen/normalisieren wäre möglich,
        // aber wir belassen die Datei so wie geliefert.
        target
    }
}
