package com.github.timeeidolon.logiccodeplugin.cache

import com.intellij.psi.PsiMethod

/**
 * Fingerprint that stays stable under comment-only and most whitespace-only edits,
 * so PSI change events can skip cache invalidation when the "logic text" is unchanged.
 */
object MethodBodyFingerprints {

    fun logicStableFingerprint(method: PsiMethod): Int {
        val body = method.body ?: return method.text.trim().hashCode()
        var s = body.text
        s = s.replace(BLOCK_COMMENT_REGEX, " ")
        s = s.replace(LINE_COMMENT_REGEX, " ")
        return s.replace(WHITESPACE_REGEX, " ").trim().hashCode()
    }

    private val BLOCK_COMMENT_REGEX = Regex("/\\*.*?\\*/", RegexOption.DOT_MATCHES_ALL)
    private val LINE_COMMENT_REGEX = Regex("//[^\n\r]*")
    private val WHITESPACE_REGEX = Regex("\\s+")
}
