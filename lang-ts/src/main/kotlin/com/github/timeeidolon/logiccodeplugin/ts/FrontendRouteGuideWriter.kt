package com.github.timeeidolon.logiccodeplugin.ts

import com.github.timeeidolon.logiccodeplugin.cleanup.GeneratedFilesManifestService
import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.intellij.openapi.project.Project
import java.nio.file.Files
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

/**
 * Writes `docs/report/FRONTEND_ROUTE_GUIDE.md` (or under configured output dir).
 * Used by [com.github.timeeidolon.logiccodeplugin.actions.GenerateFrontendRouteGuideAction].
 */
object FrontendRouteGuideWriter {

    @JvmStatic
    fun write(project: Project): java.io.File {
        val settings = ReportPluginSettings.getInstance().state
        val projectRoot = Path(project.basePath ?: throw IllegalStateException("Project base path is null"))
        val outputDir = if (!settings.outputDir.isNullOrBlank()) projectRoot.resolve(settings.outputDir) else projectRoot.resolve("docs")
        val reportDir = outputDir.resolve("report").createDirectories()
        val target = reportDir.resolve("FRONTEND_ROUTE_GUIDE.md")

        val routes = TsReactRouteCollector(project).collect()
            .sortedWith(compareBy({ it.path }, { it.filePath }))

        val md = TsArticleFormatting.tightenVerticalWhitespace(
            buildString {
                appendLine("# Frontend Route Guide (static scan)")
                appendLine("- Project: `${project.name}`")
                appendLine("- Total routes found: ${routes.size}")
                appendLine("## Routes")
                if (routes.isEmpty()) {
                    appendLine("- (No react-router patterns found. Check project routing setup.)")
                } else {
                    for (r in routes) {
                        val comp = r.component?.let { " → `$it`" } ?: ""
                        appendLine("- `${r.path}`$comp  (${r.kind})  — `${r.filePath}`")
                    }
                }
                appendLine("## Notes")
                appendLine("- This is a best-effort scan for `react-router` v5-like patterns (`<Route path=... component={...}>` and simple route objects).")
                appendLine("- For dynamic routes or custom wrappers, you may need to extend the regex rules.")
            }.toString(),
        )

        Files.writeString(target, md)
        GeneratedFilesManifestService.getInstance(project).recordGeneratedFile(project, target)
        return target.toFile()
    }
}
