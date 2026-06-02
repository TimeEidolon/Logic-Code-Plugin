package com.github.timeeidolon.logiccodeplugin.ui

import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap

/**
 * Holds the active [ReportToolWindow] per project so actions (e.g. Project View) can update status without modal dialogs.
 */
object ReportToolWindowRegistry {

    private val instances = ConcurrentHashMap<Project, ReportToolWindow>()

    fun register(project: Project, toolWindow: ReportToolWindow) {
        instances[project] = toolWindow
    }

    fun unregister(project: Project) {
        instances.remove(project)
    }

    fun get(project: Project): ReportToolWindow? = instances[project]

    fun setStatusIfAvailable(project: Project, message: String) {
        instances[project]?.setStatusMessage(message)
    }

    fun activateReportsTabAndSelectTsOverview(project: Project) {
        instances[project]?.activateReportsTabAndSelectTsOverview()
    }
}
