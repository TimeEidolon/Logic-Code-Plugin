package com.github.timeeidolon.logiccodeplugin.db

object SqlTableExtractor {
    private val WS = """[\s\n\r\t]+"""
    private val IDENT = """[A-Za-z0-9_.$]+"""

    private val FROM_JOIN = Regex("""(?is)\b(from|join)$WS($IDENT)""")
    private val UPDATE = Regex("""(?is)\bupdate$WS($IDENT)\b""")
    private val INSERT = Regex("""(?is)\binsert$WS+into$WS($IDENT)\b""")
    private val DELETE = Regex("""(?is)\bdelete$WS+from$WS($IDENT)\b""")
    private val SELECT = Regex("""(?is)\bselect\b""")

    data class Hit(val table: String, val op: TableUsage.Op)

    fun extract(sql: String): List<Hit> {
        val s = normalize(sql)
        if (!looksLikeSql(s)) return emptyList()

        val hits = mutableListOf<Hit>()
        UPDATE.findAll(s).forEach { hits.add(Hit(normalizeTable(it.groupValues[1]), TableUsage.Op.WRITE)) }
        INSERT.findAll(s).forEach { hits.add(Hit(normalizeTable(it.groupValues[1]), TableUsage.Op.WRITE)) }
        DELETE.findAll(s).forEach { hits.add(Hit(normalizeTable(it.groupValues[1]), TableUsage.Op.WRITE)) }
        FROM_JOIN.findAll(s).forEach { hits.add(Hit(normalizeTable(it.groupValues[2]), TableUsage.Op.READ)) }

        return hits
            .filter { it.table.isNotBlank() }
            .distinctBy { it.op to it.table.lowercase() }
    }

    private fun looksLikeSql(s: String): Boolean {
        val hasDml = UPDATE.containsMatchIn(s) || INSERT.containsMatchIn(s) || DELETE.containsMatchIn(s)
        val hasSelect = SELECT.containsMatchIn(s) && (FROM_JOIN.containsMatchIn(s))
        return hasDml || hasSelect
    }

    private fun normalize(sql: String): String =
        sql
            .replace('\u0000', ' ')
            .replace(Regex("""--.*?$""", setOf(RegexOption.MULTILINE)), " ")
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex(WS), " ")
            .trim()

    private fun normalizeTable(raw: String): String {
        var t = raw.trim()
        t = t.trim('"', '`')
        // Strip alias (best-effort)
        t = t.split(' ', '\t', '\n', '\r').firstOrNull().orEmpty()
        // Remove trailing punctuation
        t = t.trimEnd(',', ';', ')')
        // Remove leading '(' for subquery artifacts
        t = t.trimStart('(')
        return t
    }
}

