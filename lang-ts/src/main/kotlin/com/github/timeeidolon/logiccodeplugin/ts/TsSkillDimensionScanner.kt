package com.github.timeeidolon.logiccodeplugin.ts

import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.vfs.VirtualFile
import java.nio.charset.StandardCharsets

data class TsPackageJsonBrief(
    val relativePath: String,
    val packageName: String?,
    val scriptNames: List<String>,
    /** Lowercase tokens found in dependencies / devDependencies. */
    val stackHints: List<String>,
)

data class TsContractRoot(
    val relativePath: String,
    val kind: String,
)

data class TsKeywordBucket(
    val id: String,
    val label: String,
    val matches: Int,
    val examplePath: String?,
    /** `src/pages/<pkg>` dirs where the keyword matched (D3 back-links). */
    val pagePackageHits: List<String> = emptyList(),
)

data class TsRuntimeConfigHint(
    val symbol: String,
    val examplePath: String,
)

/**
 * Static scans for skill **D2** (workspace / package), **D3** (keyword signals), **D4** (API & contract roots).
 */
class TsSkillDimensionScanner(private val project: Project) {

    companion object {
        private val SKIP_DIRS = setOf(
            ".git", ".idea", ".mvn", "target", "build", "out", "node_modules", ".gradle", "dist",
            "coverage", ".next",
        )
        private val WORKSPACE_FILES = listOf(
            "pnpm-workspace.yaml", "pnpm-workspace.yml", "lerna.json", "turbo.json", "nx.json",
        )
        private val KEYWORD_RULES: List<Triple<String, String, Regex>> = listOf(
            Triple("sse", "Streaming / SSE", Regex("""EventSource|text/event-stream|\bSSE\b""", RegexOption.IGNORE_CASE)),
            Triple("socket", "WebSocket / realtime", Regex("""\bWebSocket\b|socket\.io|SockJS""", RegexOption.IGNORE_CASE)),
            Triple("zustand", "Zustand / lightweight store", Regex("""\bzustand\b|create\s*\(\s*\)\s*=>""")),
            Triple("redux", "Redux / RTK", Regex("""createSlice|@reduxjs|redux-saga|configureStore""")),
            Triple("i18n", "i18n / locales", Regex("""i18n|useTranslation|react-intl|formatjs|lingui""", RegexOption.IGNORE_CASE)),
            Triple("analytics", "Analytics / observability", Regex("""\btrack\b|analytics|gtag|datadog|sentry\.|posthog""", RegexOption.IGNORE_CASE)),
            Triple("router", "Router (declared)", Regex("""react-router|createBrowserRouter|RouterProvider|vue-router""", RegexOption.IGNORE_CASE)),
        )
        private const val MAX_KEYWORD_FILES = 350
        private const val READ_HEAD = 14_000
        private const val D4_MAX_HITS = 45
    }

    fun scanAll(): TsSkillScanBundle {
        val base = project.basePath ?: return TsSkillScanBundle(null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val pkg = readNearestPackageJson(base)
        val markers = WORKSPACE_FILES.mapNotNull { name ->
            val f = java.io.File(base, name)
            if (f.isFile) name else null
        }
        val tsPaths = readTsconfigPathsLines(base)
        val d4 = scanContractRoots(base)
        val buckets = scanKeywordBuckets(base)
        val runtimeHints = scanRuntimeConfigHints(base)
        return TsSkillScanBundle(pkg, markers, tsPaths, d4, buckets, runtimeHints)
    }

    private fun readNearestPackageJson(base: String): TsPackageJsonBrief? {
        val candidates = listOf(java.io.File(base, "package.json")) +
            ModuleManager.getInstance(project).modules.flatMap { m ->
                ModuleRootManager.getInstance(m).contentRoots.mapNotNull { r ->
                    java.io.File(r.path, "package.json").takeIf { it.isFile }
                }
            }.distinctBy { it.absolutePath }

        for (f in candidates.distinctBy { it.absolutePath }) {
            if (!f.isFile) continue
            val rel = TsPathHeuristics.relativizePath(base, f.absolutePath) ?: "package.json"
            val text = runCatching { f.readText(StandardCharsets.UTF_8) }.getOrNull() ?: continue
            return parsePackageJson(rel, text)
        }
        return null
    }

    private fun parsePackageJson(rel: String, raw: String): TsPackageJsonBrief {
        val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(raw)?.groupValues?.getOrNull(1)
        val scripts = mutableListOf<String>()
        Regex(""""scripts"\s*:\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL).find(raw)?.groupValues?.getOrNull(1)?.let { body ->
            Regex(""""([^"]+)"\s*:""").findAll(body).forEach { m -> scripts.add(m.groupValues[1]) }
        }
        val hints = mutableSetOf<String>()
        val depBlock = Regex(""""(?:dependencies|devDependencies|peerDependencies)"\s*:\s*\{([^}]*)\}""", RegexOption.DOT_MATCHES_ALL)
        depBlock.findAll(raw).forEach { m ->
            val inner = m.groupValues[1]
            Regex(""""([^"/@][^"]*)"\s*:""").findAll(inner).forEach { d ->
                val pkg = d.groupValues[1].lowercase()
                when {
                    pkg == "react" || pkg == "react-dom" -> hints.add("react")
                    pkg.startsWith("vue") -> hints.add("vue")
                    pkg.contains("next") -> hints.add("next")
                    pkg.contains("vite") -> hints.add("vite")
                    pkg.contains("webpack") -> hints.add("webpack")
                    pkg.contains("typescript") -> hints.add("typescript")
                    pkg.contains("axios") -> hints.add("axios")
                    pkg.contains("graphql") -> hints.add("graphql")
                }
            }
        }
        return TsPackageJsonBrief(rel, name, scripts.sorted().distinct(), hints.sorted())
    }

    private fun readTsconfigPathsLines(base: String): List<String> {
        val f = java.io.File(base, "tsconfig.json")
        if (!f.isFile) return emptyList()
        val raw = runCatching { f.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return emptyList()
        if (!raw.contains("\"paths\"")) return emptyList()
        val lines = mutableListOf<String>()
        Regex(""""([^"]+)"\s*:\s*\[([^\]]*)\]""").findAll(raw).forEach { m ->
            val key = m.groupValues[1]
            val targets = m.groupValues[2].split(',').mapNotNull { t ->
                Regex(""""([^"]+)"""").find(t.trim())?.groupValues?.getOrNull(1)
            }
            if (targets.isNotEmpty()) lines.add("- `$key` → ${targets.joinToString(", ") { "`$it`" }}")
        }
        return lines.take(24)
    }

    private fun scanContractRoots(base: String): List<TsContractRoot> {
        val out = mutableListOf<TsContractRoot>()
        val seen = mutableSetOf<String>()

        fun record(rel: String, kind: String) {
            val n = rel.replace('\\', '/')
            if (seen.add(n)) out.add(TsContractRoot(n, kind))
        }

        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                walkD4(root, 0, 8, out, base, ::record)
                if (out.size >= D4_MAX_HITS) return out.sortedBy { it.relativePath }
            }
        }
        return out.sortedBy { it.relativePath }
    }

    private fun walkD4(
        dir: VirtualFile,
        depth: Int,
        maxDepth: Int,
        out: MutableList<TsContractRoot>,
        base: String,
        add: (String, String) -> Unit,
    ) {
        if (out.size >= D4_MAX_HITS || depth > maxDepth) return
        if (!dir.isDirectory) {
            considerD4File(dir, base, add)
            return
        }
        if (dir.name in SKIP_DIRS) return
        when (dir.name) {
            ".cloudapi" -> {
                val rel = TsPathHeuristics.relativizePath(base, dir.path) ?: return
                add(rel, ".cloudapi (generated API)")
                return
            }
            "graphql" -> if (dir.parent?.name == "src" || depth <= 3) {
                val rel = TsPathHeuristics.relativizePath(base, dir.path) ?: return
                add(rel, "graphql/")
            }
        }
        for (child in dir.children) {
            if (child.isDirectory) walkD4(child, depth + 1, maxDepth, out, base, add)
            else considerD4File(child, base, add)
        }
    }

    private fun considerD4File(file: VirtualFile, base: String, add: (String, String) -> Unit) {
        val n = file.name.lowercase()
        val kind = when {
            n == "openapi.yaml" || n == "openapi.yml" || n == "openapi.json" -> "OpenAPI spec"
            n == "swagger.json" || n == "swagger.yaml" -> "Swagger"
            (n == "api.ts" || n == "api.js") && file.path.replace('\\', '/').contains("/src/") -> "page/api module"
            else -> return
        }
        val rel = TsPathHeuristics.relativizePath(base, file.path) ?: return
        add(rel, kind)
    }

    private fun scanKeywordBuckets(base: String): List<TsKeywordBucket> {
        data class Acc(var count: Int = 0, var example: String? = null, val pages: MutableSet<String> = mutableSetOf())
        val acc = KEYWORD_RULES.associate { it.first to Acc() }.toMutableMap()
        var scanned = 0
        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                val files = mutableListOf<VirtualFile>()
                collectKeywordFiles(root, 0, 12, files)
                for (vf in files) {
                    if (scanned >= MAX_KEYWORD_FILES) break
                    val rel = TsPathHeuristics.relativizePath(base, vf.path) ?: continue
                    if (!rel.endsWith(".ts", true) && !rel.endsWith(".tsx", true) && !rel.endsWith(".js", true) && !rel.endsWith(".jsx", true)) continue
                    scanned++
                    val bytes = vf.contentsToByteArray()
                    val slice = if (bytes.size > READ_HEAD) bytes.copyOf(READ_HEAD) else bytes
                    val text = runCatching { String(slice, StandardCharsets.UTF_8) }.getOrNull() ?: continue
                    val pagePkg = TsPathHeuristics.extractPagePackage(rel)
                    for ((id, _, rx) in KEYWORD_RULES) {
                        if (rx.containsMatchIn(text)) {
                            val a = acc[id]!!
                            a.count++
                            if (a.example == null) a.example = rel
                            if (pagePkg != null) a.pages.add(pagePkg)
                        }
                    }
                }
            }
        }
        return KEYWORD_RULES.mapNotNull { (id, label, _) ->
            val a = acc[id]!!
            if (a.count > 0) {
                TsKeywordBucket(id, label, a.count, a.example, a.pages.sorted())
            } else {
                null
            }
        }
    }

    private fun scanRuntimeConfigHints(base: String): List<TsRuntimeConfigHint> {
        val seen = mutableSetOf<String>()
        val out = mutableListOf<TsRuntimeConfigHint>()
        val rx = Regex("""window\.(g_[A-Za-z0-9_]+)""")
        var scanned = 0
        for (module in ModuleManager.getInstance(project).modules) {
            for (root in ModuleRootManager.getInstance(module).contentRoots) {
                val files = mutableListOf<VirtualFile>()
                collectKeywordFiles(root, 0, 10, files)
                for (vf in files) {
                    if (scanned >= 120 || out.size >= 24) break
                    val rel = TsPathHeuristics.relativizePath(base, vf.path) ?: continue
                    if (!rel.endsWith(".ts", true) && !rel.endsWith(".tsx", true) && !rel.endsWith(".js", true)) continue
                    scanned++
                    val bytes = vf.contentsToByteArray()
                    val slice = if (bytes.size > READ_HEAD) bytes.copyOf(READ_HEAD) else bytes
                    val text = runCatching { String(slice, StandardCharsets.UTF_8) }.getOrNull() ?: continue
                    for (m in rx.findAll(text)) {
                        val sym = m.groupValues.getOrNull(1) ?: continue
                        if (seen.add(sym)) out.add(TsRuntimeConfigHint(sym, rel))
                    }
                }
            }
        }
        return out.sortedBy { it.symbol }
    }

    private fun collectKeywordFiles(dir: VirtualFile, depth: Int, maxDepth: Int, acc: MutableList<VirtualFile>) {
        if (acc.size >= MAX_KEYWORD_FILES || depth > maxDepth) return
        if (!dir.isDirectory) {
            val n = dir.name
            if (n.endsWith(".ts", true) || n.endsWith(".tsx", true) || n.endsWith(".js", true) || n.endsWith(".jsx", true)) {
                acc.add(dir)
            }
            return
        }
        if (dir.name in SKIP_DIRS) return
        for (c in dir.children) {
            if (acc.size >= MAX_KEYWORD_FILES) return
            collectKeywordFiles(c, depth + 1, maxDepth, acc)
        }
    }

}

data class TsSkillScanBundle(
    val packageJson: TsPackageJsonBrief?,
    val workspaceMarkers: List<String>,
    val tsconfigPathsLines: List<String>,
    val d4Roots: List<TsContractRoot>,
    val keywordBuckets: List<TsKeywordBucket>,
    val runtimeConfigHints: List<TsRuntimeConfigHint> = emptyList(),
)
