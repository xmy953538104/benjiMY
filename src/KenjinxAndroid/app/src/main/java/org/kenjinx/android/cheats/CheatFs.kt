package org.kenjinx.android.cheats

import android.app.Activity
import android.util.Log
import java.io.File
import java.nio.charset.Charset

data class CheatItem(val buildId: String, val name: String) {
    val key get() = "$buildId-$name"
}

/* -------- Paths -------- */

private fun cheatsDirExternal(activity: Activity, titleId: String): File {
    val base = activity.getExternalFilesDir(null) // /storage/emulated/0/Android/data/<pkg>/files
    return File(base, "mods/contents/$titleId/cheats")
}

private fun cheatsDirInternal(activity: Activity, titleId: String): File {
    val base = activity.filesDir                 // /data/user/0/<pkg>/files
    return File(base, "mods/contents/$titleId/cheats")
}

private fun allCheatDirs(activity: Activity, titleId: String): List<File> {
    // Order: internal first (LibKenjinx usually writes here), then external
    return listOf(cheatsDirInternal(activity, titleId), cheatsDirExternal(activity, titleId))
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

/* -------- Public: Load cheats -------- */

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

/* -------- Public: Apply selection to disk immediately -------- */

fun applyCheatSelectionOnDisk(activity: Activity, titleId: String, enabledKeys: Set<String>) {
    // We pick exactly ONE BUILDID file (the "best") and toggle sections inside it.
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

    // Normalize enabled set: keys are "<BUILDID>-<SectionName>"
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

/* -------- Implementation: apply selection (using only ';' as comment) -------- */

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
 * Removes ONE leading comment marker (';') + optional space.
 * Only at absolute column 0 (no leading spaces allowed).
 */
private fun uncommentOnce(raw: String): String {
    if (raw.isEmpty()) return raw
    return if (raw.startsWith(";")) {
        raw.drop(1).let { if (it.startsWith(" ")) it.drop(1) else it }
    } else raw
}

/**
 * Comments out the line if it does not already start with ';'.
 * Atmosphère uses ';' — we use that exclusively.
 */
private fun commentOut(raw: String): String {
    val t = raw.trimStart()
    if (t.isEmpty()) return raw
    if (t.startsWith(";")) return raw
    return "; $raw"
}

/**
 * Rewrites the file:
 *  - Do not insert markers
 *  - For each section, comment/uncomment the body according to enabled/disabled (enabledSections)
 *  - Keep pure comment/empty lines (only ';') intact
 */
// Helpers: trim trailing blank lines / normalize header
private fun trimTrailingBlankLines(lines: MutableList<String>) {
    while (lines.isNotEmpty() && lines.last().trim().isEmpty()) {
        lines.removeAt(lines.lastIndex)
    }
}

private fun joinHeaderBufferOnce(header: List<String>): String {
    // Keep header lines unchanged, but remove trailing blanks and add exactly one blank line after
    val buf = header.toMutableList()
    trimTrailingBlankLines(buf)
    return if (buf.isEmpty()) "" else buf.joinToString("\n") + "\n\n"
}

/**
 * Rewrites the file:
 *  - Do not insert markers
 *  - For each section, comment/uncomment the body according to enabled/disabled (enabledSections)
 *  - Keep pure comment/empty lines intact
 *  - Exactly ONE blank line between sections, exactly ONE newline at the end.
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

        // Remove trailing blank lines from the block so spacing doesn't grow
        trimTrailingBlankLines(currentBlock)

        val enabled = enabledSections.contains(sec.lowercase())

        // Insert exactly one blank line between sections (but not before the first)
        if (wroteAnySection) out.append('\n')

        out.append('[').append(sec).append(']').append('\n')

        if (enabled) {
            // Uncomment (only a single leading ';' at column 0)
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
            // Disable: comment out anything that doesn't already start with ';' and isn't empty
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

    // Prepend header (with exactly one blank line after it, if present)
    val headerText = joinHeaderBufferOnce(headerBuffer)
    if (headerText.isNotEmpty()) {
        out.insert(0, headerText)
    }

    // Global normalization: 3+ newlines -> 2, and exactly ONE '\n' at the end
    val result = out.toString()
        .replace(Regex("\n{3,}"), "\n\n") // never more than 1 blank line between sections
        .trimEnd() + "\n"                 // exactly one newline at the end

    return result
}
