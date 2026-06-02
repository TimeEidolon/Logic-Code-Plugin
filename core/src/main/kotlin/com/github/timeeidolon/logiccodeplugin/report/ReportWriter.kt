package com.github.timeeidolon.logiccodeplugin.report

import com.github.timeeidolon.logiccodeplugin.cleanup.GeneratedFilesManifestService
import com.intellij.openapi.project.Project
import java.nio.file.Files
import java.nio.file.Path
import kotlin.text.RegexOption.MULTILINE

object ReportWriter {

    fun writeReports(project: Project, outputDir: Path, modelOutput: String): Map<String, Path> {
        val result = mutableMapOf<String, Path>()
        val (report, playbook, entrypoint) = splitModelOutput(modelOutput)

        val reportPath = outputDir.resolve("PROJECT_ANALYSIS_REPORT.md")
        val reportContent = report
            ?: buildString {
                appendLine("# Project Analysis Report (Fallback)")
                appendLine()
                appendLine(
                    "生成器未在大模型输出中找到预期的文件分隔符（例如 `${PromptBuilder.REPORT_MARKER}`）。" +
                        "已将完整模型输出作为主报告落盘。"
                )
                appendLine()
                appendLine("---")
                appendLine()
                appendLine(modelOutput.trim())
            }
        writeFile(project, reportPath, reportContent.trim() + "\n")
        result["report"] = reportPath

        playbook?.let {
            val path = outputDir.resolve("PROJECT_EXCEPTION_PLAYBOOK.md")
            writeFile(project, path, it.trim() + "\n")
            result["playbook"] = path
        }
        entrypoint?.let {
            val path = outputDir.resolve("PROJECT_ENTRYPOINT_GUIDE.md")
            writeFile(project, path, it.trim() + "\n")
            result["entrypointGuide"] = path
        }

        // Diagnostics for missing markers / partial outputs
        if (report == null || playbook == null || entrypoint == null) {
            val diagPath = outputDir.resolve("REPORT_GENERATION_DIAGNOSTICS.md")
            val diag = buildString {
                appendLine("# Report Generation Diagnostics")
                appendLine()
                appendLine("## Marker detection")
                appendLine("- report marker found: `${PromptBuilder.REPORT_MARKER}` → ${modelOutput.containsMarker(PromptBuilder.REPORT_MARKER)}")
                appendLine("- playbook marker found: `${PromptBuilder.PLAYBOOK_MARKER}` → ${modelOutput.containsMarker(PromptBuilder.PLAYBOOK_MARKER)}")
                appendLine("- entrypoint marker found: `${PromptBuilder.ENTRYPOINT_GUIDE_MARKER}` → ${modelOutput.containsMarker(PromptBuilder.ENTRYPOINT_GUIDE_MARKER)}")
                appendLine()
                appendLine("## Output summary")
                appendLine("- model output length: ${modelOutput.length}")
                appendLine("- extracted report length: ${report?.length ?: 0}")
                appendLine("- extracted playbook length: ${playbook?.length ?: 0}")
                appendLine("- extracted entrypoint guide length: ${entrypoint?.length ?: 0}")
                appendLine()
                appendLine("## Hint")
                appendLine("请确保大模型输出的分隔符独占一行，且不要放在 ``` 代码块里，不要添加前后空格。")
            }
            writeFile(project, diagPath, diag.trim() + "\n")
            result["diagnostics"] = diagPath
        }
        return result
    }

    fun writeFile(project: Project, path: Path, content: String) {
        Files.createDirectories(path.parent)
        Files.writeString(path, content)
        GeneratedFilesManifestService.getInstance(project).recordGeneratedFile(project, path)
    }

    private fun splitModelOutput(text: String): Triple<String?, String?, String?> {
        var report: String? = null
        var playbook: String? = null
        var entrypoint: String? = null

        val reportSplit = text.splitByMarker(PromptBuilder.REPORT_MARKER)
        val playbookSplit = text.splitByMarker(PromptBuilder.PLAYBOOK_MARKER)
        val entrySplit = text.splitByMarker(PromptBuilder.ENTRYPOINT_GUIDE_MARKER)

        if (reportSplit != null) {
            val afterReport = reportSplit.second
            val playbookInAfterReport = afterReport.splitByMarker(PromptBuilder.PLAYBOOK_MARKER)
            if (playbookInAfterReport != null) {
                report = playbookInAfterReport.first
                val afterPlaybook = playbookInAfterReport.second
                val entryInAfterPlaybook = afterPlaybook.splitByMarker(PromptBuilder.ENTRYPOINT_GUIDE_MARKER)
                if (entryInAfterPlaybook != null) {
                    playbook = entryInAfterPlaybook.first
                    entrypoint = entryInAfterPlaybook.second
                } else {
                    playbook = afterPlaybook
                }
            } else {
                report = afterReport
            }
        } else if (playbookSplit != null) {
            val afterPlaybook = playbookSplit.second
            val entryInAfterPlaybook = afterPlaybook.splitByMarker(PromptBuilder.ENTRYPOINT_GUIDE_MARKER)
            if (entryInAfterPlaybook != null) {
                playbook = entryInAfterPlaybook.first
                entrypoint = entryInAfterPlaybook.second
            } else {
                playbook = afterPlaybook
            }
        } else if (entrySplit != null) {
            entrypoint = entrySplit.second
        }

        return Triple(report?.trim(), playbook?.trim(), entrypoint?.trim())
    }

    private fun String.splitByMarker(marker: String): Pair<String, String>? {
        // Marker must appear as a standalone line; tolerate surrounding whitespace.
        val regex = Regex("""(?m)^\s*${Regex.escape(marker)}\s*$""", MULTILINE)
        val match = regex.find(this) ?: return null
        val start = match.range.first
        val end = match.range.last + 1
        val before = substring(0, start)
        val after = substring(end)
        return before to after
    }

    private fun String.containsMarker(marker: String): Boolean = splitByMarker(marker) != null
}
