package com.github.timeeidolon.logiccodeplugin.ui

/**
 * Markdown → HTML for [com.intellij.ui.components.JBHtmlPane].
 * Uses **inline styles** on elements: Swing's HTML renderer ignores most modern CSS (e.g. `:root`, `var()`, `::marker`).
 */
object SimpleMarkdownHtml {

    private object S {
        const val TEXT = "#d8dee9"
        const val MUTED = "#8b949e"
        const val ACCENT = "#3dd6c3"
        const val BLUE = "#5b8fd4"
        const val BG = "#1a1d21"
        const val SURFACE = "#22262c"
        const val BORDER = "#3d444d"
        const val CODE_BG = "#15181c"

        val wrap =
            "margin:0;padding:12px 15px 20px;background:$BG;color:$TEXT;font-family:sans-serif;font-size:13px;line-height:1.5;"
        val box =
            "border:1px solid $BORDER;border-radius:9px;padding:14px 16px;background:$SURFACE;"
        val h1 =
            "font-size:18px;font-weight:600;margin:0 0 12px;padding-bottom:8px;border-bottom:2px solid $ACCENT;color:#eef4ff;letter-spacing:-0.02em;line-height:1.35;"
        val h2 =
            "font-size:15px;font-weight:600;margin:17px 0 8px;padding:7px 11px 7px 13px;background:rgba(61,214,195,0.14);border-left:4px solid $ACCENT;border-radius:0 8px 8px 0;color:#c8f7ef;line-height:1.35;"
        val h3 =
            "font-size:14px;font-weight:600;margin:13px 0 6px;padding:3px 0 3px 9px;border-left:3px solid $BLUE;color:#b8d4ff;line-height:1.35;"
        val p = "margin:6px 0;color:$TEXT;line-height:1.5;"
        val pMuted = "margin:4px 0;color:$MUTED;font-size:12px;line-height:1.35;"
        val ul =
            "margin:6px 0;padding-left:20px;color:$TEXT;list-style-type:disc;line-height:1.5;"
        val ol =
            "margin:6px 0;padding-left:22px;color:$TEXT;list-style-type:decimal;line-height:1.5;"
        val li = "margin:4px 0;padding-left:2px;line-height:1.5;"
        val hr =
            "border:0;height:1px;margin:14px 0;background:#3d444d;"
        val bq =
            "margin:9px 0;padding:9px 13px 9px 15px;border-left:4px solid $BLUE;background:#283038;border-radius:0 8px 8px 0;color:#c5d0dd;line-height:1.45;"
        val pre =
            "white-space:pre-wrap;word-break:break-word;margin:10px 0;padding:11px 13px;background:$CODE_BG;border:1px solid $BORDER;border-radius:7px;font-family:Consolas,Menlo,monospace;font-size:12px;line-height:1.42;color:#b8c4d0;"
        val inlineCode =
            "font-family:Consolas,Menlo,monospace;font-size:12px;padding:1px 6px;border-radius:4px;background:#2a3038;color:#9ef0e1;border:1px solid $BORDER;"
        val strong = "color:#e8ecff;font-weight:600;"
        val tableWrap =
            "border-collapse:collapse;margin:11px 0;width:100%;border:1px solid $BORDER;table-layout:auto;line-height:1.35;"
        val thCell =
            "padding:6px 9px;text-align:left;color:#c8f7ef;background:#243838;border:1px solid $BORDER;font-size:12px;vertical-align:top;line-height:1.35;"
        val tdCell =
            "padding:6px 9px;color:$TEXT;border:1px solid $BORDER;font-size:12px;background:#1e2328;vertical-align:top;line-height:1.38;"
    }

    /** Full JBHtml pane document with outer padding card. */
    fun render(text: String): String = buildString {
        appendLine("<div style='${S.wrap}max-width:920px;margin:0 auto;'>")
        appendLine("<div style='${S.box}'>")
        appendBodyMarkdown(text, this)
        appendLine("</div></div>")
    }

    /** Body HTML only — embed into JCEF pages next to `<pre class="mermaid">`. */
    fun renderBodyInner(text: String): String =
        buildString { appendBodyMarkdown(text, this) }

    private fun appendBodyMarkdown(text: String, sink: StringBuilder) {
        val lines = text.replace("\r\n", "\n").replace("\r", "\n").lines()

        var inUl = false
        var inOl = false
        var inFence = false
        val fenceSb = StringBuilder()

        fun closeLists() {
            if (inUl) { sink.appendLine("</ul>"); inUl = false }
            if (inOl) { sink.appendLine("</ol>"); inOl = false }
        }

        fun openUl() {
            if (inOl) { sink.appendLine("</ol>"); inOl = false }
            if (!inUl) {
                sink.appendLine("<ul style='${S.ul}'>")
                inUl = true
            }
        }

        fun openOl() {
            if (inUl) { sink.appendLine("</ul>"); inUl = false }
            if (!inOl) {
                sink.appendLine("<ol style='${S.ol}'>")
                inOl = true
            }
        }

        fun flushFence() {
            if (fenceSb.isEmpty()) return
            val body = escapeHtml(fenceSb.toString()).trimEnd('\n')
            fenceSb.clear()
            sink.appendLine("<pre style='${S.pre}'>$body</pre>")
        }

        fun headingHtml(tag: String, style: String, titleRaw: String): String {
            val icon = iconForHeading(titleRaw)
            val inner = if (icon.isEmpty()) {
                inlineMarkdown(titleRaw.trim())
            } else {
                "<span style='margin-right:8px;'>$icon</span>${inlineMarkdown(titleRaw.trim())}"
            }
            return "<$tag style='$style'>$inner</$tag>"
        }

        fun isBulletLine(t: String): Boolean =
            t.startsWith("- ") || t.startsWith("* ") ||
                t.startsWith("• ") || t.startsWith("· ") || t.startsWith("▪ ") ||
                t.startsWith("‣ ")

        fun stripBullet(t: String): String = when {
            t.startsWith("- ") -> t.removePrefix("- ").trim()
            t.startsWith("* ") -> t.removePrefix("* ").trim()
            t.startsWith("• ") -> t.removePrefix("• ").trim()
            t.startsWith("· ") -> t.removePrefix("· ").trim()
            t.startsWith("▪ ") -> t.removePrefix("▪ ").trim()
            t.startsWith("‣ ") -> t.removePrefix("‣ ").trim()
            else -> t.trim()
        }

        // `1. ` / `1、` / `1．` + rest
        val orderedLine = Regex("""^(\d+)[.．、]\s*(.+)$""")

        var i = 0
        while (i < lines.size) {
            val raw = lines[i]
            val line = raw.trimEnd()

            if (line.trim().startsWith("```")) {
                if (inFence) {
                    inFence = false
                    flushFence()
                } else {
                    closeLists()
                    inFence = true
                    fenceSb.clear()
                }
                i++
                continue
            }
            if (inFence) {
                fenceSb.appendLine(line)
                i++
                continue
            }

            if (line.isBlank()) {
                closeLists()
                sink.appendLine("<p style='${S.pMuted}'>&nbsp;</p>")
                i++
                continue
            }

            val t0 = line.trim()
            if (t0 == "---" || t0 == "***" || t0 == "___") {
                closeLists()
                sink.appendLine("<div style='${S.hr}'></div>")
                i++
                continue
            }

            val tableParse = tryParseMarkdownTable(lines, i)
            if (tableParse != null) {
                closeLists()
                sink.appendLine(tableParse.html)
                i = tableParse.endIndexExclusive
                continue
            }

            if (line.trimStart().startsWith(">")) {
                closeLists()
                val q = line.trimStart().removePrefix(">").trimStart()
                sink.appendLine("<blockquote style='${S.bq}'><p style='margin:3px 0;color:#c5d0dd;line-height:1.45;'>${inlineMarkdown(q)}</p></blockquote>")
                i++
                continue
            }

            val trimmed = line.trimStart()

            // Standalone bold line → subheading (common in LLM summaries): `**标题**`
            if (trimmed.startsWith("**") && trimmed.endsWith("**")) {
                val innerBold = trimmed.removeSurrounding("**").trim()
                if (innerBold.isNotBlank() && !innerBold.contains("**")) {
                    closeLists()
                    sink.appendLine(headingHtml("h3", S.h3, innerBold))
                    i++
                    continue
                }
            }

            when {
                trimmed.startsWith("#### ") -> {
                    closeLists()
                    sink.appendLine(headingHtml("h3", S.h3, trimmed.removePrefix("#### ").trim()))
                }
                trimmed.startsWith("### ") -> {
                    closeLists()
                    sink.appendLine(headingHtml("h3", S.h3, trimmed.removePrefix("### ").trim()))
                }
                trimmed.startsWith("## ") -> {
                    closeLists()
                    sink.appendLine(headingHtml("h2", S.h2, trimmed.removePrefix("## ").trim()))
                }
                trimmed.startsWith("# ") -> {
                    closeLists()
                    sink.appendLine(headingHtml("h1", S.h1, trimmed.removePrefix("# ").trim()))
                }
                isBulletLine(trimmed) -> {
                    openUl()
                    sink.appendLine("<li style='${S.li}'>${inlineMarkdown(stripBullet(trimmed))}</li>")
                }
                orderedLine.matches(trimmed) -> {
                    val m = orderedLine.matchEntire(trimmed)!!
                    openOl()
                    sink.appendLine("<li style='${S.li}'>${inlineMarkdown(m.groupValues[2].trim())}</li>")
                }
                Regex("""^\d+\.\s+""").containsMatchIn(trimmed) -> {
                    openOl()
                    val item = trimmed.replaceFirst(Regex("""^\d+\.\s+"""), "").trim()
                    sink.appendLine("<li style='${S.li}'>${inlineMarkdown(item)}</li>")
                }
                else -> {
                    closeLists()
                    sink.appendLine("<p style='${S.p}'>${inlineMarkdown(trimmed)}</p>")
                }
            }
            i++
        }
        if (inFence) flushFence()
        closeLists()
    }

    private data class TableParseResult(val html: String, val endIndexExclusive: Int)

    /**
     * GFM table: header row, separator row (`| :--- | :--- |`), then body rows.
     * Ends at blank line or non-table-looking line.
     */
    private fun tryParseMarkdownTable(lines: List<String>, start: Int): TableParseResult? {
        if (start + 1 >= lines.size) return tryParseLoosePipeTable(lines, start)
        val row0 = lines[start].trim()
        val row1 = lines[start + 1].trim()
        if (!row0.contains('|') || row1.isBlank() || !row1.contains('|')) {
            return tryParseLoosePipeTable(lines, start)
        }

        val header = splitMarkdownTableRow(row0) ?: return tryParseLoosePipeTable(lines, start)
        if (header.size < 2) return tryParseLoosePipeTable(lines, start)

        val sepCells = splitMarkdownTableRow(row1) ?: return tryParseLoosePipeTable(lines, start)
        if (!isMarkdownTableSeparatorCells(sepCells) || sepCells.size != header.size) {
            return tryParseLoosePipeTable(lines, start)
        }

        val n = header.size
        val body = mutableListOf<List<String>>()
        var j = start + 2
        while (j < lines.size) {
            val rawLine = lines[j]
            val tt = rawLine.trim()
            if (tt.isBlank()) break
            if (tt.startsWith('#') || tt.startsWith('>')) break
            if (tt.startsWith("```")) break
            if (!tt.contains('|')) break
            if (tt.startsWith("- ") || tt.startsWith("* ") || tt.startsWith("• ")) break

            val rowCells = splitMarkdownTableRow(tt) ?: break
            if (isMarkdownTableSeparatorCells(rowCells)) {
                j++
                continue
            }
            body.add(normalizeTableRow(rowCells, n))
            j++
        }
        return TableParseResult(renderMarkdownTable(header, body), j)
    }

    /**
     * Tables without separator row (common LLM mistake): consecutive `| a | b |` lines, same column count.
     */
    private fun tryParseLoosePipeTable(lines: List<String>, start: Int): TableParseResult? {
        val row0 = lines[start].trim()
        if (!row0.contains('|')) return null
        val header = splitMarkdownTableRow(row0) ?: return null
        if (header.size < 2) return null
        val n = header.size

        val allRows = mutableListOf(header)
        var j = start + 1
        while (j < lines.size) {
            val tt = lines[j].trim()
            if (tt.isBlank()) break
            if (tt.startsWith('#') || tt.startsWith('>')) break
            if (tt.startsWith("```")) break
            if (!tt.contains('|')) break
            if (tt.startsWith("- ") || tt.startsWith("* ") || tt.startsWith("• ")) break

            val rowCells = splitMarkdownTableRow(tt) ?: break
            if (rowCells.size != n) break
            if (isMarkdownTableSeparatorCells(rowCells)) break
            allRows.add(rowCells)
            j++
        }
        if (allRows.size < 2) return null

        val head = allRows.first()
        val bodyRows = allRows.drop(1).map { normalizeTableRow(it, n) }
        return TableParseResult(renderMarkdownTable(head, bodyRows), j)
    }

    private fun splitMarkdownTableRow(line: String): List<String>? {
        val t = line.trim()
        if (!t.contains('|')) return null
        var inner = if (t.startsWith("|")) t.substring(1) else t
        inner = if (inner.endsWith("|")) inner.dropLast(1) else inner
        val parts = inner.split('|').map { it.trim() }
        return parts.takeIf { it.isNotEmpty() }
    }

    private fun isMarkdownTableSeparatorCells(cells: List<String>): Boolean {
        if (cells.isEmpty()) return false
        return cells.all { cell ->
            val c = cell.trim()
            c.matches(Regex("^:?-{2,}:?$"))
        }
    }

    private fun normalizeTableRow(row: List<String>, width: Int): List<String> =
        List(width) { idx -> row.getOrNull(idx).orEmpty() }

    private fun renderMarkdownTable(header: List<String>, body: List<List<String>>): String {
        val n = header.size
        val sb = StringBuilder()
        sb.append("<table style='${S.tableWrap}'>")
        sb.append("<thead><tr>")
        for (h in header) {
            sb.append("<th style='${S.thCell}'>${inlineMarkdown(h)}</th>")
        }
        sb.append("</tr></thead><tbody>")
        for (row in body) {
            sb.append("<tr>")
            val r = normalizeTableRow(row, n)
            for (c in r) {
                sb.append("<td style='${S.tdCell}'>${inlineMarkdown(c)}</td>")
            }
            sb.append("</tr>")
        }
        sb.append("</tbody></table>")
        return sb.toString()
    }

    private fun escapeHtml(s: String): String =
        s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun escapeHtmlAttr(s: String): String =
        s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    private val mdLink = Regex("""\[([^\]]*)\]\(([^)]+)\)""")

    private fun isSafeHref(href: String): Boolean {
        val h = href.trim().lowercase()
        if (h.startsWith("javascript:") || h.startsWith("data:")) return false
        return h.startsWith("logiccode:") ||
            h.startsWith("http://") ||
            h.startsWith("https://") ||
            h.startsWith("mailto:") ||
            h.startsWith("#") ||
            h.startsWith("./") ||
            h.startsWith("../") ||
            (!h.contains(":") && h.isNotBlank())
    }

    /**
     * Turns `[text](url)` into `<a href>` when [href] is allow-listed; otherwise escapes the raw match.
     * Runs before `**bold**` so bold can wrap link text in the same segment.
     */
    private fun applyMarkdownLinks(text: String): String =
        mdLink.replace(text) { m ->
            val label = m.groupValues[1]
            val href = m.groupValues[2].trim()
            if (!isSafeHref(href)) {
                escapeHtml(m.value)
            } else {
                val aStyle = "color:${S.BLUE};text-decoration:underline;"
                """<a href="${escapeHtmlAttr(href)}" style="$aStyle">${escapeHtml(label)}</a>"""
            }
        }

    /** Applies `**bold**` to [segment] which may already contain literal `<a ...>` tags (do not escape those as text). */
    private fun applyBoldAllowingHtml(segment: String): String {
        val boldParts = segment.split("**")
        return boldParts.mapIndexed { bi, piece ->
            if (bi % 2 == 1) "<strong style='${S.strong}'>$piece</strong>" else piece
        }.joinToString("")
    }

    private fun inlineMarkdown(raw: String): String {
        if (raw.isEmpty()) return ""
        val tickParts = raw.split('`')
        return tickParts.mapIndexed { ti, segment ->
            if (ti % 2 == 1) {
                "<span style='${S.inlineCode}'>${escapeHtml(segment)}</span>"
            } else {
                applyBoldAllowingHtml(applyMarkdownLinks(segment))
            }
        }.joinToString("")
    }

    private fun iconForHeading(title: String): String {
        val s = title.lowercase()
        return when {
            s.contains("架构") || s.contains("architecture") || s.contains("overview") -> "🏗️"
            s.contains("风险") || s.contains("risk") || s.contains("问题") -> "⚠️"
            s.contains("入口") || s.contains("entry") || s.contains("endpoint") || s.contains("controller") -> "🚪"
            s.contains("数据库") || s.contains("mysql") || s.contains("mapper") ||
                s.contains("表") || s.contains("persist") || s.contains("仓储") -> "🗄️"
            s.contains("配置") || s.contains("config") || s.contains("properties") || s.contains("yaml") -> "⚙️"
            s.contains("总结") || s.contains("概括") || s.contains("结论") -> "📌"
            s.contains("异常") || s.contains("错误") || s.contains("exception") || s.contains("故障") -> "🔥"
            s.contains("部署") || s.contains("deploy") || s.contains("docker") -> "🚀"
            s.contains("测试") || s.contains("test") || s.contains("junit") -> "🧪"
            s.contains("安全") || s.contains("security") || s.contains("auth") -> "🔐"
            s.contains("缓存") || s.contains("redis") || s.contains("cache") -> "⚡"
            s.contains("消息") || s.contains("kafka") || s.contains("mq") -> "📨"
            s.contains("流程") || s.contains("flow") || s.contains("链路") || s.contains("调用") -> "🔀"
            s.contains("步骤") || s.contains("分支") || s.contains("执行顺序") -> "📍"
            s.contains("模块") || s.contains("module") -> "📦"
            else -> ""
        }
    }
}
