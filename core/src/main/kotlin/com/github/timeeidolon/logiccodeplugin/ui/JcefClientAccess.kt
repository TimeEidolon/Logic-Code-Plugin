package com.github.timeeidolon.logiccodeplugin.ui

import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefClient

/**
 * JBCefBrowser accessor indirection: accessor names differ across platform releases.
 */
internal fun JBCefBrowser.logicCodeJcefClient(): JBCefClient? {
    return try {
        javaClass.getMethod("getJBCefClient").invoke(this) as? JBCefClient
    } catch (_: Throwable) {
        null
    }
}
