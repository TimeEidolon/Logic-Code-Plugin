package com.github.timeeidolon.logiccodeplugin.ui

/**
 * Builds a standalone HTML document: markdown segments via [SimpleMarkdownHtml.renderBodyInner]
 * and ```mermaid``` fences rendered by Mermaid in JCEF.
 */
object MarkdownMermaidPage {

    private val mermaidFence =
        Regex("(?m)^```(?i:mermaid)\\s*\r?\n([\\s\\S]*?)(?m)^```\\s*\r?$", RegexOption.MULTILINE)

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /**
     * Full HTML document, or **null** if Mermaid JS is unavailable or no valid ```mermaid``` block.
     */
    fun build(markdown: String): String? {
        val mermaidJs = MermaidWebJar.mermaidMinJs
        if (mermaidJs.isBlank()) return null
        val matches = mermaidFence.findAll(markdown).toList()
        if (matches.isEmpty()) return null

        val htmlBuffer = StringBuilder()
        var cursor = 0
        for (m in matches) {
            val before = markdown.substring(cursor, m.range.first)
            appendMarkdownSegment(htmlBuffer, before)
            val graph = m.groupValues[1].trimEnd('\n', '\r')
            htmlBuffer.append("<pre class=\"mermaid\">")
            htmlBuffer.append(escapeHtml(graph))
            htmlBuffer.append("</pre>\n")
            cursor = m.range.last + 1
        }
        appendMarkdownSegment(htmlBuffer, markdown.substring(cursor))

        val merged = htmlBuffer.toString()

        val initJs = mermaidInitScript()

        return buildString {
            appendLine("<!DOCTYPE html>")
            appendLine("<html lang=\"zh-CN\">")
            appendLine("  <head>")
            appendLine("    <meta charset=\"utf-8\" />")
            appendLine("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\" />")
            appendLine("    <style>")
            appendLine("      html, body { margin: 0; padding: 0; background: $COLORS_BG; color: $COLORS_FG;")
            appendLine("        font-family: sans-serif; font-size: 13px; line-height: 1.5; }")
            appendLine("      .md { max-width: 920px; margin: 0 auto; padding: 10px 14px 14px; }")
            appendLine("      .md + .md { border-top: 1px solid #3d444d; }")
            appendLine(
                "      pre.mermaid { margin: 10px auto; padding: 8px 4px 16px; text-align: center; " +
                    "background: $COLORS_BG; }"
            )
            appendLine("      pre.mermaid:not(:last-child) { border-bottom: 1px solid #3d444d; }")
            appendLine("      svg { display: block; margin: 0 auto; }")
            appendLine("    </style>")
            appendLine("  </head>")
            appendLine("  <body>")
            append(merged)
            appendLine("    <script>$mermaidJs</script>")
            appendLine("    <script>")
            appendLine(initJs)
            appendLine("    </script>")
            appendLine("  </body>")
            appendLine("</html>")
        }
    }

    private fun appendMarkdownSegment(sb: StringBuilder, raw: String) {
        val t = raw.trim('\n')
        if (t.isBlank()) return
        sb.append("<div class=\"md\">")
        sb.append(SimpleMarkdownHtml.renderBodyInner(raw))
        sb.append("</div>\n")
    }

    private fun mermaidInitScript(): String =
        """
        try {
          mermaid.initialize({
            startOnLoad: false,
            theme: 'dark',
            themeVariables: {
              darkMode: true,
              background: '#1a1d21',
              mainBkg: '#243838',
              secondaryColor: '#2a3038',
              tertiaryColor: '#1e2428',
              primaryColor: '#2d4a4a',
              primaryTextColor: '#d8eef0',
              primaryBorderColor: '#3dd6c3',
              lineColor: '#5b8fd4',
              secondaryTextColor: '#d8dee9',
              tertiaryTextColor: '#8b949e',
              noteBkgColor: '#2a3038',
              noteTextColor: '#d8dee9',
              noteBorderColor: '#5b8fd4',
              titleColor: '#c8f7ef'
            }
          });
          mermaid.run({ querySelector: '.mermaid' });
        } catch (e) {
          document.body.innerHTML = '<pre style="padding:12px;color:#ccc">Mermaid failed: ' + e + '</pre>';
        }
        """.trimIndent()

    private const val COLORS_BG = "#1a1e1e"
    private const val COLORS_FG = "#c8cdd5"
}
