package com.github.timeeidolon.logiccodeplugin.ui

import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.github.timeeidolon.logiccodeplugin.llm.LlmClient
import com.github.timeeidolon.logiccodeplugin.llm.LlmConfig
import com.github.timeeidolon.logiccodeplugin.llm.ApiKeyResolver
import com.github.timeeidolon.logiccodeplugin.cleanup.GeneratedFilesManifestService
import com.github.timeeidolon.logiccodeplugin.graph.EntrypointGuideGraphExtractor
import com.github.timeeidolon.logiccodeplugin.graph.MermaidDbAugmenter
import com.github.timeeidolon.logiccodeplugin.nav.EntryGuideMarkdownLinkInjector
import com.github.timeeidolon.logiccodeplugin.nav.TsDecomposerMarkdownLinkInjector
import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.github.timeeidolon.logiccodeplugin.report.GenerateResult
import com.github.timeeidolon.logiccodeplugin.report.ReportGenerationStep
import com.github.timeeidolon.logiccodeplugin.report.ReportGenerator
import com.github.timeeidolon.logiccodeplugin.bridge.JavaFeatureBridge
import com.github.timeeidolon.logiccodeplugin.project.LogicCodeActionVisibility
import com.github.timeeidolon.logiccodeplugin.project.ProjectKindHeuristics
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import java.io.File
import javax.swing.*
import com.intellij.openapi.ui.Messages
import com.intellij.icons.AllIcons
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.Insets
import java.awt.Dimension
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.SwingUtilities

class ReportToolWindow(private val project: Project) {

    private companion object {
        private const val TAB_GENERATE = 0
        private const val TAB_REPORTS = 1
        private const val TAB_ANALYSIS = 2

        private const val FILE_MAIN_REPORT = "PROJECT_ANALYSIS_REPORT.md"
        private const val FILE_ENTRY_GUIDE = "PROJECT_ENTRYPOINT_GUIDE.md"
        private const val FILE_PLAYBOOK = "PROJECT_EXCEPTION_PLAYBOOK.md"
        private const val FILE_FRONTEND_ROUTES = "FRONTEND_ROUTE_GUIDE.md"
        private const val FILE_TS_OVERVIEW = "PROJECT_OVERVIEW.md"
        private const val FILE_TS_DETAILS = "PROJECT_FEATURE_DETAILS.md"
    }

    private data class ReportTabVisibility(val showJava: Boolean, val showTs: Boolean)

    private data class StaleListRow(val id: MethodLogicCacheService.MethodId, val reason: String)

    private val currentStepLabel = JLabel("")
    private val reportButton = JButton("Generate Report (LLM)")
    private val analysisPanel = LogicAnalysisPanel(project)

    /** Logic / Flow JCEF panels must be disposed with the tool window. */
    val analysisPanelDisposable: Disposable get() = analysisPanel

    /** Markdown preview browsers (Reports tab) must be disposed when the tool window closes. */
    private val reportMainPreview = MarkdownPreviewPanel()
    private val reportEntryPreview = MarkdownPreviewPanel()
    private val reportPlaybookPreview = MarkdownPreviewPanel()
    private val reportRoutesPreview = MarkdownPreviewPanel()
    private val reportTsOverviewPreview = MarkdownPreviewPanel()
    private val reportTsDetailsPreview = MarkdownPreviewPanel()

    private val reportFilesTabs =
        JTabbedPane().apply {
            tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
        }

    val reportMarkdownPreviewsDisposable: Disposable =
        Disposable {
            reportMainPreview.dispose()
            reportEntryPreview.dispose()
            reportPlaybookPreview.dispose()
            reportRoutesPreview.dispose()
            reportTsOverviewPreview.dispose()
            reportTsDetailsPreview.dispose()
        }
    private val tabs = JTabbedPane().apply {
        tabLayoutPolicy = JTabbedPane.WRAP_TAB_LAYOUT
    }

    private val reportDirLabel = JLabel(" ").apply { font = font.deriveFont(font.size2D - 1f) }

    private val reportsBrowsePanel: JPanel =
        JPanel(BorderLayout()).apply {
            val toolbar = JPanel(FlowLayout(FlowLayout.LEFT, 8, 4)).apply {
                isOpaque = false
                add(JButton(AllIcons.Actions.Refresh).apply {
                    toolTipText = "Reload generated reports from disk"
                    isFocusable = false
                    addActionListener { reloadGeneratedReportsFromDisk() }
                })
                add(reportDirLabel)
                border = JBUI.Borders.empty(4, 8, 4, 8)
            }
            add(toolbar, BorderLayout.NORTH)
            add(reportFilesTabs, BorderLayout.CENTER)
            border = JBUI.Borders.empty(0)
        }

    private val stepOrder = listOf(
        ReportGenerationStep.PREPARE_OUTPUT_DIR,
        ReportGenerationStep.SCAN_INVENTORY,
        ReportGenerationStep.COLLECT_ENTRYPOINTS,
        ReportGenerationStep.COLLECT_SOURCE_CONTEXT,
        ReportGenerationStep.BUILD_PROMPT,
        ReportGenerationStep.WRITE_INTERMEDIATE,
        ReportGenerationStep.CALL_LLM,
        ReportGenerationStep.WRITE_REPORTS,
        ReportGenerationStep.DONE
    )
    private val stepChecks = stepOrder.associateWith { JCheckBox(it.displayName).apply { isEnabled = false } }

    private val cacheService = MethodLogicCacheService.getInstance(project)
    private val staleLabel = JLabel("")
    private val staleListModel = DefaultListModel<StaleListRow>()
    private val staleList = JList(staleListModel).apply {
        selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): java.awt.Component {
                val c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
                val row = value as? StaleListRow
                text = if (row == null) value?.toString().orEmpty() else "${row.id.displayName()}  —  ${row.reason}"
                return c
            }
        }
    }
    private val refreshAffectedButton = JButton("Refresh affected graphs")
    private val refreshSelectedButton = JButton("Refresh selected")
    private val clearStaleMarksButton = JButton("Clear stale marks")
    private val cleanGeneratedFilesButton = JButton("Clean generated files").apply {
        toolTipText = "Delete only files generated by this plugin (keeps user-modified files)"
    }

    private val stepPanel = JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)

        val actionButtons = listOf(
            reportButton,
            refreshAffectedButton,
            refreshSelectedButton,
            clearStaleMarksButton,
            cleanGeneratedFilesButton
        )
        // Make action buttons compact and consistent (avoid stretching full width).
        val uniformW = actionButtons.maxOf { it.preferredSize.width }.coerceAtLeast(JBUI.scale(200))
        val uniformH = JBUI.scale(30)
        actionButtons.forEach { b ->
            val d = Dimension(uniformW, uniformH)
            b.preferredSize = d
            b.minimumSize = d
            // Prevent BoxLayout from stretching buttons to full row width.
            b.maximumSize = d
            // Reduce internal padding so rows look tighter.
            b.margin = Insets(0, JBUI.scale(10), 0, JBUI.scale(10))
        }

        fun section(title: String, description: String? = null, content: JComponent): JComponent =
            JPanel().apply {
                isOpaque = false
                layout = BoxLayout(this, BoxLayout.Y_AXIS)
                alignmentX = JComponent.LEFT_ALIGNMENT
                border = JBUI.Borders.compound(
                    JBUI.Borders.customLine(JBUI.CurrentTheme.CustomFrameDecorations.separatorForeground(), 1),
                    JBUI.Borders.empty(8, 8)
                )

                add(JLabel(title).apply {
                    alignmentX = JComponent.LEFT_ALIGNMENT
                    font = font.deriveFont(font.style or java.awt.Font.BOLD)
                })
                if (!description.isNullOrBlank()) {
                    add(Box.createVerticalStrut(2))
                    add(JLabel("<html><span style='color:#9aa0a6;'>${description}</span></html>").apply {
                        alignmentX = JComponent.LEFT_ALIGNMENT
                    })
                }
                add(Box.createVerticalStrut(6))
                add(content.apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            }

        fun helpIcon(tooltip: String): JComponent =
            JButton(AllIcons.General.ContextHelp).apply {
                toolTipText = tooltip
                isFocusable = false
                isOpaque = false
                isContentAreaFilled = false
                margin = Insets(0, 0, 0, 0)
                border = JBUI.Borders.empty(0)
                preferredSize = Dimension(JBUI.scale(14), JBUI.scale(14))
                minimumSize = preferredSize
                maximumSize = preferredSize
            }

        fun actionRow(button: JButton, tooltip: String): JComponent =
            JPanel().apply {
                isOpaque = false
                border = JBUI.Borders.empty(0)
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                alignmentX = JComponent.LEFT_ALIGNMENT
                // Ensure the row itself can take full width, otherwise BoxLayout may clamp children.
                maximumSize = Dimension(Int.MAX_VALUE, uniformH)

                add(button)
                add(Box.createHorizontalStrut(0))
                add(helpIcon(tooltip))
                add(Box.createHorizontalGlue())
            }

        val reportSection = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(actionRow(reportButton, "Generate full project reports (inventory + entrypoints + LLM)."))
            add(Box.createVerticalStrut(8))
            add(JLabel("Steps").apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(4))
            stepOrder.filter { it != ReportGenerationStep.DONE }.forEach { step ->
                add(stepChecks.getValue(step).apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            }
            add(Box.createVerticalStrut(6))
            add(currentStepLabel.apply { alignmentX = JComponent.LEFT_ALIGNMENT })
        }
        add(
            section(
                title = "Report generation",
                description = null,
                content = reportSection
            )
        )

        add(Box.createVerticalStrut(10))

        val cacheSection = JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(staleLabel.apply { alignmentX = JComponent.LEFT_ALIGNMENT })
            add(Box.createVerticalStrut(6))

            // Actions (vertical, with help icons like "Clean")
            add(actionRow(refreshAffectedButton, "Re-generate all method graphs currently marked as stale using LLM."))
            add(Box.createVerticalStrut(4))
            add(actionRow(refreshSelectedButton, "Re-generate only the selected stale method graphs using LLM."))
            add(Box.createVerticalStrut(4))
            add(actionRow(clearStaleMarksButton, "Clear stale marks (does not re-generate graphs). Stale cached files are set back to CLEAN."))
            add(Box.createVerticalStrut(6))

            add(JScrollPane(staleList).apply {
                alignmentX = JComponent.LEFT_ALIGNMENT
                preferredSize = java.awt.Dimension(10, 150)
                maximumSize = java.awt.Dimension(Int.MAX_VALUE, 220)
            })
            add(Box.createVerticalStrut(6))
            add(actionRow(cleanGeneratedFilesButton, "Delete ONLY files generated by this plugin under the output directory. Modified files are kept."))
        }
        add(
            section(
                title = "Cache & maintenance",
                description = "Stale method graphs are marked automatically after code changes",
                content = cacheSection
            )
        )
    }

    val content: JComponent

    init {
        val mainPanel = JPanel(BorderLayout())
        tabs.addTab("Generate", JScrollPane(stepPanel))
        tabs.addTab("Reports", reportsBrowsePanel)
        tabs.addTab("Analysis", analysisPanel.component)
        mainPanel.add(tabs, BorderLayout.CENTER)
        content = mainPanel

        tabs.addChangeListener {
            if (tabs.selectedIndex == TAB_REPORTS) {
                reloadGeneratedReportsFromDisk()
            }
        }
        reportMainPreview.installLogicCodeNavigation(project)
        reportEntryPreview.installLogicCodeNavigation(project)
        reportPlaybookPreview.installLogicCodeNavigation(project)
        reportRoutesPreview.installLogicCodeNavigation(project)
        reportTsOverviewPreview.installLogicCodeNavigation(project)
        reportTsDetailsPreview.installLogicCodeNavigation(project)
        syncReportFileTabs()
        reloadGeneratedReportsFromDisk()

        // Button actions
        reportButton.addActionListener { generateFullReport() }
        refreshAffectedButton.addActionListener { refreshAffected(all = true) }
        refreshSelectedButton.addActionListener { refreshAffected(all = false) }
        clearStaleMarksButton.addActionListener {
            cacheService.clearStaleMarks(project)
            refreshStaleUi()
        }
        cleanGeneratedFilesButton.addActionListener { cleanGeneratedFiles() }

        refreshStaleUi()
    }

    fun setStatusMessage(text: String) {
        currentStepLabel.text = text
    }

    /**
     * Opens Logic Code tool window → Reports, reloads markdown previews, selects **TS overview**
     * (click-to-source on paths). Call after `PROJECT_OVERVIEW.md` / `PROJECT_FEATURE_DETAILS.md` are written.
     */
    fun activateReportsTabAndSelectTsOverview() {
        SwingUtilities.invokeLater {
            ToolWindowManager.getInstance(project).getToolWindow("LogicCodeReport")?.activate(null)
            tabs.selectedIndex = TAB_REPORTS
            reloadGeneratedReportsFromDisk(selectTsOverviewTab = true)
        }
    }

    /** Called after background cache invalidations so the Generate tab list stays fresh. */
    fun refreshStaleCachesUi() {
        refreshStaleUi()
    }

    private fun cleanGeneratedFiles() {
        val confirm = Messages.showYesNoDialog(
            project,
            "This will delete ONLY files generated by Logic Code plugin under your output directory (default: docs/).\n" +
                "Files that were modified after generation will be kept.\n\n" +
                "Continue?",
            "Logic Code: Clean generated files",
            Messages.getQuestionIcon()
        )
        if (confirm != Messages.YES) return

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: cleaning generated files", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Cleaning generated files..."
                val service = GeneratedFilesManifestService.getInstance(project)
                val r = service.cleanupGeneratedFiles(project)
                javax.swing.SwingUtilities.invokeLater {
                    setStatusMessage(
                        "Cleaned: deleted=${r.deleted}, kept(modified)=${r.skippedModified}, missing=${r.skippedMissing}, errors=${r.errors}"
                    )
                }
            }
        })
    }

    fun showReportGenerateResult(result: GenerateResult) {
        currentStepLabel.text = "Done — ${result.outputDir}"
        reloadGeneratedReportsFromDisk()
        SwingUtilities.invokeLater { tabs.selectedIndex = TAB_REPORTS }
        openGeneratedFile(result.reportPath?.toFile())
        openGeneratedFile(result.entrypointGuidePath?.toFile())
        openGeneratedFile(result.playbookPath?.toFile())
    }

    fun showEntrypointFlow(title: String, mermaidBody: String) {
        tabs.selectedIndex = TAB_ANALYSIS
        analysisPanel.clear()
        analysisPanel.showMermaid(title, mermaidBody)
    }

    fun showEntrypointFromGuide(title: String, className: String, methodName: String, mermaidBody: String) {
        tabs.selectedIndex = TAB_ANALYSIS
        analysisPanel.clear()
        val tables = JavaFeatureBridge.resolveMethodTablesByName(project, className, methodName)
        analysisPanel.showMermaid(title, MermaidDbAugmenter.augment(mermaidBody, "$className.$methodName", tables))

        val section = EntrypointGuideGraphExtractor(project).findEntrypointSectionFor(className, methodName)
        if (section.isNullOrBlank()) {
            analysisPanel.showLogic(
                title,
                "未从 PROJECT_ENTRYPOINT_GUIDE.md 中找到该入口的详细说明段落。\n\n" +
                    "你可以先重新生成报告，或打开 report/PROJECT_ENTRYPOINT_GUIDE.md 搜索该方法。"
            )
            return
        }
        val content = if (tables.isEmpty()) section else {
            buildString {
                appendLine(section.trimEnd())
                appendLine()
                appendLine("## Tables (static scan)")
                tables
                    .groupBy { it.op }
                    .toSortedMap()
                    .forEach { (op, list) ->
                        appendLine("### $op")
                        list.sortedBy { it.table.lowercase() }.forEach { t ->
                            val ev = if (t.evidence.isNotBlank()) " — ${t.evidence}" else ""
                            appendLine("- `${t.table}`$ev")
                        }
                        appendLine()
                    }
            }.trimEnd()
        }
        analysisPanel.showLogic(title, content)
    }

    fun showNonEntrypointLogic(
        title: String,
        methodId: MethodLogicCacheService.MethodId,
        filePath: String,
        fileStamp: Long,
        source: String
    ) {
        tabs.selectedIndex = TAB_ANALYSIS
        analysisPanel.clear()

        analysisPanel.enableRefresh {
            cacheService.invalidate(project, methodId)
            showNonEntrypointLogic(title, methodId, filePath, fileStamp, source)
        }

        val cached = cacheService.get(project, methodId)
        if (cached != null) {
            val isSame = cached.fileModificationStamp == fileStamp && cached.sourceHash == source.hashCode()
            if (cached.status == MethodLogicCacheService.Status.CLEAN && isSame) {
                analysisPanel.showLogic(title, renderSummaryWithTables("[cache hit]\n\n${cached.summary}", methodId))
                cached.mermaid?.let {
                    val methodFqn = "${methodId.classFqn}.${methodId.methodName}"
                    val tables = JavaFeatureBridge.resolveMethodTables(project, methodId)
                    analysisPanel.showMermaid(title, MermaidDbAugmenter.augment(it, methodFqn, tables))
                }
                return
            }
            if (cached.status == MethodLogicCacheService.Status.STALE) {
                analysisPanel.showLogic(
                    title,
                    renderSummaryWithTables(
                        "[stale cache — not auto-refreshed]\nReason: ${cached.staleReason ?: "unknown"}\n\n" +
                            "Use “Refresh affected graphs” / “Refresh selected”, or tap the gutter icon refresh on this method.\n\n${cached.summary}",
                        methodId
                    )
                )
                cached.mermaid?.let {
                    val methodFqn = "${methodId.classFqn}.${methodId.methodName}"
                    val tables = JavaFeatureBridge.resolveMethodTables(project, methodId)
                    analysisPanel.showMermaid(title, MermaidDbAugmenter.augment(it, methodFqn, tables))
                }
                refreshStaleUi()
                return
            }
        }

        analysisPanel.showLoading(title, "Analyzing method logic with LLM...")

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: analyzing method logic", true) {
            override fun run(indicator: ProgressIndicator) {
                val apiKey = ApiKeyResolver.resolve().orEmpty()
                if (apiKey.isBlank()) {
                    javax.swing.SwingUtilities.invokeLater {
                        analysisPanel.showLogic(
                            title,
                            "LLM API key not configured.\n\nTip: Settings → Tools → Logic Code Report, or env LLM_API_KEY / OPENAI_API_KEY"
                        )
                    }
                    return
                }

                val settings = ReportPluginSettings.getInstance().state
                val prompt = buildString {
                    appendLine("你是资深 Java 代码阅读助手。请基于下面方法源码输出两部分内容：逻辑说明 + Mermaid 流程图。")
                    appendLine()
                    appendLine("要求：")
                    appendLine("- 先给 1 句总体概括")
                    appendLine("- 再按执行顺序列出关键步骤（含条件分支）")
                    appendLine("- 标出关键变量/字段的含义与来源")
                    appendLine("- 标出外部调用（DB/HTTP/RPC/消息/缓存）与副作用")
                    appendLine("- 标出异常处理与可能的边界情况")
                    appendLine("- 生成一张 Mermaid `flowchart TD` 流程图，节点用 `Class.method 中文说明` 风格；重要分支用菱形；外部调用/副作用单独节点标注")
                    appendLine("- 逻辑说明中的小标题可适度加一个 Emoji（如 📍 步骤、🗄️ 数据库），便于扫读。")
                    appendLine()
                    appendLine("输出格式必须严格如下（分隔符独占一行，Mermaid 不要包 ```）：")
                    appendLine("---SUMMARY---")
                    appendLine("[这里输出中文逻辑说明]")
                    appendLine("---MERMAID---")
                    appendLine("[这里输出 mermaid flowchart TD 的图内容]")
                    appendLine()
                    appendLine("方法源码：")
                    appendLine("```java")
                    appendLine(source)
                    appendLine("```")
                }

                val llmClient = LlmClient(
                    LlmConfig(
                        baseUrl = settings.llmBaseUrl,
                        model = settings.llmModel,
                        apiKey = apiKey
                    )
                )
                val sb = StringBuilder()
                llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta ->
                    sb.append(delta)
                    indicator.checkCanceled()
                }
                val raw = sb.toString()
                val (summary, mermaid) = parseSummaryAndMermaid(raw)
                cacheService.put(
                    project,
                    methodId,
                    MethodLogicCacheService.Entry(
                        summary = summary,
                        mermaid = mermaid,
                        filePath = filePath,
                        fileModificationStamp = fileStamp,
                        sourceHash = source.hashCode(),
                        status = MethodLogicCacheService.Status.CLEAN
                    )
                )
                javax.swing.SwingUtilities.invokeLater {
                    analysisPanel.showLogic(title, renderSummaryWithTables(summary, methodId))
                    if (!mermaid.isNullOrBlank()) {
                        val methodFqn = "${methodId.classFqn}.${methodId.methodName}"
                        val tables = JavaFeatureBridge.resolveMethodTables(project, methodId)
                        analysisPanel.showMermaid(title, MermaidDbAugmenter.augment(mermaid, methodFqn, tables))
                    }
                    refreshStaleUi()
                }
            }
        })
    }

    private fun renderSummaryWithTables(summary: String, methodId: MethodLogicCacheService.MethodId): String {
        val tables = JavaFeatureBridge.resolveMethodTables(project, methodId)
        if (tables.isEmpty()) return summary

        val grouped = tables.groupBy { it.op }.toSortedMap()
        val sb = StringBuilder()
        sb.appendLine(summary.trimEnd())
        sb.appendLine()
        sb.appendLine("## Tables (static scan)")
        for ((op, list) in grouped) {
            val label = when (op) {
                "READ" -> "READ"
                "WRITE" -> "WRITE"
                "READ_WRITE" -> "READ/WRITE"
                else -> op
            }
            sb.appendLine("### $label")
            list
                .sortedBy { it.table.lowercase() }
                .forEach { t ->
                    val ev = if (t.evidence.isNotBlank()) " — ${t.evidence}" else ""
                    sb.appendLine("- `${t.table}`$ev")
                }
            sb.appendLine()
        }
        return sb.toString().trimEnd()
    }

    private fun refreshStaleUi() {
        val stale = cacheService.listStale()
        staleLabel.text = if (stale.isEmpty()) "No stale caches." else "${stale.size} method(s) marked as stale."
        staleListModel.removeAllElements()
        stale.forEach { (id, info) -> staleListModel.addElement(StaleListRow(id, info.reason)) }
    }

    private fun refreshAffected(all: Boolean) {
        val ids: List<MethodLogicCacheService.MethodId> = if (all) {
            cacheService.listStale().map { it.first }
        } else {
            staleList.selectedValuesList.map { it.id }
        }
        if (ids.isEmpty()) {
            setStatusMessage("No stale method selected.")
            return
        }

        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: refreshing affected method graphs", true) {
            override fun run(indicator: ProgressIndicator) {
                val apiKey = ApiKeyResolver.resolve().orEmpty()
                if (apiKey.isBlank()) {
                    javax.swing.SwingUtilities.invokeLater {
                        setStatusMessage("Missing LLM API key — configure in Settings → Tools → Logic Code Report")
                    }
                    return
                }

                val settings = ReportPluginSettings.getInstance().state
                val llmClient = LlmClient(
                    LlmConfig(
                        baseUrl = settings.llmBaseUrl,
                        model = settings.llmModel,
                        apiKey = apiKey
                    )
                )

                var done = 0
                for (id in ids) {
                    indicator.checkCanceled()
                    indicator.text = "Refreshing: ${id.displayName()}"

                    val resolved = JavaFeatureBridge.resolveMethodSource(project, id)
                    if (resolved == null) {
                        cacheService.markStale(project, id, "Java PSI not available or method cannot be resolved")
                        done++
                        indicator.fraction = done.toDouble() / ids.size.toDouble()
                        continue
                    }

                    val resolvedFilePath = resolved.filePath
                    val resolvedFileStamp = resolved.fileStamp
                    val source = resolved.source

                    val prompt = buildString {
                        appendLine("你是资深 Java 代码阅读助手。请基于下面方法源码输出两部分内容：逻辑说明 + Mermaid 流程图。")
                        appendLine()
                        appendLine("要求：")
                        appendLine("- 先给 1 句总体概括")
                        appendLine("- 再按执行顺序列出关键步骤（含条件分支）")
                        appendLine("- 标出关键变量/字段的含义与来源")
                        appendLine("- 标出外部调用（DB/HTTP/RPC/消息/缓存）与副作用")
                        appendLine("- 标出异常处理与可能的边界情况")
                        appendLine("- 生成一张 Mermaid `flowchart TD` 流程图，节点用 `Class.method 中文说明` 风格；重要分支用菱形；外部调用/副作用单独节点标注")
                        appendLine("- 逻辑说明中的小标题可适度加一个 Emoji（如 📍 步骤、🗄️ 数据库），便于扫读。")
                        appendLine()
                        appendLine("输出格式必须严格如下（分隔符独占一行，Mermaid 不要包 ```）：")
                        appendLine("---SUMMARY---")
                        appendLine("[这里输出中文逻辑说明]")
                        appendLine("---MERMAID---")
                        appendLine("[这里输出 mermaid flowchart TD 的图内容]")
                        appendLine()
                        appendLine("方法源码：")
                        appendLine("```java")
                        appendLine(source)
                        appendLine("```")
                    }

                    val sb = StringBuilder()
                    llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta ->
                        sb.append(delta)
                        indicator.checkCanceled()
                    }
                    val raw = sb.toString()
                    val (summary, mermaid) = parseSummaryAndMermaid(raw)
                    cacheService.put(
                        project,
                        id,
                        MethodLogicCacheService.Entry(
                            summary = summary,
                            mermaid = mermaid,
                            filePath = resolvedFilePath,
                            fileModificationStamp = resolvedFileStamp,
                            sourceHash = source.hashCode(),
                            status = MethodLogicCacheService.Status.CLEAN
                        )
                    )
                    done++
                    indicator.fraction = done.toDouble() / ids.size.toDouble()
                }

                javax.swing.SwingUtilities.invokeLater {
                    refreshStaleUi()
                    setStatusMessage("Refreshed ${ids.size} affected method graph(s).")
                }
            }
        })
    }

    private fun resolveReportDirectory(): Path? {
        val settings = ReportPluginSettings.getInstance().state
        val base = project.basePath ?: return null
        val root = Path.of(base)
        val docsRoot =
            if (!settings.outputDir.isNullOrBlank()) root.resolve(settings.outputDir) else root.resolve("docs")
        return docsRoot.resolve("report")
    }

    private fun markdownPathToRaw(path: Path): String {
        val raw =
            if (Files.exists(path)) runCatching { Files.readString(path, StandardCharsets.UTF_8) }.getOrNull() else null
        return raw ?: buildString {
            appendLine("# File not found")
            appendLine()
            appendLine(
                "Run **Generate Report** first, or check **Settings → Tools → Logic Code Report → Output Directory**."
            )
            appendLine()
            appendLine("Expected:")
            appendLine("`$path`")
        }
    }

    private fun entryGuideMarkdown(path: Path): String {
        val raw = markdownPathToRaw(path)
        if (raw.startsWith("# File not found")) return raw
        return EntryGuideMarkdownLinkInjector.augment(raw)
    }

    private fun routesGuideMarkdown(path: Path): String {
        val raw = markdownPathToRaw(path)
        if (raw.startsWith("# File not found")) return raw
        return TsDecomposerMarkdownLinkInjector.augment(raw)
    }

    private fun tsDecomposerPreviewMarkdown(path: Path): String {
        val raw = markdownPathToRaw(path)
        if (raw.startsWith("# File not found")) return raw
        return TsDecomposerMarkdownLinkInjector.augment(raw)
    }

    /**
     * Shows Java vs TS report tabs based on the **current** project (same heuristics as action visibility).
     * Mixed repos (e.g. plugin + `lang-ts`) default to Java tabs unless TS docs already exist on disk.
     */
    private fun resolveReportTabVisibility(dir: Path?, docsRoot: Path?): ReportTabVisibility {
        val signals = ProjectKindHeuristics.signals(project)
        var showJava = LogicCodeActionVisibility.shouldShowJavaProjectReport(project) && signals.hasJvmMarkers
        var showTs = LogicCodeActionVisibility.shouldShowTsDocumentationActions(project) && signals.hasFrontendMarkers

        if (showJava && showTs) {
            val hasTsDocs =
                docsRoot != null &&
                    (
                        Files.exists(docsRoot.resolve(FILE_TS_OVERVIEW)) ||
                            Files.exists(docsRoot.resolve(FILE_TS_DETAILS))
                        )
            val hasJavaDocs =
                dir != null &&
                    (
                        Files.exists(dir.resolve(FILE_MAIN_REPORT)) ||
                            Files.exists(dir.resolve(FILE_ENTRY_GUIDE)) ||
                            Files.exists(dir.resolve(FILE_PLAYBOOK)) ||
                            Files.exists(dir.resolve(FILE_FRONTEND_ROUTES))
                        )
            when {
                signals.hasJvmMarkers && !signals.hasFrontendMarkers -> showTs = false
                signals.hasFrontendMarkers && !signals.hasJvmMarkers -> showJava = false
                hasTsDocs && !hasJavaDocs -> showJava = false
                hasJavaDocs && !hasTsDocs -> showTs = false
                else -> showTs = false
            }
        }

        if (!showJava && !showTs) {
            showJava = LogicCodeActionVisibility.shouldShowJavaProjectReport(project)
            showTs = LogicCodeActionVisibility.shouldShowTsDocumentationActions(project)
            if (!showJava && !showTs) showJava = true
        }
        return ReportTabVisibility(showJava, showTs)
    }

    private fun syncReportFileTabs(selectTsOverview: Boolean = false) {
        val dir = resolveReportDirectory()
        val docsRoot = dir?.parent
        val visibility = resolveReportTabVisibility(dir, docsRoot)
        val selected = reportFilesTabs.selectedComponent

        reportFilesTabs.removeAll()
        if (visibility.showJava) {
            reportFilesTabs.addTab("Main report", reportMainPreview.component)
            reportFilesTabs.addTab("Entry guide", reportEntryPreview.component)
            reportFilesTabs.addTab("Playbook", reportPlaybookPreview.component)
        }
        if (visibility.showTs) {
            reportFilesTabs.addTab("TS routes", reportRoutesPreview.component)
            reportFilesTabs.addTab("TS overview", reportTsOverviewPreview.component)
            reportFilesTabs.addTab("TS details", reportTsDetailsPreview.component)
        }

        when {
            selectTsOverview && visibility.showTs ->
                selectReportTab(reportTsOverviewPreview.component)
            selected != null && reportFilesTabs.indexOfComponent(selected) >= 0 ->
                reportFilesTabs.selectedComponent = selected
            reportFilesTabs.tabCount > 0 -> reportFilesTabs.selectedIndex = 0
        }
    }

    private fun selectReportTab(component: java.awt.Component) {
        val idx = reportFilesTabs.indexOfComponent(component)
        if (idx >= 0) reportFilesTabs.selectedIndex = idx
    }

    private fun reloadGeneratedReportsFromDisk(selectTsOverviewTab: Boolean = false) {
        val runnable = Runnable {
            val dir = resolveReportDirectory()
            if (dir == null) {
                reportDirLabel.text = "(project base path unavailable)"
                val md = "# Reports\n\nProject base path is unavailable."
                reportMainPreview.setMarkdown(md)
                reportEntryPreview.setMarkdown(md)
                reportPlaybookPreview.setMarkdown(md)
                reportRoutesPreview.setMarkdown(md)
                reportTsOverviewPreview.setMarkdown(md)
                reportTsDetailsPreview.setMarkdown(md)
                syncReportFileTabs()
                return@Runnable
            }
            val docsRoot = dir.parent
            val visibility = resolveReportTabVisibility(dir, docsRoot)
            syncReportFileTabs(selectTsOverview = selectTsOverviewTab)

            reportDirLabel.text =
                when {
                    visibility.showJava && visibility.showTs ->
                        "report/${dir.fileName}  ·  TS: ${docsRoot.fileName}/"
                    visibility.showTs -> "TS docs: ${docsRoot.fileName}/"
                    else -> "report/${dir.fileName}"
                }
            reportDirLabel.toolTipText =
                when {
                    visibility.showJava && visibility.showTs ->
                        "Java reports: ${dir.toAbsolutePath()}\nTS decomposition: ${docsRoot.toAbsolutePath()}"
                    visibility.showTs -> docsRoot.toAbsolutePath().toString()
                    else -> dir.toAbsolutePath().toString()
                }

            if (visibility.showJava) {
                reportMainPreview.setMarkdown(markdownPathToRaw(dir.resolve(FILE_MAIN_REPORT)))
                reportEntryPreview.setMarkdown(entryGuideMarkdown(dir.resolve(FILE_ENTRY_GUIDE)))
                reportPlaybookPreview.setMarkdown(markdownPathToRaw(dir.resolve(FILE_PLAYBOOK)))
            }
            if (visibility.showTs) {
                reportRoutesPreview.setMarkdown(routesGuideMarkdown(dir.resolve(FILE_FRONTEND_ROUTES)))
                reportTsOverviewPreview.setMarkdown(tsDecomposerPreviewMarkdown(docsRoot.resolve(FILE_TS_OVERVIEW)))
                reportTsDetailsPreview.setMarkdown(tsDecomposerPreviewMarkdown(docsRoot.resolve(FILE_TS_DETAILS)))
            }
        }
        if (SwingUtilities.isEventDispatchThread()) runnable.run() else SwingUtilities.invokeLater(runnable)
    }

    private fun parseSummaryAndMermaid(text: String): Pair<String, String?> {
        val sMarker = "---SUMMARY---"
        val mMarker = "---MERMAID---"
        val sIdx = text.indexOf(sMarker)
        val mIdx = text.indexOf(mMarker)
        if (sIdx >= 0 && mIdx > sIdx) {
            val summary = text.substring(sIdx + sMarker.length, mIdx).trim()
            val mermaid = text.substring(mIdx + mMarker.length).trim().trim('`').trim()
            return summary.ifBlank { text.trim() } to mermaid.takeIf { it.isNotBlank() }
        }
        // Fallback: treat all as summary
        return text.trim() to null
    }

    fun resetGenerateSteps() {
        stepChecks.values.forEach { it.isSelected = false }
        currentStepLabel.text = ""
    }

    fun onGenerateStep(step: ReportGenerationStep) {
        // Mark all steps before current as done, keep current highlighted in label.
        val idx = stepOrder.indexOf(step).takeIf { it >= 0 } ?: return
        stepOrder.take(idx).forEach { s -> stepChecks[s]?.isSelected = true }
        if (step == ReportGenerationStep.DONE) {
            stepChecks.values.forEach { it.isSelected = true }
            currentStepLabel.text = "Done"
            return
        }
        if (step == ReportGenerationStep.ERROR) {
            currentStepLabel.text = "Error (see /error/LLM_ERROR.md)"
            return
        }
        currentStepLabel.text = "Current: ${step.displayName}"
    }

    private fun generateFullReport() {
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Logic Code: full report (LLM)", true) {
            override fun run(indicator: ProgressIndicator) {
                indicator.text = "Checking API key..."
                val apiKey = ApiKeyResolver.resolve()

                if (apiKey.isNullOrBlank()) {
                    javax.swing.SwingUtilities.invokeLater {
                        ToolWindowManager.getInstance(project).getToolWindow("LogicCodeReport")?.activate(null)
                        currentStepLabel.text =
                            "Missing LLM API key — Settings → Tools → Logic Code Report, or LLM_API_KEY / OPENAI_API_KEY"
                    }
                    return
                }

                indicator.text = "Scanning and generating full report with LLM..."
                indicator.fraction = 0.0

                val generator = ReportGenerator(project)
                javax.swing.SwingUtilities.invokeLater { resetGenerateSteps() }
                val result = generator.generate(
                    callModel = true,
                    apiKey = apiKey,
                    indicator = indicator,
                    onStep = { step ->
                        javax.swing.SwingUtilities.invokeLater { onGenerateStep(step) }
                    }
                )

                indicator.fraction = 1.0
                indicator.text = "Done — report: ${result.reportPath?.fileName ?: "PROJECT_ANALYSIS_REPORT.md"}"

                javax.swing.SwingUtilities.invokeLater {
                    showReportGenerateResult(result)
                }
            }
        })
    }

    private fun openGeneratedFile(file: File?) {
        if (file == null) return
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return
        FileEditorManager.getInstance(project).openFile(vf, true)
    }
}
