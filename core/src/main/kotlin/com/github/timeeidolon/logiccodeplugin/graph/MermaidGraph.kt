package com.github.timeeidolon.logiccodeplugin.graph

data class MermaidGraph(
    val headerLines: List<String>,
    val nodeDefs: Map<String, String>,
    val edges: List<Pair<String, String>>
)

object MermaidGraphParser {
    private val EDGE_REGEX = Regex("""^\s*([A-Za-z0-9_]+)\s*-->\s*([A-Za-z0-9_]+)\s*$""")
    private val NODE_DEF_REGEX = Regex("""^\s*([A-Za-z0-9_]+)\s*\[(.+)]\s*$""")

    fun parse(mermaidBody: String): MermaidGraph {
        val lines = mermaidBody.lines()
        val header = mutableListOf<String>()
        val nodeDefs = linkedMapOf<String, String>()
        val edges = mutableListOf<Pair<String, String>>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            if (trimmed.startsWith("flowchart")) {
                header.add(trimmed)
                continue
            }
            if (trimmed.startsWith("%%")) {
                header.add(trimmed)
                continue
            }

            val edge = EDGE_REGEX.matchEntire(trimmed)
            if (edge != null) {
                edges.add(edge.groupValues[1] to edge.groupValues[2])
                continue
            }

            val node = NODE_DEF_REGEX.matchEntire(trimmed)
            if (node != null) {
                nodeDefs[node.groupValues[1]] = trimmed
            }
        }

        return MermaidGraph(
            headerLines = if (header.isNotEmpty()) header else listOf("flowchart TD"),
            nodeDefs = nodeDefs,
            edges = edges
        )
    }
}

object MermaidGraphSlicer {
    fun sliceDownstream(graph: MermaidGraph, startNodeId: String, maxNodes: Int = 40): MermaidGraph {
        if (maxNodes <= 0) return graph.copy(nodeDefs = emptyMap(), edges = emptyList())

        val outgoing = graph.edges.groupBy({ it.first }, { it.second })
        val seen = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(startNodeId)
        seen.add(startNodeId)

        while (queue.isNotEmpty() && seen.size < maxNodes) {
            val cur = queue.removeFirst()
            val nexts = outgoing[cur].orEmpty()
            for (n in nexts) {
                if (seen.size >= maxNodes) break
                if (seen.add(n)) queue.add(n)
            }
        }

        val keptEdges = graph.edges.filter { (a, b) -> a in seen && b in seen }
        val keptNodes = graph.nodeDefs.filterKeys { it in seen }

        return graph.copy(nodeDefs = keptNodes, edges = keptEdges)
    }

    fun render(graph: MermaidGraph): String {
        val sb = StringBuilder()
        for (h in graph.headerLines) sb.appendLine(h)
        for (def in graph.nodeDefs.values) sb.appendLine("    $def")
        for ((a, b) in graph.edges) sb.appendLine("    $a --> $b")
        return sb.toString().trimEnd()
    }
}

