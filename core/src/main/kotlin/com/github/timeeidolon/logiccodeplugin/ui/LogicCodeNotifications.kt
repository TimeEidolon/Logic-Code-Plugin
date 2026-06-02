package com.github.timeeidolon.logiccodeplugin.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil

object LogicCodeNotifications {

    /**
     * [Messages.showInfoMessage] can render an empty body for multiline plain text on some IDE builds / themes.
     * Wrap as HTML with escaped text so paths and special characters display reliably.
     */
    fun showInfoDialog(project: Project?, title: String, plainMessage: String) {
        val html = buildHtmlMessage(plainMessage)
        Messages.showInfoMessage(project, html, title)
    }

    private fun buildHtmlMessage(plainMessage: String): String {
        val escaped = StringUtil.escapeXmlEntities(plainMessage)
        return (
            "<html><body style='width:520px'>" +
                "<pre style='white-space:pre-wrap;word-wrap:break-word;font-family:Dialog;font-size:12pt;margin:0'>" +
                escaped +
                "</pre></body></html>"
            )
    }
}
