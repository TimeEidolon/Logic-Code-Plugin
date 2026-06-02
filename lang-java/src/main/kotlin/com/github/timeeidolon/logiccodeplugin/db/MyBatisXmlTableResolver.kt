package com.github.timeeidolon.logiccodeplugin.db

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

/**
 * MVP MyBatis resolver:
 * - Identify mapper interface method (namespace = interface FQN)
 * - Find matching XML files by scanning `namespace="..."`
 * - Find statement tag by `id="methodName"` and extract its body as SQL
 * - Extract table names from SQL (best-effort)
 */
object MyBatisXmlTableResolver {
    fun resolveTablesForMapperMethod(
        project: Project,
        mapperMethod: PsiMethod,
        scope: GlobalSearchScope
    ): List<TableUsage> {
        val cls = mapperMethod.containingClass ?: return emptyList()
        if (!cls.isInterface) return emptyList()
        val namespace = cls.qualifiedName ?: return emptyList()
        val methodName = mapperMethod.name

        val xmlFiles = FilenameIndex.getAllFilesByExt(project, "xml", scope)
        if (xmlFiles.isEmpty()) return emptyList()

        val out = mutableListOf<TableUsage>()
        for (vf in xmlFiles) {
            val text = runCatching { VfsUtilCore.loadText(vf) }.getOrNull() ?: continue
            if (!text.contains("namespace=\"$namespace\"") && !text.contains("namespace='$namespace'")) continue

            // Match <select|insert|update|delete ... id="methodName" ...> ... </tag>
            val regex = Regex(
                """(?is)<\s*(select|insert|update|delete)\b[^>]*\bid\s*=\s*(['"])${Regex.escape(methodName)}\2[^>]*>([\s\S]*?)</\s*\1\s*>"""
            )
            val m = regex.find(text) ?: continue
            val tag = m.groupValues[1].lowercase()
            val body = m.groupValues[3]

            val op = when (tag) {
                "select" -> TableUsage.Op.READ
                "insert", "update", "delete" -> TableUsage.Op.WRITE
                else -> TableUsage.Op.UNKNOWN
            }

            val sqlBody = body
                .replace(Regex("""(?is)<!\[CDATA\[(.*?)]]>"""), "$1")
                .trim()

            SqlTableExtractor.extract(sqlBody).forEach { hit ->
                out.add(
                    TableUsage(
                        table = hit.table,
                        op = when (op) {
                            TableUsage.Op.READ -> TableUsage.Op.READ
                            TableUsage.Op.WRITE -> TableUsage.Op.WRITE
                            else -> hit.op
                        },
                        evidence = "MyBatis XML: $namespace#$methodName",
                        confidence = TableUsage.Confidence.HIGH
                    )
                )
            }
        }

        return out.distinctBy { it.op to it.table.lowercase() }
    }
}

