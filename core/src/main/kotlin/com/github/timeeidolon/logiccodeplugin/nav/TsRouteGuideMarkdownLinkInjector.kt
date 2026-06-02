package com.github.timeeidolon.logiccodeplugin.nav

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Appends a small open-file link for lines produced by [com.github.timeeidolon.logiccodeplugin.actions.GenerateFrontendRouteGuideAction]
 * (pattern: trailing `` `relative/path.ext` ``).
 */
object TsRouteGuideMarkdownLinkInjector {

    private val fileTail = Regex("""—\s*`([^`]+\.(?:tsx?|jsx?|js))`\s*$""")

    fun augment(markdown: String): String =
        markdown.lines().joinToString("\n") { line ->
            if (line.contains("](logiccode:")) return@joinToString line
            val m = fileTail.find(line) ?: return@joinToString line
            val rel = m.groupValues[1].trim().replace('\\', '/')
            if (!rel.contains("/")) return@joinToString line
            val enc = URLEncoder.encode(rel, StandardCharsets.UTF_8)
            line.trimEnd() + " [`→`](logiccode://ts/file?path=$enc)"
        }
}
