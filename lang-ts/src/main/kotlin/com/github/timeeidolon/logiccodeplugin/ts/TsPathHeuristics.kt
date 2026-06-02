package com.github.timeeidolon.logiccodeplugin.ts

/**
 * Path helpers shared by TS decomposition scanners (HelpCenter `src/pages/<pkg>/` layout).
 */
object TsPathHeuristics {

    private const val PAGES_PREFIX = "src/pages/"

    fun extractPagePackage(relativePath: String): String? {
        val norm = relativePath.replace('\\', '/')
        val idx = norm.indexOf(PAGES_PREFIX)
        if (idx < 0) return null
        val rest = norm.substring(idx + PAGES_PREFIX.length)
        val pkg = rest.substringBefore('/')
        if (pkg.isBlank()) return null
        return "$PAGES_PREFIX$pkg"
    }

    fun relativizePath(basePath: String, absolutePath: String): String? {
        val normBase = basePath.trimEnd('/', '\\').replace('\\', '/')
        val normPath = absolutePath.replace('\\', '/')
        return if (normPath.startsWith(normBase)) normPath.removePrefix(normBase).trimStart('/') else null
    }
}
