package com.github.timeeidolon.logiccodeplugin.skill

import com.intellij.openapi.project.Project
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

class SkillTemplateResolver(private val project: Project) {

    /**
     * Prefer project-local override files, then fall back to bundled plugin resources.
     *
     * Project-local override:
     * - `<project>/java/<filename>` (e.g. `JAVA_PROJECT_REPORT_WORKFLOW.md`)
     */
    fun loadTextOrEmpty(filename: String, bundledResourcePath: String): String {
        resolveProjectOverrideFile(filename)?.let { p ->
            try {
                return p.readText(Charsets.UTF_8)
            } catch (_: Exception) {
                // fall through to bundled
            }
        }

        val stream = SkillTemplateResolver::class.java.classLoader.getResourceAsStream(bundledResourcePath)
            ?: return ""
        return stream.bufferedReader().use { it.readText() }
    }

    private fun resolveProjectOverrideFile(filename: String): Path? {
        val base = project.basePath ?: return null
        val p = Path(base).resolve("java").resolve(filename)
        return if (p.exists() && p.isRegularFile()) p else null
    }
}
