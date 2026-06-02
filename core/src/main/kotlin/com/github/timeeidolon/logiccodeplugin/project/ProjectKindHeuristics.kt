package com.github.timeeidolon.logiccodeplugin.project

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * Cheap content-root scans (no stub index) to guess whether the project looks JVM-centric or frontend-centric.
 * Used only for **menu visibility** when smart filtering is enabled in settings.
 */
data class ProjectKindSignals(
    val hasJvmMarkers: Boolean,
    val hasFrontendMarkers: Boolean,
)

object ProjectKindHeuristics {

    private val cache = ConcurrentHashMap<Project, CacheEntry>()
    private const val CACHE_TTL_MS = 4_000L

    private data class CacheEntry(val signals: ProjectKindSignals, val ts: Long)

    fun signals(project: Project): ProjectKindSignals {
        if (project.isDisposed) return ProjectKindSignals(hasJvmMarkers = true, hasFrontendMarkers = true)
        val now = System.currentTimeMillis()
        cache[project]?.let { (s, t) ->
            if (now - t < CACHE_TTL_MS) return s
        }
        val computed = ApplicationManager.getApplication().runReadAction<ProjectKindSignals> {
            val roots = contentRoots(project)
            val state = ScanState()
            for (r in roots) {
                scanTree(r, depth = 0, maxDepth = 6, state)
                if (state.jvm && state.frontend) break
            }
            ProjectKindSignals(hasJvmMarkers = state.jvm, hasFrontendMarkers = state.frontend)
        } ?: ProjectKindSignals(true, true)
        cache[project] = CacheEntry(computed, now)
        return computed
    }

    fun invalidate(project: Project) {
        cache.remove(project)
    }

    private fun contentRoots(project: Project): List<VirtualFile> =
        ModuleManager.getInstance(project).modules.flatMap { m ->
            ModuleRootManager.getInstance(m).contentRoots.filterNotNull()
        }.distinctBy { it.path }

    private class ScanState {
        var jvm: Boolean = false
        var frontend: Boolean = false
    }

    private val skipDirs = setOf(
        "node_modules", ".git", ".idea", "dist", "out", "build", ".gradle",
        "target", ".next", "coverage", "__pycache__",
    )

    private fun scanTree(dir: VirtualFile, depth: Int, maxDepth: Int, state: ScanState) {
        if (state.jvm && state.frontend) return
        if (depth > maxDepth) return
        if (!dir.isDirectory) {
            considerFile(dir, state)
            return
        }
        if (dir.name in skipDirs) return
        considerDirName(dir, state)
        val kids = runCatching { dir.children }.getOrNull() ?: return
        for (c in kids) {
            scanTree(c, depth + 1, maxDepth, state)
            if (state.jvm && state.frontend) return
        }
    }

    private fun considerDirName(dir: VirtualFile, state: ScanState) {
        if (dir.name == "pages" && dir.parent?.name == "src") state.frontend = true
    }

    private fun considerFile(file: VirtualFile, state: ScanState) {
        val n = file.name
        when {
            n.endsWith(".java", ignoreCase = true) -> state.jvm = true
            n.endsWith(".kt", ignoreCase = true) || n.endsWith(".kts", ignoreCase = true) -> state.jvm = true
            n == "pom.xml" -> state.jvm = true
            n == "build.gradle" || n == "build.gradle.kts" -> state.jvm = true
            n.endsWith(".tsx", ignoreCase = true) || n.endsWith(".jsx", ignoreCase = true) -> state.frontend = true
            n == "package.json" -> {
                if (packageJsonSuggestsFrontend(readUtf8Head(file, 120_000))) state.frontend = true
            }
            n == "tsconfig.json" || n.startsWith("vite.config", ignoreCase = true) ||
                n.startsWith("webpack.config", ignoreCase = true) -> state.frontend = true
        }
    }

    internal fun packageJsonSuggestsFrontend(text: String): Boolean {
        val t = text.lowercase()
        val keys = listOf(
            "\"react\"", "'react'", "\"vue\"", "'vue'", "next", "vite", "webpack", "svelte",
            "\"@angular/", "'@angular/", "nuxt", "solid-js", "remix",
        )
        if (keys.any { t.contains(it) }) return true
        if (t.contains("typescript") && (t.contains("react") || t.contains("vite") || t.contains("webpack"))) return true
        return false
    }

    private fun readUtf8Head(file: VirtualFile, maxBytes: Int): String {
        val bytes = runCatching { file.contentsToByteArray() }.getOrNull() ?: return ""
        val slice = if (bytes.size > maxBytes) bytes.copyOf(maxBytes) else bytes
        return String(slice, StandardCharsets.UTF_8)
    }
}
