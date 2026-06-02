package com.github.timeeidolon.logiccodeplugin.graph

import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

class EntrypointGuideGraphExtractor(private val project: Project) {

    /** Resolves path of `PROJECT_ENTRYPOINT_GUIDE.md` given current settings/output layout. */
    fun guideMarkdownPath(): Path? = resolveEntrypointGuidePath()

    /**
     * 0-based line index (Unix newlines assumed for counting) pointing at the Markdown section anchor
     * for `[classSimpleName].[methodName]`, or nearest `###` heading before first hit line.
     */
    fun guideSectionStartLine(classSimpleName: String, methodName: String): Int? {
        val file = resolveEntrypointGuidePath() ?: return null
        if (!file.exists()) return null
        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        return sectionStartLine(classSimpleName, methodName, text)
    }

    data class MermaidBlock(
        val fullText: String,   // includes only mermaid body, not fences
        val matchedNodeId: String?
    )

    fun findMermaidBlockFor(className: String, methodName: String): MermaidBlock? {
        val file = resolveEntrypointGuidePath() ?: return null
        if (!file.exists()) return null

        val text = try {
            file.readText(Charsets.UTF_8)
        } catch (_: Exception) {
            return null
        }

        val target = "$className.$methodName"
        val blocks = extractMermaidBlocks(text)
        if (blocks.isEmpty()) return null

        // Prefer blocks that mention Class.method anywhere
        val candidate = blocks.firstOrNull { it.contains(target) }
            ?: blocks.firstOrNull { it.contains(methodName) }
            ?: return null

        val graph = MermaidGraphParser.parse(candidate)
        val matchedNodeId = graph.nodeDefs.entries.firstOrNull { (_, def) -> def.contains(target) }?.key

        return MermaidBlock(fullText = candidate.trim(), matchedNodeId = matchedNodeId)
    }

    /**
     * Extract a human-readable section for an entrypoint method from PROJECT_ENTRYPOINT_GUIDE.md.
     * Best-effort: finds the first occurrence of `Class.method` and returns the surrounding section
     * until the next "###" heading (or a reasonable max window).
     */
    fun findEntrypointSectionFor(className: String, methodName: String): String? {
        val file = resolveEntrypointGuidePath() ?: return null
        if (!file.exists()) return null

        val text = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: return null
        val lines = text.lines()
        val hitIdx = findBestHitLine(className, methodName, lines) ?: return null

        val start = walkSectionStart(lines, hitIdx)
        val endExclusive = sectionEndExclusive(lines, hitIdx, start)

        val section = lines.subList(start, endExclusive.coerceAtMost(lines.size)).joinToString("\n").trim()
        return section.takeIf { it.isNotBlank() }
    }

    private fun sectionStartLine(className: String, methodName: String, text: String): Int? {
        val lines = text.lines()
        val hitIdx = findHitLine(className, methodName, lines) ?: return null
        return walkSectionStart(lines, hitIdx).coerceAtLeast(0)
    }

    private fun findHitLine(className: String, methodName: String, lines: List<String>): Int? {
        val target = "$className.$methodName"
        return lines.indexOfFirst { it.contains(target) }
            .takeIf { it >= 0 }
            ?: lines.indexOfFirst { it.contains(methodName) }.takeIf { it >= 0 }
    }

    /**
     * Prefer the real entrypoint detail section header like `### Class.method`.
     * This avoids matching the index / summary area which may list *all* entrypoints.
     */
    private fun findBestHitLine(className: String, methodName: String, lines: List<String>): Int? {
        val target = "$className.$methodName"

        val detailsStart = lines.indexOfFirst { it.trimStart().startsWith("## Details") }
            .takeIf { it >= 0 }
            ?: 0

        // 1) Prefer exact method-level headings in Details region: `#### Class.method` / `### Class.method`
        run {
            for (i in detailsStart until lines.size) {
                val t = lines[i].trimStart()
                if ((t.startsWith("#### ") || t.startsWith("### ")) && t.contains(target)) return i
            }
        }

        // 2) Next best: methodName in a method-level heading (`####`) in Details region
        run {
            for (i in detailsStart until lines.size) {
                val t = lines[i].trimStart()
                if (t.startsWith("#### ") && t.contains(methodName)) return i
            }
        }

        // 3) Next: any `###` header containing methodName in Details region
        run {
            for (i in detailsStart until lines.size) {
                val t = lines[i].trimStart()
                if (t.startsWith("### ") && t.contains(methodName)) return i
            }
        }

        // 4) Fallback to old behavior (may hit index area)
        return findHitLine(className, methodName, lines)
    }

    private fun walkSectionStart(lines: List<String>, hitIdx: Int): Int {
        // If the hit line itself is already a heading, start there.
        val hitTrim = lines[hitIdx].trimStart()
        if (hitTrim.startsWith("#### ") || hitTrim.startsWith("### ")) return hitIdx

        // Prefer nearest method-level heading (`####`) above, else fall back to `###`.
        var i = hitIdx
        while (i > 0) {
            val t = lines[i].trimStart()
            if (t.startsWith("#### ")) return i
            i--
        }
        i = hitIdx
        while (i > 0) {
            val t = lines[i].trimStart()
            if (t.startsWith("### ")) return i
            i--
        }
        return 0
    }

    private fun sectionEndExclusive(lines: List<String>, hitIdx: Int, sectionStartLine: Int): Int {
        val startLine = lines[sectionStartLine].trimStart()
        val startIsH4 = startLine.startsWith("#### ")
        val startIsH3 = startLine.startsWith("### ")

        var end = (hitIdx + 1).coerceAtLeast(sectionStartLine + 1)
        while (end < lines.size) {
            val t = lines[end].trimStart()
            if (end <= sectionStartLine) {
                end++
                continue
            }

            // Stop at next heading of same-or-higher level.
            if (startIsH4) {
                if (t.startsWith("#### ") || t.startsWith("### ") || t.startsWith("## ")) break
            } else if (startIsH3) {
                if (t.startsWith("### ") || t.startsWith("## ")) break
            } else {
                if (t.startsWith("## ")) break
            }
            end++
        }

        val maxLines = 220
        if (end - sectionStartLine > maxLines) end = sectionStartLine + maxLines
        return end.coerceAtMost(lines.size)
    }

    private fun resolveEntrypointGuidePath(): Path? {
        val settings = ReportPluginSettings.getInstance().state
        val projectRoot = project.basePath?.let { Path(it) } ?: return null
        val outputDir = if (!settings.outputDir.isNullOrBlank()) projectRoot.resolve(settings.outputDir) else projectRoot.resolve("docs")
        // New layout: final reports are under /report
        return outputDir.resolve("report").resolve("PROJECT_ENTRYPOINT_GUIDE.md")
    }

    private fun extractMermaidBlocks(markdown: String): List<String> {
        // Extract ```mermaid ... ``` blocks
        val lines = markdown.lines()
        val blocks = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line == "```mermaid") {
                val sb = StringBuilder()
                i++
                while (i < lines.size && lines[i].trim() != "```") {
                    sb.appendLine(lines[i])
                    i++
                }
                val body = sb.toString().trimEnd()
                if (body.isNotBlank()) blocks.add(body)
            }
            i++
        }
        return blocks
    }
}

