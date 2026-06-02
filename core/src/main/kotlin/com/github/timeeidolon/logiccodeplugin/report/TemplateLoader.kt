package com.github.timeeidolon.logiccodeplugin.report

import com.github.timeeidolon.logiccodeplugin.skill.SkillTemplateResolver
import com.intellij.openapi.project.Project

object TemplateLoader {

    private const val WORKFLOW_FILE = "JAVA_PROJECT_REPORT_WORKFLOW.md"
    private const val TEMPLATE_FILE = "JAVA_PROJECT_REPORT_TEMPLATE.md"

    fun loadWorkflow(project: Project): String =
        SkillTemplateResolver(project).loadTextOrEmpty(
            filename = WORKFLOW_FILE,
            bundledResourcePath = "java/$WORKFLOW_FILE"
        )

    fun loadReportTemplate(project: Project): String =
        SkillTemplateResolver(project).loadTextOrEmpty(
            filename = TEMPLATE_FILE,
            bundledResourcePath = "java/$TEMPLATE_FILE"
        )
}
