package com.github.timeeidolon.logiccodeplugin.ts

import com.intellij.openapi.project.Project
import java.nio.file.Path as NioPath
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * Loads TypeScript skill / stub markdown bundled in [lang-ts], with optional project overrides
 * under `<projectRoot>/typescript/` (same filenames as in the jar: e.g. [TS_PROJECT_DECOMPOSER_SKILL.md]).
 */
object TsBundledTemplates {

    private fun classLoader(): ClassLoader = TsBundledTemplates::class.java.classLoader

    fun loadBundled(path: String): String? =
        classLoader().getResourceAsStream(path)?.bufferedReader()?.use { it.readText() }

    fun loadBundledOrEmpty(path: String): String = loadBundled(path).orEmpty()

    fun loadWithProjectOverride(project: Project, bundledPath: String, overrideFileName: String): String {
        val override = resolveProjectOverride(project, overrideFileName)
        if (override != null) {
            runCatching { return override.readText(Charsets.UTF_8) }
        }
        return loadBundledOrEmpty(bundledPath)
    }

    private fun resolveProjectOverride(project: Project, filename: String): NioPath? {
        val base = project.basePath ?: return null
        val p = Path(base, "typescript", filename)
        return if (p.isRegularFile() && p.exists()) p else null
    }
}
