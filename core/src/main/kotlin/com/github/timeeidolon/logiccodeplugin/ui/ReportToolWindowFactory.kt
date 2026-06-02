package com.github.timeeidolon.logiccodeplugin.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory
class ReportToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val reportToolWindow = ReportToolWindow(project)
        ReportToolWindowRegistry.register(project, reportToolWindow)
        Disposer.register(toolWindow.disposable, reportToolWindow.analysisPanelDisposable)
        Disposer.register(toolWindow.disposable, reportToolWindow.reportMarkdownPreviewsDisposable)
        Disposer.register(toolWindow.disposable, Disposable {
            ReportToolWindowRegistry.unregister(project)
        })
        val content = ContentFactory.getInstance().createContent(reportToolWindow.content, null, false)
        toolWindow.contentManager.addContent(content)
    }
}
