package com.github.timeeidolon.logiccodeplugin.ui

import com.github.timeeidolon.logiccodeplugin.nav.LogicCodeUriNavigator
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefRequestHandlerAdapter
import org.cef.network.CefRequest
import java.awt.CardLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.event.HyperlinkEvent

/** Renders Markdown in [JBHtmlPane], or switches to JCEF when ```mermaid``` blocks are present. */
class MarkdownPreviewPanel : Disposable {
    private val htmlPane =
        JBHtmlPane().apply {
            isEditable = false
            text = ""
        }
    private val htmlScroll = JBScrollPane(htmlPane)
    private val cefBrowser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null

    private var cefRequestNavHandler: CefRequestHandlerAdapter? = null
    private var navigationProject: Project? = null
    private var navigationInstalled = false

    private val cards =
        JPanel(CardLayout()).apply {
            add(htmlScroll, "html")
            cefBrowser?.let { add(JBScrollPane(it.component), "cef") }
        }

    val component: JComponent = cards

    /**
     * Enables `logiccode:` / http(s) navigation from rendered markdown. Safe to call once per panel.
     */
    fun installLogicCodeNavigation(project: Project) {
        if (navigationInstalled) return
        navigationInstalled = true
        navigationProject = project

        htmlPane.addHyperlinkListener { e ->
            if (e.eventType != HyperlinkEvent.EventType.ACTIVATED) return@addHyperlinkListener
            val desc = e.description ?: e.url?.toString() ?: return@addHyperlinkListener
            if (desc.startsWith("logiccode:")) {
                LogicCodeUriNavigator.navigate(project, desc)
            } else if (desc.startsWith("http://") || desc.startsWith("https://")) {
                LogicCodeUriNavigator.navigate(project, desc)
            }
        }

        val b = cefBrowser ?: return
        val client = b.logicCodeJcefClient() ?: return
        val handler =
            object : CefRequestHandlerAdapter() {
                override fun onBeforeBrowse(
                    browser: CefBrowser?,
                    frame: CefFrame?,
                    request: CefRequest?,
                    userGesture: Boolean,
                    isRedirect: Boolean,
                ): Boolean {
                    val url = request?.url ?: return false
                    if (url.startsWith("logiccode:")) {
                        val p = navigationProject ?: return true
                        ApplicationManager.getApplication().invokeLater {
                            LogicCodeUriNavigator.navigate(p, url)
                        }
                        return true
                    }
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        val p = navigationProject ?: return true
                        ApplicationManager.getApplication().invokeLater {
                            LogicCodeUriNavigator.navigate(p, url)
                        }
                        return true
                    }
                    return false
                }
            }
        cefRequestNavHandler = handler
        client.addRequestHandler(handler, b.cefBrowser)
    }

    fun setMarkdown(raw: String) {
        val merged =
            if (cefBrowser != null && raw.contains("```mermaid", ignoreCase = true)) {
                MarkdownMermaidPage.build(raw)
            } else {
                null
            }
        val cl = cards.layout as CardLayout
        if (merged != null && cefBrowser != null) {
            cefBrowser.loadHTML(merged)
            cl.show(cards, "cef")
        } else {
            htmlPane.text = SimpleMarkdownHtml.render(raw)
            cl.show(cards, "html")
        }
        htmlPane.caretPosition = 0
    }

    fun clear() {
        htmlPane.text = ""
        cefBrowser?.loadHTML("<html><body style=\"background:#1a1e1e;\"></body></html>")
        (cards.layout as CardLayout).show(cards, "html")
    }

    override fun dispose() {
        val h = cefRequestNavHandler
        val b = cefBrowser
        if (h != null && b != null) {
            runCatching { b.logicCodeJcefClient()?.removeRequestHandler(h, b.cefBrowser) }
        }
        cefRequestNavHandler = null
        navigationProject = null
        cefBrowser?.dispose()
    }
}
