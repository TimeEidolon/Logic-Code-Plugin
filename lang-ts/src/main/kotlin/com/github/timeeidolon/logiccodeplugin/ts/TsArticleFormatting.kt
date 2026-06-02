package com.github.timeeidolon.logiccodeplugin.ts

/**
 * Tightens vertical whitespace in plugin-generated markdown so previews and editors feel less "airy".
 */
object TsArticleFormatting {

    fun tightenVerticalWhitespace(md: String): String {
        var s = md.trimEnd()
        s = s.replace(Regex("\n{3,}"), "\n\n")
        s = s.replace(Regex("\n\n(?=#{1,6} )"), "\n")
        s = s.replace(Regex("\n\n(?=>)"), "\n")
        s = s.replace(Regex("\n\n(?=<a\\b)"), "\n")
        return s.trimEnd() + "\n"
    }
}
