package com.github.timeeidolon.logiccodeplugin.project

import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.intellij.openapi.project.Project

/**
 * Centralizes when Java vs TS-related actions appear in the UI (see [ReportPluginSettings]).
 */
object LogicCodeActionVisibility {

    fun shouldShowJavaProjectReport(project: Project): Boolean {
        val s = ReportPluginSettings.getInstance().state
        if (!s.hideJavaReportWithoutJvmSignals) return true
        return ProjectKindHeuristics.signals(project).hasJvmMarkers
    }

    fun shouldShowTsDocumentationActions(project: Project): Boolean {
        val s = ReportPluginSettings.getInstance().state
        if (!s.hideTsDocsWithoutFrontendSignals) return true
        return ProjectKindHeuristics.signals(project).hasFrontendMarkers
    }
}
