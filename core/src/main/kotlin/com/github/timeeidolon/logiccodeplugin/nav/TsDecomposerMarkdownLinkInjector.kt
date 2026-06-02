package com.github.timeeidolon.logiccodeplugin.nav

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Injects `logiccode://ts/file?path=...` after backtick-wrapped source paths in TS decomposition / overview markdown
 * so [MarkdownPreviewPanel] can open files from the Logic Code tool window.
 *
 * Chains [TsRouteGuideMarkdownLinkInjector] first (route lines ending with `` — `path` ``).
 */
object TsDecomposerMarkdownLinkInjector {

    private val backtickedSourceFile = Regex("`([^`]+\\.(?:tsx?|jsx?|js))`")
    private val commonDirBullet = Regex("^-\\s+`(src/common(?:/[^`]+)?)`\\s*$")

    fun augment(markdown: String): String {
        var s = TsRouteGuideMarkdownLinkInjector.augment(markdown)
        s = s.lines().joinToString("\n") { augmentLine(it) }
        return s
    }

    private fun augmentLine(line: String): String {
        if (line.contains("](logiccode:")) return line
        var out = line
        val inserts = mutableListOf<Pair<Int, String>>()
        backtickedSourceFile.findAll(out).forEach { m ->
            val rel = m.groupValues[1].trim().replace('\\', '/')
            if (!rel.contains("/") && !rel.startsWith("src/")) return@forEach
            val enc = URLEncoder.encode(rel, StandardCharsets.UTF_8)
            inserts.add(m.range.last + 1 to " [`→`](logiccode://ts/file?path=$enc)")
        }
        if (inserts.isNotEmpty()) {
            for ((idx, fragment) in inserts.sortedByDescending { it.first }) {
                out = out.substring(0, idx) + fragment + out.substring(idx)
            }
            return out
        }
        commonDirBullet.matchEntire(out.trimEnd())?.let { m ->
            val rel = m.groupValues[1].trim().replace('\\', '/')
            val enc = URLEncoder.encode(rel, StandardCharsets.UTF_8)
            return out.trimEnd() + " [`→`](logiccode://ts/file?path=$enc)"
        }
        return out
    }
}
