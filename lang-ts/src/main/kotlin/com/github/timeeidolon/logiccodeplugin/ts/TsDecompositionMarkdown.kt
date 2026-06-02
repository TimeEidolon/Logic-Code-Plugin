package com.github.timeeidolon.logiccodeplugin.ts

/**
 * Markdown blocks aligned with the TS project-decomposer skill (D1–D4 skeletons, anchors, one overview flowchart).
 */
object TsDecompositionMarkdown {

    private val RUN_SCRIPT_PRIORITY = listOf("dev", "start", "serve", "build", "preview", "test", "lint")

    fun formatHowToRun(brief: TsPackageJsonBrief?): String {
        if (brief == null || brief.scriptNames.isEmpty()) {
            return "_Add scripts in `package.json`; ports and env vars are environment-specific._\n"
        }
        val picked = RUN_SCRIPT_PRIORITY.filter { it in brief.scriptNames } +
            brief.scriptNames.filter { it !in RUN_SCRIPT_PRIORITY }.take(6)
        val cmds = picked.distinct().take(8).joinToString(" · ") { script ->
            val pm = if (brief.relativePath.contains("/")) "pnpm --filter <pkg>" else "pnpm"
            "`$pm run $script`"
        }
        return buildString {
            appendLine("- **package**: `${brief.relativePath}`")
            appendLine("- **Suggested commands** (keys from `scripts`; adjust package manager): $cmds")
            appendLine("- **Ports / env**: _以环境为准_ — document `PORT`, `.env*`, or SSR injection if you find them.")
        }.trimEnd() + "\n"
    }

    fun formatSrcStructureMap(scan: TsPagesScanResult, bundle: TsSkillScanBundle): String {
        return buildString {
            appendLine("| Area | Auto-detected |")
            appendLine("|---|---|")
            appendLine("| D1 `src/pages/*` packages | ${scan.pageRows.size} |")
            appendLine("| D2 `src/common` subdirs | ${scan.commonSubdirsRelative.size} |")
            appendLine("| D4 contract roots | ${bundle.d4Roots.size} |")
            appendLine("| D3 keyword signals | ${bundle.keywordBuckets.size} |")
            appendLine("| D4 runtime `window.g_*` | ${bundle.runtimeConfigHints.size} |")
            if (scan.pageRows.isEmpty() && scan.commonSubdirsRelative.isEmpty()) {
                appendLine()
                appendLine("_Non–HelpCenter layout: group **Page 维度** by domain/module in details and note the rule here._")
            }
        }.trimEnd() + "\n"
    }

    fun formatRuntimeConfigHints(hints: List<TsRuntimeConfigHint>): String {
        if (hints.isEmpty()) {
            return "_No `window.g_*` globals detected in scanned TS/JS sources (skill D4 SSR/CSR injection)._\n"
        }
        return buildString {
            appendLine("| Symbol | Example file |")
            appendLine("|---|---|")
            for (h in hints.take(20)) {
                appendLine("| `window.${h.symbol}` | `${h.examplePath}` |")
            }
        }.trimEnd() + "\n"
    }

    fun formatPackageJsonBlock(brief: TsPackageJsonBrief?): String {
        if (brief == null) return "_No `package.json` found at project root (or content roots)._\n"
        return buildString {
            appendLine("- **File**: `${brief.relativePath}`")
            brief.packageName?.let { appendLine("- **name**: `$it`") }
            if (brief.stackHints.isNotEmpty()) {
                appendLine("- **Stack hints (deps)**: ${brief.stackHints.joinToString(", ") { "`$it`" }}")
            }
            if (brief.scriptNames.isNotEmpty()) {
                appendLine("- **Scripts** (keys only): ${brief.scriptNames.take(18).joinToString(", ") { "`$it`" }}${if (brief.scriptNames.size > 18) ", …" else ""}")
            }
        }.trimEnd() + "\n"
    }

    fun formatWorkspaceMarkers(markers: List<String>): String {
        if (markers.isEmpty()) return "_No `pnpm-workspace` / `turbo` / `nx` marker files at repository root._\n"
        return markers.joinToString("\n") { "- `$it`" } + "\n"
    }

    fun formatTsconfigPaths(lines: List<String>): String {
        if (lines.isEmpty()) return "_No `paths` aliases parsed from root `tsconfig.json` (file missing or no paths block)._\n"
        return lines.joinToString("\n") { it } + "\n"
    }

    fun formatD4Table(roots: List<TsContractRoot>): String {
        if (roots.isEmpty()) {
            return "_No OpenAPI/Swagger/`.cloudapi`/`graphql`/`src/**/api.ts` hits under content roots (see skill D4)._\n"
        }
        return buildString {
            appendLine("| Path | Kind |")
            appendLine("|---|---|")
            for (r in roots) {
                appendLine("| `${r.relativePath}` | ${r.kind} |")
            }
        }.trimEnd() + "\n"
    }

    fun formatPageTable(rows: List<TsPagePackageRow>): String {
        if (rows.isEmpty()) {
            return "_No `src/pages/<pkg>` tree detected under module source roots. Skill targets HelpCenter-style layouts._\n"
        }
        return buildString {
            appendLine("| Package directory | Entry files | D3 structure (modules / store / api) |")
            appendLine("|---|---|---|")
            for (r in rows) {
                val files = when {
                    r.presentEntries.isNotEmpty() -> r.presentEntries.joinToString(", ") { entry ->
                        val rel = if (entry.contains("/")) entry else "${r.packageDirRelative}/$entry"
                        "`$rel`"
                    }
                    r.hasStoreDir -> "— _(legacy: `store/` only)_"
                    else -> "—"
                }
                val mod = when {
                    r.moduleSubdirNames.isNotEmpty() ->
                        "`modules/` → ${r.moduleSubdirNames.take(6).joinToString(", ") { "`$it`" }}${if (r.moduleSubdirNames.size > 6) ", …" else ""}"
                    else -> "—"
                }
                val flags = buildList {
                    if (r.hasStoreDir) add("`store/`")
                    if (r.hasPackageApiFile) add("`api.*`")
                }.joinToString(", ").ifBlank { "—" }
                val d3Cell = when {
                    mod != "—" && flags != "—" -> "$mod · $flags"
                    mod != "—" -> mod
                    flags != "—" -> flags
                    else -> "—"
                }
                appendLine("| `${r.packageDirRelative}` | $files | $d3Cell |")
            }
        }.trimEnd() + "\n"
    }

    fun pageToDetailIndex(rows: List<TsPagePackageRow>): String {
        if (rows.isEmpty()) return "| (no pages) | — |\n"
        return buildString {
            appendLine("| Page package | Detail anchor (auto) |")
            appendLine("|---|---|")
            rows.forEachIndexed { i, r ->
                val nn = (i + 1).toString().padStart(2, '0')
                appendLine("| `${r.packageDirRelative}` | [`#detail-$nn`](./PROJECT_FEATURE_DETAILS.md#detail-$nn) |")
            }
        }.trimEnd() + "\n"
    }

    fun featureIndexTable(catalog: List<TsFeatureCatalogEntry>): String {
        if (catalog.isEmpty()) {
            return "| _(no D2/D3/D4 signals — refine manually per skill A2)_ | | | |\n"
        }
        return buildString {
            appendLine("| Capability | Anchor | Kind | Related `detail-*` |")
            appendLine("|---|---|---|---|")
            for (e in catalog) {
                val anchor = "[`#feat-${e.id}`](./PROJECT_FEATURE_DETAILS.md#feat-${e.id})"
                val details = if (e.relatedDetailAnchors.isEmpty()) {
                    "_TBD_"
                } else {
                    e.relatedDetailAnchors.joinToString(", ") { "[`#$it`](./PROJECT_FEATURE_DETAILS.md#$it)" }
                }
                appendLine("| ${e.label} | $anchor | ${e.kind} | $details |")
            }
        }.trimEnd() + "\n"
    }

    fun dimensionNavLinks(): String =
        "- Page 维度: [`#page-dimension-marker`](./PROJECT_FEATURE_DETAILS.md#page-dimension-marker)\n" +
            "- 功能维度: [`#feature-dimension-marker`](./PROJECT_FEATURE_DETAILS.md#feature-dimension-marker)\n"

    fun mermaidOverviewSkeleton(pageRows: List<TsPagePackageRow>, hasCommon: Boolean, hasD4: Boolean): String {
        val pkgs = pageRows.mapIndexed { i, r ->
            val id = "p${i + 1}"
            val label = r.packageDirRelative.substringAfterLast('/').take(28).ifBlank { "pkg${i + 1}" }
            id to label.replace("\"", "'")
        }
        return buildString {
            appendLine("```mermaid")
            appendLine("flowchart TB")
            if (pkgs.isEmpty()) {
                appendLine("  nopages[No src_pages packages detected]")
            } else {
                appendLine("  subgraph pages[Pages]")
                pkgs.forEach { (id, label) -> appendLine("    $id[\"$label\"]") }
                appendLine("  end")
            }
            if (hasCommon) {
                appendLine("  common[\"src/common\"]")
            }
            if (hasD4) {
                appendLine("  apis[\"API / contracts D4\"]")
            }
            if (pkgs.isNotEmpty()) {
                if (hasCommon) pkgs.forEach { (id, _) -> appendLine("  $id --> common") }
                if (hasD4) pkgs.forEach { (id, _) -> appendLine("  $id --> apis") }
            }
            appendLine("```")
            appendLine()
            appendLine("_Auto skeleton: replace node labels and edges after you read real imports. Skill allows **only this one** system-wide flowchart in this file._")
        }.trimEnd() + "\n"
    }

    fun buildPageDetailSections(rows: List<TsPagePackageRow>): String {
        if (rows.isEmpty()) return "_No page packages to expand._\n"
        return buildString {
            appendLine("### Per-package sections (auto skeleton)")
            appendLine()
            rows.forEachIndexed { i, r ->
                val nn = (i + 1).toString().padStart(2, '0')
                appendLine("<a id=\"detail-$nn\"></a>")
                appendLine("### `${r.packageDirRelative}`")
                appendLine()
                appendLine("_索引层：升级为 **已深描** 时补协作图、数据流、下方状态机与 TS 契约（skill Step D）。_")
                appendLine()
                appendLine("- **D1 入口**: ${formatPageEntries(r)}")
                if (r.moduleSubdirNames.isNotEmpty()) {
                    appendLine("- **`modules/`**: ${r.moduleSubdirNames.joinToString(", ") { "`$it`" }}")
                }
                if (r.hasStoreDir) appendLine("- **Local `store/`** present under this package.")
                if (r.hasPackageApiFile) appendLine("- **Local `api.ts` / `api.js`** present (D4 page API surface).")
                appendLine()
                appendLine("```mermaid")
                appendLine("flowchart LR")
                appendLine("  UI[UI shell] --> Data[data / store]")
                appendLine("  Data --> Net[network / api]")
                appendLine("```")
                appendLine()
                appendLine("```mermaid")
                appendLine("stateDiagram-v2")
                appendLine("  [*] --> Boot")
                appendLine("  Boot --> Ready: hydrate / mount")
                appendLine("  Ready --> Loading: user action")
                appendLine("  Loading --> Ready: success")
                appendLine("  Loading --> Error: failure")
                appendLine("  Error --> Ready: retry")
                appendLine("```")
                appendLine()
                appendLine("```ts")
                appendLine("// 简化自 ${r.packageDirRelative}/…，非逐字拷贝")
                appendLine("type PageModel = {")
                appendLine("  // TODO: store slice / props / SSR payload subset")
                appendLine("}")
                appendLine("```")
                appendLine()
            }
        }.trimEnd() + "\n"
    }

    private fun formatPageEntries(r: TsPagePackageRow): String =
        when {
            r.presentEntries.isNotEmpty() -> r.presentEntries.joinToString(", ") { e ->
                if (e.contains("/")) "`$e`" else "`${r.packageDirRelative}/$e`"
            }
            r.hasStoreDir -> "— _(legacy `store/` only; no A1 entry file)_"
            else -> "—"
        }

    fun buildFeatureDimensionSections(
        catalog: List<TsFeatureCatalogEntry>,
        bundle: TsSkillScanBundle,
        commonSubdirs: List<String>,
    ): String {
        if (catalog.isEmpty() && commonSubdirs.isEmpty()) {
            return "_No feature catalog entries; run scans on a HelpCenter-style repo or fill **功能维度** manually._\n"
        }
        return buildString {
            for (e in catalog) {
                appendLine("<a id=\"feat-${e.id}\"></a>")
                appendLine("### ${e.label}")
                appendLine()
                appendLine("- **Kind**: ${e.kind}")
                if (e.relatedDetailAnchors.isNotEmpty()) {
                    val links = e.relatedDetailAnchors.joinToString(", ") { "[`#$it`](#$it)" }
                    appendLine("- **Related pages**: $links")
                }
                if (e.hintPaths.isNotEmpty()) {
                    appendLine("- **Paths**: ${e.hintPaths.joinToString(", ") { "`$it`" }}")
                }
                appendLine()
                appendLine("```mermaid")
                appendLine("flowchart LR")
                appendLine("  Trigger[User / route] --> UI[UI + modules]")
                appendLine("  UI --> State[store / context]")
                appendLine("  State --> IO[API / SSE / WS]")
                appendLine("  IO --> State")
                appendLine("```")
                appendLine()
                appendLine("```mermaid")
                appendLine("stateDiagram-v2")
                appendLine("  [*] --> Idle")
                appendLine("  Idle --> Active: invoke")
                appendLine("  Active --> Idle: settle")
                appendLine("  Active --> Aborted: cancel / unmount")
                appendLine("```")
                appendLine()
                appendLine("```ts")
                appendLine("// 简化自 <path>，非逐字拷贝 — payload / stream chunk / shared props")
                appendLine("type FeatureContract = unknown")
                appendLine("```")
                appendLine()
            }
            if (bundle.runtimeConfigHints.isNotEmpty() && catalog.none { it.id == "contracts" }) {
                appendLine("#### Runtime injection (`window.g_*`)")
                appendLine()
                appendLine(formatRuntimeConfigHints(bundle.runtimeConfigHints))
            }
        }.trimEnd() + "\n"
    }
}
