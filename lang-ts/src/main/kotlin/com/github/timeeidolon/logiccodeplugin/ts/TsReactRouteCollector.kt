package com.github.timeeidolon.logiccodeplugin.ts

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile

data class TsRouteEntrypoint(
    val filePath: String,
    val path: String,
    val component: String?,
    val kind: String
)

/**
 * Best-effort collector for React + TypeScript projects using react-router-dom v5-ish patterns.
 * This is intentionally regex-based to avoid hard dependency on JS/TS PSI.
 */
class TsReactRouteCollector(private val project: Project) {

    companion object {
        private val SKIP_DIRS = setOf(".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle", "dist")
        private val ROUTE_TAG = Regex("""<Route\b([^>]+)>?""")
        private val PATH_ATTR = Regex("""\bpath\s*=\s*["']([^"']+)["']""")
        private val COMPONENT_ATTR = Regex("""\bcomponent\s*=\s*\{\s*([A-Za-z0-9_$.]+)\s*}""")
        private val RENDER_ATTR = Regex("""\brender\s*=\s*\{""")

        // route config objects: { path: '/x', component: Foo }
        private val ROUTE_OBJECT = Regex("""\bpath\s*:\s*["']([^"']+)["']\s*,\s*(?:exact\s*:\s*(?:true|false)\s*,\s*)?(?:component|element)\s*:\s*([A-Za-z0-9_$.]+)""")
    }

    fun collect(): List<TsRouteEntrypoint> {
        val result = mutableListOf<TsRouteEntrypoint>()
        val visited = mutableSetOf<String>()

        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).sourceRoots) {
                scanDir(root, result, visited)
            }
        }
        return result
    }

    private fun scanDir(dir: VirtualFile, out: MutableList<TsRouteEntrypoint>, visited: MutableSet<String>) {
        for (child in dir.children) {
            if (child.isDirectory && !SKIP_DIRS.contains(child.name)) {
                scanDir(child, out, visited)
            } else if (!child.isDirectory && visited.add(child.path)) {
                val name = child.name
                if (name.endsWith(".tsx") || name.endsWith(".ts") || name.endsWith(".jsx") || name.endsWith(".js")) {
                    scanFile(child, out)
                }
            }
        }
    }

    private fun scanFile(file: VirtualFile, out: MutableList<TsRouteEntrypoint>) {
        val text = runCatching { String(file.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return
        if (!text.contains("react-router")) return

        val rel = project.basePath?.let { base ->
            if (file.path.startsWith(base)) file.path.removePrefix(base + "/") else file.path
        } ?: file.path

        // JSX <Route ... />
        ROUTE_TAG.findAll(text).forEach { m ->
            val attrs = m.groupValues.getOrNull(1).orEmpty()
            val path = PATH_ATTR.find(attrs)?.groupValues?.getOrNull(1) ?: return@forEach
            val comp = COMPONENT_ATTR.find(attrs)?.groupValues?.getOrNull(1)
            val kind = when {
                comp != null -> "jsx:component"
                RENDER_ATTR.containsMatchIn(attrs) -> "jsx:render"
                else -> "jsx:route"
            }
            out.add(TsRouteEntrypoint(rel, path, comp, kind))
        }

        // route arrays
        ROUTE_OBJECT.findAll(text).forEach { m ->
            val path = m.groupValues.getOrNull(1) ?: return@forEach
            val comp = m.groupValues.getOrNull(2)
            out.add(TsRouteEntrypoint(rel, path, comp, "config"))
        }
    }
}

