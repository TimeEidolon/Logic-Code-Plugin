package com.github.timeeidolon.logiccodeplugin.actions

import com.github.timeeidolon.logiccodeplugin.MyBundle
import com.github.timeeidolon.logiccodeplugin.project.LogicCodeActionVisibility
import com.github.timeeidolon.logiccodeplugin.ts.FrontendRouteGuideWriter
import com.github.timeeidolon.logiccodeplugin.ui.LogicCodeBalloonNotifier
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

class GenerateFrontendRouteGuideAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: frontend route guide", true) {
            override fun run(indicator: ProgressIndicator) {
                val output = writeGuide(project)
                javax.swing.SwingUtilities.invokeLater {
                    openFile(project, output)
                    LogicCodeBalloonNotifier.notifyInfo(
                        project,
                        MyBundle.message("notification.logic.code.title"),
                        MyBundle.message("notification.ts.route.guide", output.absolutePath),
                    )
                }
            }
        })
    }

    private fun writeGuide(project: Project): java.io.File = FrontendRouteGuideWriter.write(project)

    private fun openFile(project: Project, file: java.io.File) {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        FileEditorManager.getInstance(project).openFile(vf, true)
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val show = LogicCodeActionVisibility.shouldShowTsDocumentationActions(project)
        e.presentation.isVisible = show
        e.presentation.isEnabled = show
    }
}

