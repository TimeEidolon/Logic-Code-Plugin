package com.github.timeeidolon.logiccodeplugin.report

import com.github.timeeidolon.logiccodeplugin.inventory.AnnotationVisitor
import com.github.timeeidolon.logiccodeplugin.inventory.EntrypointAnnotationNames
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

data class EntrypointInfo(
    val filePath: String,
    val className: String,
    val methodName: String,
    val annotations: List<String>,
    val annotationDetails: Map<String, String>,
    val returnType: String?,
    val parameters: List<String>,
    val type: EntrypointType
)

enum class EntrypointType {
    CONTROLLER,
    XXL_JOB,
    SCHEDULED,
    MESSAGE_LISTENER,
    APPLICATION_EVENT,
    GRPC,
    JAX_RS,
}

/**
 * Lightweight entrypoint scanner using regex on source text.
 */
class EntrypointCollector(private val project: Project) {

    companion object {
        private val SKIP_DIRS = setOf(".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle")

        private val ANNOTATION_REGEX = Regex("@(\\w+(?:\\.\\w+)*)\\s*(?:\\(([^)]*)\\))?")
        private val CLASS_NAME_REGEX = Regex("""\bclass\s+([A-Za-z0-9_]+)""")
    }

    fun collect(): Map<EntrypointType, List<EntrypointInfo>> {
        val result = mutableMapOf<EntrypointType, MutableList<EntrypointInfo>>()
        for (type in EntrypointType.entries) {
            result[type] = mutableListOf()
        }

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
        result: MutableMap<EntrypointType, MutableList<EntrypointInfo>>,
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
        result: MutableMap<EntrypointType, MutableList<EntrypointInfo>>
    ) {
        val text = try {
            String(vFile.contentsToByteArray(), Charsets.UTF_8)
        } catch (e: Exception) {
            return
        }

        val relativePath = project.basePath?.let { base ->
            if (vFile.path.startsWith(base)) vFile.path.removePrefix(base + "/") else vFile.path
        } ?: vFile.path

        if (!text.contains("@")) return

        val interesting =
            text.contains("Mapping") ||
                text.contains("XxlJob") ||
                text.contains("Scheduled") ||
                text.contains("KafkaListener") ||
                text.contains("RabbitListener") ||
                text.contains("JmsListener") ||
                text.contains("PulsarListener") ||
                text.contains("EventListener") ||
                text.contains("TransactionalEventListener") ||
                text.contains("GrpcMethod") ||
                text.contains("RpcMethod") ||
                EntrypointAnnotationNames.JAX_RS_HTTP_VERBS.any { v -> Regex("@${Regex.escape(v)}\\b").containsMatchIn(text) } ||
                text.contains("@Path")

        if (!interesting) return

        val className = CLASS_NAME_REGEX.find(text)?.groupValues?.get(1) ?: vFile.nameWithoutExtension

        val lines = text.lines()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("@")) {
                val annotationLines = mutableListOf<String>()
                var j = i
                while (j < lines.size && (lines[j].trim().startsWith("@") || lines[j].trim().isEmpty())) {
                    val trimmed = lines[j].trim()
                    if (trimmed.isNotEmpty()) annotationLines.add(trimmed)
                    j++
                }
                while (j < lines.size) {
                    val trimLine = lines[j].trim()
                    if (trimLine.isEmpty()) {
                        j++
                        continue
                    }
                    val methodMatch = Regex("""[\w<>\[\],\s]+\s+(\w+)\s*\(""").find(trimLine)
                    if (methodMatch != null) {
                        val methodName = methodMatch.groupValues[1]
                        val returnType =
                            trimLine.substringBefore("(").trim().replace(Regex("\\s+\\w+$"), "").trim()
                        val paramsStr = trimLine.substringAfter("(").substringBefore(")")
                        val params = if (paramsStr.isBlank()) emptyList()
                        else paramsStr.split(",").map { it.trim() }.filter { it.isNotBlank() }

                        val annotations: MutableList<String> = mutableListOf()
                        val details = mutableMapOf<String, String>()

                        for (annLine in annotationLines) {
                            val annMatch = ANNOTATION_REGEX.find(annLine)
                            if (annMatch != null) {
                                val fullQName = annMatch.groupValues[1]
                                val shortName = fullQName.substringAfterLast(".")
                                annotations.add(shortName)
                                if (annMatch.groupValues.size > 2 && annMatch.groupValues[2].isNotEmpty()) {
                                    details[shortName] = annMatch.groupValues[2]
                                }
                            }
                        }

                        fun add(kind: EntrypointType) {
                            result[kind]?.add(
                                EntrypointInfo(
                                    relativePath,
                                    className,
                                    methodName,
                                    annotations.toList(),
                                    details.toMap(),
                                    returnType,
                                    params.toList(),
                                    kind
                                )
                            )
                        }

                        val isController = annotations.any { it in AnnotationVisitor.MAPPING_ANNOTATIONS }
                        val isXxlJob = annotations.any { it in EntrypointAnnotationNames.JOB_ANNOTATIONS }
                        val isScheduled = annotations.any { it in EntrypointAnnotationNames.SCHEDULE_ANNOTATIONS }
                        val isMessage = annotations.any { it in EntrypointAnnotationNames.MESSAGE_ANNOTATIONS }
                        val isEvent = annotations.any { it in EntrypointAnnotationNames.EVENT_ANNOTATIONS }
                        val isGrpc = annotations.any { it in EntrypointAnnotationNames.GRPC_METHOD_ANNOTATIONS }
                        val isJaxRs =
                            annotations.contains("Path") && annotations.any { it in EntrypointAnnotationNames.JAX_RS_HTTP_VERBS }

                        if (isController) add(EntrypointType.CONTROLLER)
                        if (isXxlJob) add(EntrypointType.XXL_JOB)
                        if (isScheduled) add(EntrypointType.SCHEDULED)
                        if (isMessage) add(EntrypointType.MESSAGE_LISTENER)
                        if (isEvent) add(EntrypointType.APPLICATION_EVENT)
                        if (isGrpc) add(EntrypointType.GRPC)
                        if (isJaxRs && !isController) add(EntrypointType.JAX_RS)

                        i = j + 1
                        break
                    }
                    j++
                }
                i = j
            } else {
                i++
            }
        }
    }
}
