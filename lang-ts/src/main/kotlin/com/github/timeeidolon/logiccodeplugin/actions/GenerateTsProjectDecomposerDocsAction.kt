package com.github.timeeidolon.logiccodeplugin.actions

import com.github.timeeidolon.logiccodeplugin.MyBundle
import com.github.timeeidolon.logiccodeplugin.cleanup.GeneratedFilesManifestService
import com.github.timeeidolon.logiccodeplugin.project.LogicCodeActionVisibility
import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.github.timeeidolon.logiccodeplugin.ts.TsArticleFormatting
import com.github.timeeidolon.logiccodeplugin.ts.TsBundledTemplates
import com.github.timeeidolon.logiccodeplugin.ts.TsDecompositionMarkdown
import com.github.timeeidolon.logiccodeplugin.ts.TsPagesPackageScanner
import com.github.timeeidolon.logiccodeplugin.ts.TsReactRouteCollector
import com.github.timeeidolon.logiccodeplugin.ts.TsRouteEntrypoint
import com.github.timeeidolon.logiccodeplugin.ts.TsFeatureCatalogBuilder
import com.github.timeeidolon.logiccodeplugin.ts.TsSkillDimensionScanner
import com.github.timeeidolon.logiccodeplugin.ui.LogicCodeBalloonNotifier
import com.github.timeeidolon.logiccodeplugin.ui.ReportToolWindowRegistry
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import java.nio.file.Files

/**
 * Materializes the **TypeScript project decomposer** skill into `docs/`: two files with **D1–D4** static scans,
 * per-page `detail-*` skeletons, feature index + `feat-*` stubs (Mermaid/TS placeholders per skill D 节).
 */
class GenerateTsProjectDecomposerDocsAction : AnAction(), DumbAware {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Logic Code: TS project decomposition docs",
            true,
        ) {
            override fun run(indicator: ProgressIndicator) {
                val overview = writeOverview(project)
                val details = writeFeatureDetails(project)
                javax.swing.SwingUtilities.invokeLater {
                    openFile(project, overview)
                    openFile(project, details)
                    ReportToolWindowRegistry.activateReportsTabAndSelectTsOverview(project)
                    LogicCodeBalloonNotifier.notifyInfo(
                        project,
                        MyBundle.message("notification.logic.code.title"),
                        MyBundle.message("notification.ts.decomp", overview.absolutePath, details.absolutePath),
                    )
                }
            }
        })
    }

    private fun docsDir(project: Project): java.nio.file.Path {
        val settings = ReportPluginSettings.getInstance().state
        val projectRoot = Path(project.basePath ?: throw IllegalStateException("Project base path is null"))
        return if (!settings.outputDir.isNullOrBlank()) {
            projectRoot.resolve(settings.outputDir)
        } else {
            projectRoot.resolve("docs")
        }.createDirectories()
    }

    private fun writeOverview(project: Project): java.io.File {
        val dir = docsDir(project)
        val scan = TsPagesPackageScanner(project).scan()
        val routes = TsReactRouteCollector(project).collect()
            .sortedWith(compareBy({ it.path }, { it.filePath }))
        val dim = TsSkillDimensionScanner(project).scanAll()
        val catalog = TsFeatureCatalogBuilder.build(scan, dim)

        var body = TsBundledTemplates.loadBundledOrEmpty("ts/stubs/PROJECT_OVERVIEW.stub.md")
        if (body.isBlank()) body = FALLBACK_OVERVIEW

        val text = TsArticleFormatting.tightenVerticalWhitespace(
            body
                .replace("{{AUTOGEN_HOW_TO_RUN}}", TsDecompositionMarkdown.formatHowToRun(dim.packageJson))
                .replace("{{AUTOGEN_SRC_MAP}}", TsDecompositionMarkdown.formatSrcStructureMap(scan, dim))
                .replace("{{AUTOGEN_PACKAGE_JSON}}", TsDecompositionMarkdown.formatPackageJsonBlock(dim.packageJson))
                .replace("{{AUTOGEN_WORKSPACE_MARKERS}}", TsDecompositionMarkdown.formatWorkspaceMarkers(dim.workspaceMarkers))
                .replace("{{AUTOGEN_TSCONFIG_PATHS}}", TsDecompositionMarkdown.formatTsconfigPaths(dim.tsconfigPathsLines))
                .replace("{{AUTOGEN_D4_TABLE}}", TsDecompositionMarkdown.formatD4Table(dim.d4Roots))
                .replace("{{AUTOGEN_RUNTIME_CONFIG}}", TsDecompositionMarkdown.formatRuntimeConfigHints(dim.runtimeConfigHints))
                .replace(
                    "{{AUTOGEN_MERMAID_OVERVIEW}}",
                    TsDecompositionMarkdown.mermaidOverviewSkeleton(
                        scan.pageRows,
                        scan.commonSubdirsRelative.isNotEmpty(),
                        dim.d4Roots.isNotEmpty(),
                    ),
                )
                .replace("{{AUTOGEN_PAGE_TABLE}}", TsDecompositionMarkdown.formatPageTable(scan.pageRows))
                .replace("{{AUTOGEN_ROUTES_TABLE}}", formatRoutesTable(routes))
                .replace("{{AUTOGEN_COMMON_LIST}}", formatCommonList(scan.commonSubdirsRelative))
                .replace("{{AUTOGEN_PAGE_TO_DETAIL_INDEX}}", TsDecompositionMarkdown.pageToDetailIndex(scan.pageRows))
                .replace("{{AUTOGEN_DIMENSION_NAV}}", TsDecompositionMarkdown.dimensionNavLinks())
                .replace("{{AUTOGEN_FEATURE_INDEX}}", TsDecompositionMarkdown.featureIndexTable(catalog))
        )

        val target = dir.resolve("PROJECT_OVERVIEW.md")
        Files.writeString(target, text)
        GeneratedFilesManifestService.getInstance(project).recordGeneratedFile(project, target)
        return target.toFile()
    }

    private fun writeFeatureDetails(project: Project): java.io.File {
        val dir = docsDir(project)
        val scan = TsPagesPackageScanner(project).scan()
        val dim = TsSkillDimensionScanner(project).scanAll()
        val catalog = TsFeatureCatalogBuilder.build(scan, dim)

        var body = TsBundledTemplates.loadBundledOrEmpty("ts/stubs/PROJECT_FEATURE_DETAILS.stub.md")
        if (body.isBlank()) body = FALLBACK_DETAILS

        val text = TsArticleFormatting.tightenVerticalWhitespace(
            body
                .replace("{{AUTOGEN_PAGE_TABLE}}", TsDecompositionMarkdown.formatPageTable(scan.pageRows))
                .replace("{{AUTOGEN_PAGE_DETAIL_SECTIONS}}", TsDecompositionMarkdown.buildPageDetailSections(scan.pageRows))
                .replace("{{AUTOGEN_D3_INDEX}}", TsDecompositionMarkdown.featureIndexTable(catalog))
                .replace(
                    "{{AUTOGEN_FEATURE_DIM_SECTIONS}}",
                    TsDecompositionMarkdown.buildFeatureDimensionSections(catalog, dim, scan.commonSubdirsRelative),
                ),
        )

        val target = dir.resolve("PROJECT_FEATURE_DETAILS.md")
        Files.writeString(target, text)
        GeneratedFilesManifestService.getInstance(project).recordGeneratedFile(project, target)
        return target.toFile()
    }

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

    companion object {
        private fun formatRoutesTable(routes: List<TsRouteEntrypoint>): String {
            if (routes.isEmpty()) {
                return "_No react-router patterns found (see `TsReactRouteCollector`)._\n"
            }
            return buildString {
                appendLine("| Path | Component / kind | File |")
                appendLine("|---|---|---|")
                for (r in routes) {
                    val comp = r.component?.let { "`$it`" } ?: "—"
                    appendLine("| `${r.path}` | $comp (${r.kind}) | `${r.filePath}` |")
                }
            }.trimEnd() + "\n"
        }

        private fun formatCommonList(paths: List<String>): String {
            if (paths.isEmpty()) {
                return "_No `src/common/<subdir>` detected._\n"
            }
            return paths.joinToString("\n") { "- `$it`" } + "\n"
        }

        private val FALLBACK_OVERVIEW = """
## Project overview
{{AUTOGEN_HOW_TO_RUN}}
{{AUTOGEN_SRC_MAP}}
{{AUTOGEN_PACKAGE_JSON}}
{{AUTOGEN_WORKSPACE_MARKERS}}
{{AUTOGEN_TSCONFIG_PATHS}}
{{AUTOGEN_PAGE_TABLE}}
{{AUTOGEN_ROUTES_TABLE}}
{{AUTOGEN_COMMON_LIST}}
{{AUTOGEN_D4_TABLE}}
{{AUTOGEN_RUNTIME_CONFIG}}
{{AUTOGEN_MERMAID_OVERVIEW}}
{{AUTOGEN_DIMENSION_NAV}}
{{AUTOGEN_PAGE_TO_DETAIL_INDEX}}
{{AUTOGEN_FEATURE_INDEX}}
""".trimIndent()

        private val FALLBACK_DETAILS = """
## Page 维度
{{AUTOGEN_PAGE_TABLE}}
{{AUTOGEN_PAGE_DETAIL_SECTIONS}}
## 功能维度
{{AUTOGEN_D3_INDEX}}
{{AUTOGEN_FEATURE_DIM_SECTIONS}}
""".trimIndent()
    }
}
