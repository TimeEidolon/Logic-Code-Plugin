package com.github.timeeidolon.logiccodeplugin.inventory

import com.intellij.openapi.project.Project

class ProjectInventoryScanner(private val project: Project) {

    private val moduleAnalyzer = ModuleAnalyzer(project)
    private val annotationVisitor = AnnotationVisitor(project)
    private val resourceCollector = ResourceCollector(project)

    fun scan(): InventoryResult {
        val modules = moduleAnalyzer.analyze()
        val signals = annotationVisitor.scan()
        val resources = resourceCollector.collect()

        return InventoryResult(
            projectName = project.name,
            projectPath = project.basePath ?: "",
            modules = modules,
            signals = signals,
            resourceFiles = resources
        )
    }
}
