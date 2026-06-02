package com.github.timeeidolon.logiccodeplugin.report

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class SourceContextCollector(private val project: Project) {

    companion object {
        val SKIP_DIRS = setOf(".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle")
        val RELEVANT_KEYWORDS = setOf("controller", "service", "config", "job", "mapper", "repository", "model", "dto", "vo", "entity")
        val SENSITIVE_REGEX = Regex(
            "(?i)(token|secret|password|passwd|api[_-]?key|access[_-]?key|private[_-]?key)\\s*[:=]\\s*([^\\s]+)"
        )
    }

    fun collect(maxTotalChars: Int = 180_000): String {
        val sb = StringBuilder("# Source Context\n")
        var total = 0
        val visited = mutableSetOf<String>()
        val candidates = mutableListOf<String>()

        // Collect build files and resource files first
        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                collectFilePaths(root, candidates, visited)
            }
        }

        // Write collected source files
        for (path in candidates) {
            val file = File(path)
            if (!file.exists()) continue
            val raw = file.readText(Charsets.UTF_8)
            val redacted = redactSensitive(raw)
            val section = "\n\n## File: ${path.removePrefix(project.basePath + "/")}\n\n```text\n$redacted\n```\n"
            if (total + section.length > maxTotalChars) {
                sb.append("\n\n[TRUNCATED: source context exceeded maxTotalChars]\n")
                break
            }
            sb.append(section)
            total += section.length
        }

        return sb.toString()
    }

    fun collectForFiles(includeAbsolutePaths: List<String>, maxTotalChars: Int): String {
        val sb = StringBuilder("# Source Context (FAST)\n")
        var total = 0
        val base = project.basePath?.trimEnd('/')?.plus("/") ?: ""

        for (path in includeAbsolutePaths.distinct()) {
            val file = File(path)
            if (!file.exists() || !file.isFile) continue
            val raw = runCatching { file.readText(Charsets.UTF_8) }.getOrNull() ?: continue
            val redacted = redactSensitive(raw)
            val rel = if (base.isNotBlank() && path.startsWith(base)) path.removePrefix(base) else path
            val section = "\n\n## File: $rel\n\n```text\n$redacted\n```\n"
            if (total + section.length > maxTotalChars) {
                sb.append("\n\n[TRUNCATED: source context exceeded maxTotalChars]\n")
                break
            }
            sb.append(section)
            total += section.length
        }

        return sb.toString()
    }

    private fun collectFilePaths(dir: VirtualFile, result: MutableList<String>, visited: MutableSet<String>) {
        for (child in dir.children) {
            if (child.isDirectory && !SKIP_DIRS.contains(child.name)) {
                collectFilePaths(child, result, visited)
            } else if (!child.isDirectory && !visited.contains(child.path)) {
                visited.add(child.path)
                val name = child.name
                val ext = child.extension ?: ""
                when {
                    name == "pom.xml" || name == "build.gradle" || name == "build.gradle.kts" ||
                    name == "settings.gradle" || name == "settings.gradle.kts" -> result.add(child.path)
                    ext in setOf("yml", "yaml", "properties", "xml", "sql", "md") ->
                        result.add(child.path)
                    ext == "java" && isRelevantJava(child) -> result.add(child.path)
                }
            }
        }
    }

    private fun isRelevantJava(file: VirtualFile): Boolean {
        val name = file.nameWithoutExtension.lowercase()
        return RELEVANT_KEYWORDS.any { name.contains(it) }
    }

    private fun redactSensitive(text: String): String =
        SENSITIVE_REGEX.replace(text) { "${it.groupValues[1]}: [REDACTED]" }
}
