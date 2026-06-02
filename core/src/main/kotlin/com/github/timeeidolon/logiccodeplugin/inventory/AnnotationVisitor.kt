package com.github.timeeidolon.logiccodeplugin.inventory

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Lightweight Java annotation scanner using file-based regex matching.
 * Avoids deep PSI APIs for compatibility across IntelliJ versions.
 */
class AnnotationVisitor(private val project: Project) {

    companion object {
        private val ANNOTATION_REGEX = mapOf(
            "startup" to Regex("@SpringBootApplication\\b"),
            "controller" to Regex("@(RestController|Controller)\\b"),
            "service" to Regex("@Service\\b"),
            "repository" to Regex("@(Repository|Mapper)\\b"),
            "config" to Regex("@(Configuration|Bean)\\b"),
            "job" to Regex("@(Scheduled|XxlJob)\\b")
        )

        val MAPPING_ANNOTATIONS = setOf(
            "RequestMapping",
            "GetMapping",
            "PostMapping",
            "PutMapping",
            "DeleteMapping",
            "PatchMapping"
        )

        private val MAPPING_REGEX = Regex(
            """@(${MAPPING_ANNOTATIONS.joinToString("|")})\s*(?:\((.*?)\))?""",
            setOf(RegexOption.DOT_MATCHES_ALL)
        )

        private val CLASS_REGEX = Regex("""\b(?:class|interface|enum)\s+([A-Za-z0-9_]+)""")

        val SKIP_DIRS = setOf(".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle")
    }

    fun scan(): Map<String, List<AnnotationSignal>> {
        val result = mutableMapOf<String, MutableList<AnnotationSignal>>()
        ANNOTATION_REGEX.keys.forEach { result[it] = mutableListOf() }
        val visited = mutableSetOf<String>()

        for (module in ModuleManager.getInstance(project).modules) {
            for (sourceRoot in ModuleRootManager.getInstance(module).sourceRoots) {
                scanDirectory(sourceRoot, result, visited)
            }
        }
        return result
    }

    private fun scanDirectory(
        dir: VirtualFile,
        result: MutableMap<String, MutableList<AnnotationSignal>>,
        visited: MutableSet<String>
    ) {
        for (child in dir.children) {
            if (child.isDirectory && !SKIP_DIRS.contains(child.name)) {
                scanDirectory(child, result, visited)
            } else if (child.name.endsWith(".java") && visited.add(child.path)) {
                scanJavaFile(child, result)
            }
        }
    }

    private fun scanJavaFile(
        vFile: VirtualFile,
        result: MutableMap<String, MutableList<AnnotationSignal>>
    ) {
        val text = try {
            String(vFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            return
        }

        val className = CLASS_REGEX.find(text)?.groupValues?.get(1) ?: vFile.nameWithoutExtension
        val relativePath = project.basePath?.let { base ->
            if (vFile.path.startsWith(base)) vFile.path.removePrefix(base + "/") else vFile.path
        } ?: vFile.path

        for ((category, regex) in ANNOTATION_REGEX) {
            if (regex.containsMatchIn(text)) {
                val detail = if (category == "controller") {
                    extractFirstMapping(text)
                } else if (category == "job") {
                    extractJobDetail(text)
                } else {
                    className
                }
                result[category]?.add(AnnotationSignal(relativePath, className, detail))
            }
        }
    }

    private fun extractFirstMapping(text: String): String {
        val match = MAPPING_REGEX.find(text) ?: return ""
        return match.value.replace("\n", " ").replace(Regex("\\s+"), " ").trim()
    }

    private fun extractJobDetail(text: String): String {
        // Extract both @Scheduled and @XxlJob details
        val xxlJobRegex = Regex("""@XxlJob\s*\(\s*"([^"]+)""")
        val scheduledRegex = Regex("""@Scheduled\s*\(([^)]+)\)""")
        val parts = mutableListOf<String>()
        xxlJobRegex.find(text)?.let { parts.add("@XxlJob(${it.groupValues[1]})") }
        scheduledRegex.find(text)?.let { parts.add("@Scheduled(${it.groupValues[1]})") }
        return parts.joinToString(", ")
    }
}
