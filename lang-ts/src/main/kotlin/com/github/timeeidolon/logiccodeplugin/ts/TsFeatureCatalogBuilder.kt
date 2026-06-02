package com.github.timeeidolon.logiccodeplugin.ts

/**
 * Builds skill **D3** feature rows with real `#detail-*` back-links (not a single placeholder).
 */
data class TsFeatureCatalogEntry(
    val id: String,
    val label: String,
    val kind: String,
    val relatedDetailAnchors: List<String>,
    val hintPaths: List<String> = emptyList(),
)

object TsFeatureCatalogBuilder {

    fun build(scan: TsPagesScanResult, bundle: TsSkillScanBundle): List<TsFeatureCatalogEntry> {
        val pageToDetail = scan.pageRows.mapIndexed { i, row ->
            row.packageDirRelative to detailAnchor(i)
        }.toMap()

        val out = mutableListOf<TsFeatureCatalogEntry>()
        val seenIds = mutableSetOf<String>()

        fun add(entry: TsFeatureCatalogEntry) {
            if (seenIds.add(entry.id)) out.add(entry)
        }

        if (scan.commonSubdirsRelative.isNotEmpty()) {
            add(
                TsFeatureCatalogEntry(
                    id = "common",
                    label = "Shared UI / services (`src/common`)",
                    kind = "D2",
                    relatedDetailAnchors = allDetailAnchors(scan.pageRows.size).take(3),
                    hintPaths = scan.commonSubdirsRelative.take(8),
                ),
            )
        }

        if (bundle.d4Roots.isNotEmpty()) {
            val linked = pagesForD4(bundle.d4Roots, scan.pageRows, pageToDetail)
            add(
                TsFeatureCatalogEntry(
                    id = "contracts",
                    label = "External / generated contracts (D4)",
                    kind = "D4",
                    relatedDetailAnchors = linked.ifEmpty { allDetailAnchors(scan.pageRows.size).take(2) },
                    hintPaths = bundle.d4Roots.take(6).map { it.relativePath },
                ),
            )
        }

        for (b in bundle.keywordBuckets) {
            val linked = b.pagePackageHits.mapNotNull { pageToDetail[it] }.distinct().sorted()
            add(
                TsFeatureCatalogEntry(
                    id = b.id,
                    label = "${b.label} (${b.matches} file hits)",
                    kind = "D3-signal",
                    relatedDetailAnchors = linked.ifEmpty { fallbackDetails(scan.pageRows.size) },
                    hintPaths = listOfNotNull(b.examplePath),
                ),
            )
        }

        val moduleToPages = mutableMapOf<String, MutableSet<String>>()
        for (row in scan.pageRows) {
            for (mod in row.moduleSubdirNames) {
                moduleToPages.getOrPut(mod) { mutableSetOf() }.add(row.packageDirRelative)
            }
        }
        moduleToPages.entries.sortedBy { it.key.lowercase() }.forEach { (mod, pkgs) ->
            val slug = slugify(mod)
            if (slug.isBlank()) return@forEach
            val anchors = pkgs.mapNotNull { pageToDetail[it] }.distinct().sorted()
            add(
                TsFeatureCatalogEntry(
                    id = "mod-$slug",
                    label = "Page module `$mod` (D3)",
                    kind = "D3-module",
                    relatedDetailAnchors = anchors.ifEmpty { fallbackDetails(scan.pageRows.size) },
                    hintPaths = pkgs.map { "$it/modules/$mod" }.take(6),
                ),
            )
        }

        return out
    }

    private fun pagesForD4(
        roots: List<TsContractRoot>,
        pageRows: List<TsPagePackageRow>,
        pageToDetail: Map<String, String>,
    ): List<String> {
        val hits = mutableSetOf<String>()
        for (r in roots) {
            val pkg = TsPathHeuristics.extractPagePackage(r.relativePath)
            if (pkg != null) pageToDetail[pkg]?.let { hits.add(it) }
            pageRows.filter { row -> row.hasPackageApiFile && r.relativePath.startsWith(row.packageDirRelative) }
                .forEach { row -> pageToDetail[row.packageDirRelative]?.let { hits.add(it) } }
        }
        return hits.toList().sorted()
    }

    fun detailAnchor(pageIndex: Int): String = "detail-${(pageIndex + 1).toString().padStart(2, '0')}"

    private fun allDetailAnchors(pageCount: Int): List<String> =
        (0 until pageCount).map { detailAnchor(it) }

    private fun fallbackDetails(pageCount: Int): List<String> =
        if (pageCount > 0) listOf(detailAnchor(0)) else emptyList()

    private fun slugify(name: String): String =
        name.lowercase()
            .replace(Regex("""[^a-z0-9]+"""), "-")
            .trim('-')
            .take(40)
}
