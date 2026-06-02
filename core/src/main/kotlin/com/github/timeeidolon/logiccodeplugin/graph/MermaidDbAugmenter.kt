package com.github.timeeidolon.logiccodeplugin.graph

import com.github.timeeidolon.logiccodeplugin.bridge.JavaFeatureBridge

object MermaidDbAugmenter {
    fun augment(mermaidBody: String, methodFqn: String, tables: List<JavaFeatureBridge.TableUsageHint>): String {
        if (tables.isEmpty()) return mermaidBody

        val graph = MermaidGraphParser.parse(mermaidBody)
        val methodNodeId = graph.nodeDefs.entries.firstOrNull { (_, def) -> def.contains(methodFqn) }?.key
            ?: return mermaidBody

        val newNodeDefs = LinkedHashMap(graph.nodeDefs)
        val newEdges = graph.edges.toMutableList()

        // Add classDef once (Mermaid tolerates duplicates, but keep it clean).
        val header = graph.headerLines.toMutableList()
        val classDef = "%% logiccode:dbclass"
        if (header.none { it.contains("logiccode:dbclass") }) {
            header.add(classDef)
            header.add("classDef logiccodeDb fill:#0f2a2e,stroke:#2fb9c3,color:#d6f6f8,stroke-width:1px;")
        }

        fun nodeIdForTable(table: String): String {
            val safe = table.lowercase().replace(Regex("""[^a-z0-9_]+"""), "_").trim('_')
            val base = "DB_${safe.take(32)}"
            var id = base.ifBlank { "DB_t" }
            var i = 1
            while (newNodeDefs.containsKey(id)) {
                id = "${base}_${i++}"
            }
            return id
        }

        for (t in tables) {
            val tid = nodeIdForTable(t.table)
            if (!newNodeDefs.containsKey(tid)) {
                val op = when (t.op) {
                    "READ" -> "R"
                    "WRITE" -> "W"
                    "READ_WRITE" -> "R/W"
                    else -> "?"
                }
                newNodeDefs[tid] = """$tid[ DB $op: ${t.table} ]"""
                // Apply style class to the node
                header.add("class $tid logiccodeDb;")
            }
            if ((methodNodeId to tid) !in newEdges) {
                newEdges.add(methodNodeId to tid)
            }
        }

        return MermaidGraphSlicer.render(
            MermaidGraph(
                headerLines = header,
                nodeDefs = newNodeDefs,
                edges = newEdges
            )
        )
    }
}

