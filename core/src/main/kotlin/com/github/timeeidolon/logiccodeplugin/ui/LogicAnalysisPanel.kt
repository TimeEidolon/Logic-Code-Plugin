package com.github.timeeidolon.logiccodeplugin.ui

import com.github.timeeidolon.logiccodeplugin.bridge.JavaFeatureBridge
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.Insets
import java.net.URLDecoder
import javax.swing.Icon
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTabbedPane
import javax.swing.SwingUtilities

class LogicAnalysisPanel(private val project: Project) : Disposable {
    private val header = JLabel("Click a gutter icon to see analysis here.")
    private val refreshButton = JButton(AllIcons.Actions.Refresh).apply {
        toolTipText = "Refresh logic (re-analyze and clear cache)"
        isFocusable = false
        isOpaque = false
        isContentAreaFilled = false
        border = JBUI.Borders.empty(2)
        isVisible = false
    }
    private var refreshAction: (() -> Unit)? = null

    private val headerBar = JPanel(BorderLayout()).apply {
        add(header, BorderLayout.WEST)
        add(refreshButton, BorderLayout.EAST)
        border = JBUI.Borders.empty(4, 6, 4, 6)
    }

    private val tabbed = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }

    private val logicPreview = MarkdownPreviewPanel()

    private val browser: JBCefBrowser? = if (JBCefApp.isSupported()) JBCefBrowser() else null
    private var zoomSteps: Int = 0
    private val zoomLabel = JLabel("100%").apply {
        font = JBFont.small()
        foreground = JBColor.foreground()
    }

    private fun iconButton(icon: Icon, tooltip: String, onClick: () -> Unit): JButton =
        JButton(icon).apply {
            toolTipText = tooltip
            margin = Insets(0, 0, 0, 0)
            isFocusable = false
            isOpaque = false
            isContentAreaFilled = false
            border = JBUI.Borders.empty(2)
            addActionListener { onClick() }
        }

    private val zoomBar = JPanel(FlowLayout(FlowLayout.LEFT, 2, 2)).apply {
        isOpaque = true
        background = JBUI.CurrentTheme.ToolWindow.background()
        border = JBUI.Borders.compound(
            JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
            JBUI.Borders.empty(2, 4)
        )
        add(iconButton(AllIcons.General.Remove, "Zoom out", onClick = { setZoomSteps(zoomSteps - 1) }))
        add(iconButton(AllIcons.General.Add, "Zoom in", onClick = { setZoomSteps(zoomSteps + 1) }))
        add(iconButton(AllIcons.Actions.Refresh, "Reset zoom", onClick = { setZoomSteps(0) }))
        add(zoomLabel)
    }

    private val flowContainer: JComponent? = browser?.let { b ->
        JPanel(BorderLayout()).apply {
            // JCEF is heavyweight; overlay toolbars are unreliable. Keep toolbar inside the tab.
            add(zoomBar, BorderLayout.NORTH)
            add(b.component, BorderLayout.CENTER)
        }
    }

    val component: JComponent = JPanel(BorderLayout()).apply {
        add(headerBar, BorderLayout.NORTH)
        add(tabbed, BorderLayout.CENTER)
    }

    init {
        refreshButton.addActionListener { refreshAction?.invoke() }

        tabbed.addTab("Logic", logicPreview.component)
        logicPreview.installLogicCodeNavigation(project)
        browser?.let { b ->
            registerFlowConsoleNavigation(b)
            flowContainer?.let { tabbed.addTab("Flow", it) }
        }
    }

    private fun registerFlowConsoleNavigation(b: JBCefBrowser) {
        val client = b.logicCodeJcefClient() ?: return
        client.addDisplayHandler(
            object : CefDisplayHandlerAdapter() {
                override fun onConsoleMessage(
                    cefBrowser: CefBrowser?,
                    level: CefSettings.LogSeverity?,
                    message: String?,
                    source: String?,
                    line: Int
                ): Boolean {
                    val m = message ?: return false
                    if (!m.startsWith("LOGICCODE_FLOW_NAV:")) return false
                    val encoded = m.removePrefix("LOGICCODE_FLOW_NAV:")
                    val decoded = runCatching {
                        URLDecoder.decode(encoded, Charsets.UTF_8.name())
                    }.getOrNull() ?: encoded
                    JavaFeatureBridge.tryNavigateFlowNode(project, decoded)
                    return true
                }
            },
            b.cefBrowser
        )
    }

    override fun dispose() {
        logicPreview.dispose()
        browser?.dispose()
    }

    fun showLoading(title: String, message: String) {
        header.text = title
        // Preserve refresh handler: callers set it before showLoading when re-fetching LLM output.
        refreshButton.isVisible = refreshAction != null
        logicPreview.setMarkdown(message)
        tabbed.selectedIndex = 0
    }

    fun showLogic(title: String, content: String) {
        header.text = title
        logicPreview.setMarkdown(content)
        tabbed.selectedIndex = 0
    }

    fun showMermaid(title: String, mermaidBody: String) {
        header.text = title
        browser?.loadHTML(buildMermaidHtml(mermaidBody))
        SwingUtilities.invokeLater { applyScale() }
        // Prefer Flow if available, else Mermaid text
        tabbed.selectedIndex = 1
    }

    private fun setZoomSteps(steps: Int) {
        zoomSteps = steps.coerceIn(-12, 24)
        val scale = Math.pow(1.15, zoomSteps.toDouble())
        val percent = (scale * 100.0).toInt()
        zoomLabel.text = "${percent}%"
        applyScale()
    }

    fun clear() {
        header.text = ""
        refreshButton.isVisible = false
        refreshAction = null
        logicPreview.clear()
        browser?.loadHTML("<html><body style='background:#1e1e1e;'></body></html>")
        setZoomSteps(0)
    }

    fun enableRefresh(action: () -> Unit) {
        refreshAction = action
        refreshButton.isVisible = true
    }

    private fun applyScale() {
        val b = browser?.cefBrowser ?: return
        val scale = Math.pow(1.15, zoomSteps.toDouble())
        b.executeJavaScript("window.__setMermaidScale && window.__setMermaidScale($scale);", b.url, 0)
    }

    private fun buildMermaidHtml(mermaid: String): String {
        val escaped = mermaid
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

        val mermaidJs = MermaidWebJar.mermaidMinJs
        if (mermaidJs.isBlank()) {
            return """
                <!doctype html>
                <html>
                  <head><meta charset="utf-8" /></head>
                  <body style="margin:0;padding:12px;background:#1e1e1e;color:#ddd;font-family:monospace;">
                    Mermaid asset not found in plugin runtime.
                  </body>
                </html>
            """.trimIndent()
        }

        return """
            <!doctype html>
            <html>
              <head>
                <meta charset="utf-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1" />
                <style>
                  html, body { margin: 0; padding: 0; background: #1e1e1e; height: 100%; }
                  .wrap { position: fixed; inset: 0; overflow: auto; cursor: grab; }
                  .wrap.dragging { cursor: grabbing; }
                  #scaleRoot { transform-origin: 0 0; }
                  .mermaid { color: #ddd; }
                  svg { display: block; }
                </style>
              </head>
              <body>
                <div class="wrap">
                  <div id="scaleRoot">
                    <pre class="mermaid">$escaped</pre>
                  </div>
                </div>
                <script>
                $mermaidJs
                </script>
                <script>
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
                    window.__setMermaidScale = function(scale) {
                      const root = document.getElementById('scaleRoot');
                      if (!root) return;
                      root.style.transform = 'scale(' + scale + ')';
                    };

                    function hookFlowNav() {
                      document.querySelectorAll('.mermaid svg g.node').forEach(function(g) {
                        g.addEventListener('dblclick', function(ev) {
                          ev.stopPropagation();
                          var t = (g.textContent || '').trim();
                          if (t.length > 0) {
                            console.error('LOGICCODE_FLOW_NAV:' + encodeURIComponent(t.substring(0, 4000)));
                          }
                        });
                      });
                    }

                    mermaid.run({ querySelector: '.mermaid' }).then(hookFlowNav).catch(function() { hookFlowNav(); });

                    // Drag to pan
                    (function() {
                      const wrap = document.querySelector('.wrap');
                      if (!wrap) return;
                      let dragging = false;
                      let startX = 0, startY = 0;
                      let startLeft = 0, startTop = 0;
                      wrap.addEventListener('mousedown', (e) => {
                        if (e.button !== 0) return;
                        dragging = true;
                        wrap.classList.add('dragging');
                        startX = e.clientX;
                        startY = e.clientY;
                        startLeft = wrap.scrollLeft;
                        startTop = wrap.scrollTop;
                        e.preventDefault();
                      });
                      window.addEventListener('mouseup', () => {
                        dragging = false;
                        wrap.classList.remove('dragging');
                      });
                      window.addEventListener('mousemove', (e) => {
                        if (!dragging) return;
                        const dx = e.clientX - startX;
                        const dy = e.clientY - startY;
                        wrap.scrollLeft = startLeft - dx;
                        wrap.scrollTop = startTop - dy;
                      });
                    })();
                  } catch (e) {
                    document.body.innerHTML = '<div style="padding:12px;color:#ddd;font-family:monospace;">Mermaid init failed: ' + e + '</div>';
                  }
                </script>
              </body>
            </html>
        """.trimIndent()
    }
}

