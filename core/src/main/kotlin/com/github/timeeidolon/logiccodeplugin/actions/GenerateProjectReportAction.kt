package com.github.timeeidolon.logiccodeplugin.actions

import com.github.timeeidolon.logiccodeplugin.MyBundle
import com.github.timeeidolon.logiccodeplugin.llm.ApiKeyResolver
import com.github.timeeidolon.logiccodeplugin.project.LogicCodeActionVisibility
import com.github.timeeidolon.logiccodeplugin.report.ReportGenerator
import com.github.timeeidolon.logiccodeplugin.ui.LogicCodeBalloonNotifier
import com.github.timeeidolon.logiccodeplugin.ui.ReportToolWindowRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.wm.ToolWindowManager

class GenerateProjectReportAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val apiKey = ApiKeyResolver.resolve()

        if (apiKey.isNullOrBlank()) {
            ToolWindowManager.getInstance(project).getToolWindow("LogicCodeReport")?.activate(null)
            ReportToolWindowRegistry.setStatusIfAvailable(
                project,
                "Missing LLM API key — Settings → Tools → Logic Code Report, or LLM_API_KEY / OPENAI_API_KEY"
            )
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: generating report", true) {
            override fun run(indicator: ProgressIndicator) {
                val generator = ReportGenerator(project)
                val toolWindow = ReportToolWindowRegistry.get(project)
                javax.swing.SwingUtilities.invokeLater { toolWindow?.resetGenerateSteps() }
                val result = generator.generate(
                    callModel = true,
                    apiKey = apiKey,
                    indicator = indicator,
                    onStep = { step ->
                        javax.swing.SwingUtilities.invokeLater { toolWindow?.onGenerateStep(step) }
                    }
                )

                indicator.fraction = 1.0
                indicator.text = "Done — report: ${result.reportPath?.fileName ?: "PROJECT_ANALYSIS_REPORT.md"}"

                javax.swing.SwingUtilities.invokeLater {
                    ToolWindowManager.getInstance(project).getToolWindow("LogicCodeReport")?.activate(null)
                    ReportToolWindowRegistry.get(project)?.showReportGenerateResult(result)
                    result.reportPath?.let { p ->
                        LogicCodeBalloonNotifier.notifyInfo(
                            project,
                            MyBundle.message("notification.logic.code.title"),
                            MyBundle.message("notification.report.done", p.toString()),
                        )
                    }
                }
            }
        })
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null) {
            e.presentation.isEnabledAndVisible = false
            return
        }
        val show = LogicCodeActionVisibility.shouldShowJavaProjectReport(project)
        e.presentation.isVisible = show
        e.presentation.isEnabled = show
    }
}
