package com.github.timeeidolon.logiccodeplugin.nav

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Injects `logiccode://java/method?label=...` markdown links for method headings under `## Details`
 * so the ToolWindow preview can navigate to PSI.
 */
object EntryGuideMarkdownLinkInjector {

    private val detailsStart = Regex("""^##\s+Details\b.*$""", RegexOption.IGNORE_CASE)
    private val methodHeading = Regex("""^(#{3,4})\s+([\w$.]+)\.(\w+)\s*$""")

    fun augment(markdown: String): String {
        val lines = markdown.lines().toMutableList()
        var inDetails = false
        for (i in lines.indices) {
            val t = lines[i].trim()
            if (detailsStart.matches(t)) {
                inDetails = true
                continue
            }
            if (!inDetails) continue

            if (t.startsWith("## ") && !detailsStart.matches(t)) {
                inDetails = false
                continue
            }

            if (lines[i].contains("](logiccode:")) continue

            val m = methodHeading.matchEntire(t) ?: continue
            val hashes = m.groupValues[1]
            val cls = m.groupValues[2]
            val meth = m.groupValues[3]
            val label = "$cls.$meth"
            val enc = URLEncoder.encode(label, StandardCharsets.UTF_8)
            lines[i] = "$hashes [`$label`](logiccode://java/method?label=$enc)"
        }
        return lines.joinToString("\n")
    }
}
