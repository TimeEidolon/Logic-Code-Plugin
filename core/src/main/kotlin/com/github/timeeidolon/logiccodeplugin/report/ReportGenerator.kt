package com.github.timeeidolon.logiccodeplugin.report

import com.github.timeeidolon.logiccodeplugin.inventory.ProjectInventoryScanner
import com.github.timeeidolon.logiccodeplugin.llm.LlmClient
import com.github.timeeidolon.logiccodeplugin.llm.LlmConfig
import com.github.timeeidolon.logiccodeplugin.settings.ReportPluginSettings
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import java.nio.file.Path
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.io.path.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.name

class ReportGenerator(private val project: Project) {

    private val inventoryScanner = ProjectInventoryScanner(project)
    private val entrypointCollector = EntrypointCollector(project)
    private val sourceContextCollector = SourceContextCollector(project)
    private val promptBuilder = PromptBuilder(project)

    fun generate(
        outputDir: String? = null,
        callModel: Boolean = true,
        apiKey: String? = null,
        indicator: ProgressIndicator? = null,
        onStep: ((ReportGenerationStep) -> Unit)? = null
    ): GenerateResult {
        val settings = ReportPluginSettings.getInstance().state
        val projectRoot = Path(project.basePath ?: throw IllegalStateException("Project base path is null"))

        indicator?.isIndeterminate = false

        fun step(text: String, fraction: Double) {
            indicator?.checkCanceled()
            indicator?.text = text
            indicator?.fraction = fraction.coerceIn(0.0, 1.0)
        }

        fun emit(step: ReportGenerationStep) {
            onStep?.invoke(step)
        }

        emit(ReportGenerationStep.PREPARE_OUTPUT_DIR)
        step("Preparing output directory...", 0.02)

        val resolvedOutputDir = if (!outputDir.isNullOrBlank()) {
            Path(outputDir)
        } else if (!settings.outputDir.isNullOrBlank()) {
            projectRoot.resolve(settings.outputDir)
        } else {
            projectRoot.resolve("docs")
        }
        resolvedOutputDir.createDirectories()

        val prevDir = resolvedOutputDir.resolve("prev").createDirectories()
        val reportDir = resolvedOutputDir.resolve("report").createDirectories()
        val errorDir = resolvedOutputDir.resolve("error").createDirectories()

        // Run inventory scan
        emit(ReportGenerationStep.SCAN_INVENTORY)
        step("Scanning project inventory...", 0.08)
        val inventoryResult = inventoryScanner.scan()
        val inventoryText = buildInventoryMarkdown(inventoryResult)

        // Collect entrypoints
        emit(ReportGenerationStep.COLLECT_ENTRYPOINTS)
        step("Collecting entrypoints (controllers, jobs)...", 0.18)
        val entrypoints = entrypointCollector.collect()
        val entrypointsText = buildEntrypointMarkdown(entrypoints)

        // Collect source context
        emit(ReportGenerationStep.COLLECT_SOURCE_CONTEXT)
        step("Collecting source context (may take a while)...", 0.32)
        val sourceContext = if (settings.generationMode == ReportPluginSettings.GenerationMode.FAST) {
            // FAST: only include entrypoint-related files (reduces token size drastically)
            val include = entrypoints
                .values
                .flatten()
                .mapNotNull { it.filePath }
                .distinct()
            step("Collecting source context (FAST: entrypoints only)...", 0.32)
            sourceContextCollector.collectForFiles(include, settings.fastMaxSourceChars.coerceAtLeast(5_000))
        } else {
            sourceContextCollector.collect(settings.maxSourceChars)
        }

        // Build prompt
        emit(ReportGenerationStep.BUILD_PROMPT)
        step("Building report prompt...", 0.48)
        val prompt = promptBuilder.build(project.basePath ?: "", inventoryText, entrypointsText, sourceContext)

        // Write intermediate files
        emit(ReportGenerationStep.WRITE_INTERMEDIATE)
        step("Writing intermediate Markdown files...", 0.58)
        val inventoryPath = prevDir.resolve("PROJECT_INVENTORY.md")
        val sourceContextPath = prevDir.resolve("PROJECT_SOURCE_CONTEXT.md")
        val promptPath = prevDir.resolve("GENERATE_REPORT_PROMPT.md")
        ReportWriter.writeFile(project, inventoryPath, inventoryText)
        ReportWriter.writeFile(project, sourceContextPath, sourceContext)
        ReportWriter.writeFile(project, promptPath, prompt)

        val result = GenerateResult(
            projectRoot = projectRoot,
            outputDir = resolvedOutputDir,
            inventoryPath = inventoryPath,
            sourceContextPath = sourceContextPath,
            promptPath = promptPath
        )

        // Optionally call LLM
        if (callModel) {
            emit(ReportGenerationStep.CALL_LLM)
            // FAST: generate entrypoint guide incrementally (parallel), then split report/playbook into separate calls
            if (settings.generationMode == ReportPluginSettings.GenerationMode.FAST) {
                step("FAST mode: generating entrypoint guide (parallel)...", 0.62)
                val llmConfig = LlmConfig(
                    baseUrl = settings.llmBaseUrl,
                    model = settings.llmModel,
                    apiKey = apiKey ?: ""
                )
                val llmClient = LlmClient(llmConfig)
                indicator?.isIndeterminate = false

                val entryGuidePath = reportDir.resolve("PROJECT_ENTRYPOINT_GUIDE.md")
                val entryGuideSkeleton = buildFastEntrypointGuideSkeleton(entrypoints)
                ReportWriter.writeFile(project, entryGuidePath, entryGuideSkeleton)

                val flatEntrypoints = entrypoints.values.flatten()
                val concurrency = settings.llmConcurrency.coerceIn(1, 12)
                val pool = Executors.newFixedThreadPool(concurrency)
                try {
                    val tasks: List<Future<Pair<EntrypointInfo, String>>> = flatEntrypoints.map { ep ->
                        pool.submit(Callable {
                            val section = generateFastEntrypointSection(llmClient, projectRoot.toString(), ep)
                            ep to section
                        })
                    }

                    val results = LinkedHashMap<EntrypointInfo, String>()
                    var done = 0
                    for (f in tasks) {
                        indicator?.checkCanceled()
                        val (ep, section) = try {
                            f.get()
                        } catch (t: Throwable) {
                            // tolerate single-entry failures
                            val dummy = flatEntrypoints.getOrNull(done)
                            val ep0 = dummy ?: EntrypointInfo(
                                filePath = "(unknown)",
                                className = "(unknown)",
                                methodName = "(unknown)",
                                annotations = emptyList(),
                                annotationDetails = emptyMap(),
                                returnType = null,
                                parameters = emptyList(),
                                type = EntrypointType.CONTROLLER
                            )
                            ep0 to buildString {
                                appendLine("### ${ep0.className}.${ep0.methodName}")
                                appendLine()
                                appendLine("_FAST generation failed: ${t.message}_")
                                appendLine()
                            }
                        }
                        results[ep] = section
                        done++
                        val frac = 0.62 + (done.toDouble() / (flatEntrypoints.size.coerceAtLeast(1))) * 0.18
                        step("FAST: entrypoints generated $done/${flatEntrypoints.size}", frac)
                    }

                    // Assemble in original order (grouped by type order from buildEntrypointMarkdown sections)
                    val assembled = assembleFastEntrypointGuide(entrypoints, results)
                    ReportWriter.writeFile(project, entryGuidePath, assembled.trimEnd() + "\n")

                    // Split report + playbook into separate smaller calls (workflow + template from java/*.md)
                    emit(ReportGenerationStep.WRITE_REPORTS)
                    step("FAST: generating main report...", 0.85)
                    val reportText = generateFastSingleFile(
                        llmClient = llmClient,
                        fileName = "PROJECT_ANALYSIS_REPORT.md",
                        projectPath = projectRoot.toString(),
                        inventory = inventoryText,
                        entrypoints = entrypointsText,
                        workflow = TemplateLoader.loadWorkflow(project),
                        template = TemplateLoader.loadReportTemplate(project),
                        sourceContext = sourceContext
                    )
                    val reportPath = reportDir.resolve("PROJECT_ANALYSIS_REPORT.md")
                    ReportWriter.writeFile(project, reportPath, reportText.trimEnd() + "\n")

                    step("FAST: generating exception playbook...", 0.93)
                    val playbookText = generateFastExceptionPlaybook(
                        llmClient = llmClient,
                        projectPath = projectRoot.toString(),
                        entrypointGuide = assembled
                    )
                    val playbookPath = reportDir.resolve("PROJECT_EXCEPTION_PLAYBOOK.md")
                    ReportWriter.writeFile(project, playbookPath, playbookText.trimEnd() + "\n")

                    emit(ReportGenerationStep.DONE)
                    step("Done (FAST incremental).", 1.0)
                    return result.copy(
                        modelOutputPath = null,
                        reportPath = reportPath,
                        playbookPath = playbookPath,
                        entrypointGuidePath = entryGuidePath
                    )
                } finally {
                    pool.shutdownNow()
                }
            }

            step("Calling LLM (${settings.llmModel}) — this can take several minutes...", 0.62)
            val llmConfig = LlmConfig(
                baseUrl = settings.llmBaseUrl,
                model = settings.llmModel,
                apiKey = apiKey ?: ""
            )
            val llmClient = LlmClient(llmConfig)
            indicator?.isIndeterminate = true
            val modelOutput = try {
                try {
                    val sb = StringBuilder()
                    llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta ->
                        sb.append(delta)
                        // Keep progress responsive during long streams
                        indicator?.checkCanceled()
                    }
                    sb.toString()
                } finally {
                    indicator?.isIndeterminate = false
                }
            } catch (t: Throwable) {
                indicator?.isIndeterminate = false
                indicator?.checkCanceled()
                emit(ReportGenerationStep.ERROR)
                step("LLM failed — writing diagnostics and fallback report...", 0.92)

                val errorPath = errorDir.resolve("LLM_ERROR.md")
                val errorText = buildString {
                    appendLine("# LLM Call Failed")
                    appendLine()
                    appendLine("LLM Base URL: `${settings.llmBaseUrl}`")
                    appendLine("Model: `${settings.llmModel}`")
                    appendLine()
                    appendLine("Error:")
                    appendLine("```")
                    appendLine(t.toString())
                    t.stackTrace.take(50).forEach { appendLine("  at $it") }
                    if (t.stackTrace.size > 50) appendLine("  ... ${t.stackTrace.size - 50} more")
                    appendLine("```")
                    appendLine()
                    appendLine("## Next steps")
                    appendLine("- Check API key, base URL, and network connectivity.")
                    appendLine("- Open `${promptPath.name}` and run it manually with your LLM to verify formatting.")
                    appendLine("- If your provider is OpenAI-compatible but not OpenAI, confirm the endpoint supports `/chat/completions`.")
                }
                ReportWriter.writeFile(project, errorPath, errorText)

                val fallbackReport = buildString {
                    appendLine("# Project Analysis Report (LLM failed)")
                    appendLine()
                    appendLine("本次报告生成在调用大模型阶段失败，因此未能生成完整的三份文档。")
                    appendLine()
                    appendLine("已生成以下中间文件（可用于手工重试）：")
                    inventoryPath.fileName?.let { appendLine("- `${it}`") }
                    sourceContextPath.fileName?.let { appendLine("- `${it}`") }
                    promptPath.fileName?.let { appendLine("- `${it}`") }
                    appendLine("- `${errorPath.fileName}`")
                    appendLine()
                    appendLine("你可以在 Settings → Tools → Logic Code Report 中检查 Base URL/Model，或确认环境变量 `LLM_API_KEY`/`OPENAI_API_KEY`。")
                }
                val reportPath = reportDir.resolve("PROJECT_ANALYSIS_REPORT.md")
                ReportWriter.writeFile(project, reportPath, fallbackReport)
                emit(ReportGenerationStep.DONE)
                step("Done (LLM failed, fallback written).", 1.0)
                return result.copy(
                    reportPath = reportPath,
                    modelOutputPath = null
                )
            }
            indicator?.checkCanceled()
            emit(ReportGenerationStep.WRITE_REPORTS)
            step("Writing model output and splitting reports...", 0.92)
            val modelOutputPath = prevDir.resolve("MODEL_OUTPUT.md")
            ReportWriter.writeFile(project, modelOutputPath, modelOutput)

            val reportPaths = ReportWriter.writeReports(project, reportDir, modelOutput)
            emit(ReportGenerationStep.DONE)
            step("Done.", 1.0)
            return result.copy(
                modelOutputPath = modelOutputPath,
                reportPath = reportPaths["report"],
                playbookPath = reportPaths["playbook"],
                entrypointGuidePath = reportPaths["entrypointGuide"]
            )
        }

        emit(ReportGenerationStep.DONE)
        step("Done (prompt only).", 1.0)
        return result
    }

    private fun buildFastEntrypointGuideSkeleton(entrypoints: Map<EntrypointType, List<EntrypointInfo>>): String {
        val sb = StringBuilder()
        sb.appendLine("# Project Entrypoint Guide (FAST incremental)")
        sb.appendLine()
        sb.appendLine("> Generated in FAST mode: sections are produced per entrypoint and assembled.")
        sb.appendLine()
        for ((type, items) in entrypoints) {
            sb.appendLine("## ${type.name}")
            if (items.isEmpty()) {
                sb.appendLine("- （静态扫描未发现）")
                sb.appendLine()
                continue
            }
            for (ep in items) {
                sb.appendLine("- `${ep.className}.${ep.methodName}` (`${ep.filePath}`)")
            }
            sb.appendLine()
        }
        sb.appendLine("---")
        sb.appendLine()
        sb.appendLine("## Details")
        sb.appendLine()
        sb.appendLine("_Sections will be filled below._")
        sb.appendLine()
        return sb.toString()
    }

    private fun generateFastEntrypointSection(llmClient: LlmClient, projectPath: String, ep: EntrypointInfo): String {
        val fileText = runCatching { java.io.File(ep.filePath).readText(Charsets.UTF_8) }.getOrNull().orEmpty()
        val snippet = takeAround(fileText, ep.methodName, maxChars = 12_000)
        val trigger = ep.annotations.joinToString(", ") { a ->
            ep.annotationDetails[a]?.let { "@$a($it)" } ?: "@$a"
        }

        val prompt = buildString {
            appendLine("你是资深 Java 项目分析助手。请为下面入口方法生成一段入口手册内容。")
            appendLine()
            appendLine("要求：")
            appendLine("- 输出为 Markdown（不要输出多文件分隔符）")
            appendLine("- 必须包含触发元信息（注解/路径/cron/topic 等能从静态信息推断的）")
            appendLine("- 必须给 Mermaid `flowchart TD` 图（不要包 ```），节点风格：`NodeId[ Class.method 中文说明 ]`")
            appendLine("- 不要编造数据库表/外部系统；无法确认写“静态分析无法确认”")
            appendLine("- 小节标题可加一个 Emoji 便于扫读（例如 🚪 触发与元信息、🔀 调用链）；少用，保持专业。")
            appendLine()
            appendLine("入口：`${ep.className}.${ep.methodName}`")
            appendLine("触发：$trigger")
            appendLine("文件：`${ep.filePath}`")
            appendLine()
            appendLine("源码片段（可能截断）：")
            appendLine("```java")
            appendLine(snippet)
            appendLine("```")
        }

        val sb = StringBuilder()
        llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta -> sb.append(delta) }
        val body = sb.toString().trim()
        return buildString {
            appendLine("### ${ep.className}.${ep.methodName}")
            appendLine()
            appendLine(body)
            appendLine()
        }
    }

    private fun assembleFastEntrypointGuide(
        entrypoints: Map<EntrypointType, List<EntrypointInfo>>,
        sections: Map<EntrypointInfo, String>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("# PROJECT_ENTRYPOINT_GUIDE (FAST incremental)")
        sb.appendLine()
        sb.appendLine("Generated per-entrypoint with parallel requests; assembled by entrypoint inventory order.")
        sb.appendLine()
        for ((type, items) in entrypoints) {
            sb.appendLine("## ${type.name}")
            sb.appendLine()
            for (ep in items) {
                sb.append(sections[ep] ?: buildString {
                    appendLine("### ${ep.className}.${ep.methodName}")
                    appendLine()
                    appendLine("_Pending / not generated_")
                    appendLine()
                })
            }
        }
        return sb.toString()
    }

    private fun generateFastSingleFile(
        llmClient: LlmClient,
        fileName: String,
        projectPath: String,
        inventory: String,
        entrypoints: String,
        workflow: String,
        template: String,
        sourceContext: String
    ): String {
        val prompt = buildString {
            appendLine("你是资深 Java 项目分析助手。请生成单一 Markdown 文件：`$fileName`。")
            appendLine("不要输出任何其它文件内容，不要输出分隔符。")
            appendLine("章节标题可适度使用 Emoji 与 `> **提示：**` 引用块，减轻长文疲劳；不要滥用。")
            appendLine()
            appendLine("# Project")
            appendLine("`$projectPath`")
            appendLine()
            appendLine("# Inventory")
            appendLine(inventory)
            appendLine()
            appendLine("# Entrypoint Inventory")
            appendLine(entrypoints)
            appendLine()
            appendLine("# Workflow")
            appendLine(workflow)
            appendLine()
            appendLine("# Report Template")
            appendLine(template)
            appendLine()
            appendLine("# Source Context (FAST, may be partial)")
            appendLine(sourceContext)
        }
        val sb = StringBuilder()
        llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta -> sb.append(delta) }
        return sb.toString()
    }

    private fun generateFastExceptionPlaybook(
        llmClient: LlmClient,
        projectPath: String,
        entrypointGuide: String
    ): String {
        val prompt = buildString {
            appendLine("你是资深 Java 项目异常排查助手。请生成 `PROJECT_EXCEPTION_PLAYBOOK.md`。")
            appendLine("只输出该文件的 Markdown 内容，不要输出分隔符。")
            appendLine("每条异常场景的小节标题可加一个 Emoji（如 🔥 ⚠️ ✅）便于区分；正文保持可执行。")
            appendLine()
            appendLine("# Project")
            appendLine("`$projectPath`")
            appendLine()
            appendLine("# Entrypoint Guide (FAST generated)")
            appendLine(entrypointGuide.take(120_000))
        }
        val sb = StringBuilder()
        llmClient.chatCompletionStream(prompt, timeoutMs = 600_000) { delta -> sb.append(delta) }
        return sb.toString()
    }

    private fun takeAround(text: String, needle: String, maxChars: Int): String {
        if (text.isBlank()) return ""
        if (needle.isBlank()) return text.take(maxChars)
        val idx = text.indexOf(needle)
        if (idx < 0) return text.take(maxChars)
        val half = maxChars / 2
        val start = (idx - half).coerceAtLeast(0)
        val end = (idx + half).coerceAtMost(text.length)
        return text.substring(start, end)
    }

    private fun buildInventoryMarkdown(result: com.github.timeeidolon.logiccodeplugin.inventory.InventoryResult): String {
        val sb = StringBuilder()
        sb.appendLine("# Java Project Inventory: ${result.projectName}")
        sb.appendLine()
        sb.appendLine("Project path: `${result.projectPath}`")
        sb.appendLine()

        // Modules
        sb.appendLine("## Build Files / Modules")
        for (module in result.modules) {
            val desc = buildString {
                append("artifact=${module.artifactId}; packaging=${module.packaging}")
                module.javaVersion?.let { append("; java=$it") }
                if (module.subModules.isNotEmpty()) append("; modules=${module.subModules.joinToString(", ")}")
            }
            sb.appendLine("- `${module.name}`: $desc")
        }
        if (result.modules.isEmpty()) sb.appendLine("- No modules found.")
        sb.appendLine()

        // Annotation signals
        sb.appendLine("## Java Signals")
        val labels = listOf(
            "startup" to "Startup Classes",
            "controller" to "Controllers",
            "service" to "Services",
            "repository" to "Repositories / Mappers",
            "config" to "Configurations",
            "job" to "Jobs / Schedulers"
        )
        for ((key, label) in labels) {
            sb.appendLine("### $label")
            val items = result.signals[key] ?: emptyList()
            if (items.isEmpty()) {
                sb.appendLine("- None found.")
            } else {
                for (signal in items) {
                    val extra = if (signal.detail.isNotBlank() && signal.detail != signal.className) " | ${signal.detail}" else ""
                    sb.appendLine("- `${signal.filePath}`: `${signal.className}`$extra")
                }
            }
            sb.appendLine()
        }

        // Resources
        sb.appendLine("## Resource Files")
        val resources = result.resourceFiles
        if (resources.isEmpty()) {
            sb.appendLine("- None found.")
        } else {
            for (path in resources.take(80)) {
                sb.appendLine("- `$path`")
            }
            if (resources.size > 80) sb.appendLine("- ... ${resources.size - 80} more files")
        }
        sb.appendLine()

        sb.appendLine("## Next Steps")
        sb.appendLine("- Read Controller files and map each endpoint to Service calls.")
        sb.appendLine("- Trace core Service methods to Mapper/Repository/Client/external systems.")

        return sb.toString()
    }

    private fun buildEntrypointMarkdown(entrypoints: Map<EntrypointType, List<EntrypointInfo>>): String {
        val sb = StringBuilder()
        sb.appendLine("# Entrypoint Inventory (static scan)")
        sb.appendLine()

        fun section(title: String, items: List<EntrypointInfo>) {
            sb.appendLine("## $title")
            if (items.isEmpty()) {
                sb.appendLine("- （静态扫描未发现）")
            } else {
                for (item in items) {
                    val snippet = item.annotations.joinToString(", ") {
                        if (it in item.annotationDetails) "@$it(${item.annotationDetails[it]})" else "@$it"
                    }
                    sb.appendLine("- `${item.filePath}`: `${item.className}.${item.methodName}` — $snippet")
                }
            }
            sb.appendLine()
        }

        section("Controller Mappings", entrypoints[EntrypointType.CONTROLLER] ?: emptyList())
        section("XXL-Job Handlers", entrypoints[EntrypointType.XXL_JOB] ?: emptyList())
        section("Scheduled Jobs", entrypoints[EntrypointType.SCHEDULED] ?: emptyList())
        section(
            "Message Listeners (@KafkaListener / @RabbitListener / @JmsListener / @PulsarListener)",
            entrypoints[EntrypointType.MESSAGE_LISTENER] ?: emptyList()
        )
        section(
            "Application Events (@EventListener / @TransactionalEventListener)",
            entrypoints[EntrypointType.APPLICATION_EVENT] ?: emptyList()
        )
        section(
            "gRPC-style methods (@GrpcMethod / @RpcMethod)",
            entrypoints[EntrypointType.GRPC] ?: emptyList()
        )
        section(
            "JAX-RS (@Path + verb)",
            entrypoints[EntrypointType.JAX_RS] ?: emptyList()
        )

        return sb.toString()
    }
}
