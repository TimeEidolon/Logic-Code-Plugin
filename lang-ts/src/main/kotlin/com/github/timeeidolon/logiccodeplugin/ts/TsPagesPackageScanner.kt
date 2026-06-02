package com.github.timeeidolon.logiccodeplugin.ts

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

data class TsPagePackageRow(
    val packageDirRelative: String,
    val presentEntries: List<String>,
    /** Subdirectory names under `modules/` when present (D3 hint). */
    val moduleSubdirNames: List<String> = emptyList(),
    val hasStoreDir: Boolean = false,
    val hasPackageApiFile: Boolean = false,
)

data class TsPagesScanResult(
    val pageRows: List<TsPagePackageRow>,
    /** Immediate subdirs under each detected `src/common` (deduped, sorted). */
    val commonSubdirsRelative: List<String>,
)

/**
 * HelpCenter-oriented scan: `src/pages/<pkg>/` entry files (Step A1 in the TS project decomposer skill).
 */
class TsPagesPackageScanner(private val project: Project) {

    companion object {
        private val SKIP_DIRS = setOf(
            ".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle", "dist",
        )
        val ENTRY_FILES = listOf("App.tsx", "entry-server.tsx", "entry.tsx", "entry.ts", "entry.js")
    }

    fun scan(): TsPagesScanResult {
        val base = project.basePath ?: return TsPagesScanResult(emptyList(), emptyList())
        val pageRows = mutableListOf<TsPagePackageRow>()
        val commonSubdirs = mutableSetOf<String>()

        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).sourceRoots) {
                for (pagesRoot in findSrcPagesRoots(root)) {
                    for (pkg in pagesRoot.children.filter { it.isDirectory }) {
                        val present = ENTRY_FILES.filter { pkg.findChild(it) != null }
                        val rel = TsPathHeuristics.relativizePath(base, pkg.path)
                            ?: relativizeToProject(base, pkg.path)
                        val modulesDir = pkg.findChild("modules")
                        val moduleNames = if (modulesDir != null && modulesDir.isDirectory) {
                            modulesDir.children.filter { it.isDirectory }.map { it.name }.sorted()
                        } else {
                            emptyList()
                        }
                        val hasStore = pkg.findChild("store")?.let { it.isDirectory } == true
                        val hasApi = pkg.findChild("api.ts") != null || pkg.findChild("api.js") != null
                        pageRows.add(
                            TsPagePackageRow(
                                packageDirRelative = rel,
                                presentEntries = present,
                                moduleSubdirNames = moduleNames,
                                hasStoreDir = hasStore,
                                hasPackageApiFile = hasApi,
                            ),
                        )
                    }
                }
                for (commonRoot in findSrcCommonRoots(root)) {
                    for (child in commonRoot.children.filter { it.isDirectory }) {
                        val rel = TsPathHeuristics.relativizePath(base, child.path)
                            ?: relativizeToProject(base, child.path)
                        commonSubdirs.add(rel)
                    }
                }
            }
        }

        val dedupedPages = pageRows
            .distinctBy { it.packageDirRelative }
            .sortedBy { it.packageDirRelative }

        return TsPagesScanResult(
            pageRows = dedupedPages,
            commonSubdirsRelative = commonSubdirs.sorted(),
        )
    }

    private fun findSrcPagesRoots(dir: VirtualFile): List<VirtualFile> {
        val out = mutableListOf<VirtualFile>()
        fun walk(current: VirtualFile) {
            if (current.name == "pages" && current.parent?.name == "src") {
                out.add(current)
                return
            }
            for (child in current.children) {
                if (child.isDirectory && child.name !in SKIP_DIRS) walk(child)
            }
        }
        walk(dir)
        return out
    }

    private fun findSrcCommonRoots(dir: VirtualFile): List<VirtualFile> {
        val out = mutableListOf<VirtualFile>()
        fun walk(current: VirtualFile) {
            if (current.name == "common" && current.parent?.name == "src") {
                out.add(current)
                return
            }
            for (child in current.children) {
                if (child.isDirectory && child.name !in SKIP_DIRS) walk(child)
            }
        }
        walk(dir)
        return out
    }

    private fun relativizeToProject(basePath: String, absolutePath: String): String {
        val normBase = basePath.trimEnd('/', '\\').replace('\\', '/')
        val normPath = absolutePath.replace('\\', '/')
        return if (normPath.startsWith(normBase)) {
            normPath.removePrefix(normBase).trimStart('/')
        } else {
            normPath
        }
    }
}
