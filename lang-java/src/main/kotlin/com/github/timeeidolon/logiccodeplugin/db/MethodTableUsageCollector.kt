package com.github.timeeidolon.logiccodeplugin.db

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiLiteralExpression
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

object MethodTableUsageCollector {
    fun collect(project: Project, method: PsiMethod): List<TableUsage> {
        val out = linkedMapOf<String, TableUsage>() // key=op|table

        fun add(table: String, op: TableUsage.Op, evidence: String, confidence: TableUsage.Confidence) {
            val t = table.trim().trim('`', '"').ifBlank { return }
            val key = "${op.name}|${t.lowercase()}"
            val prev = out[key]
            if (prev == null || confidence.ordinal < prev.confidence.ordinal) {
                out[key] = TableUsage(t, op, evidence, confidence)
            }
        }

        // 1) SQL literals inside method body
        method.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is PsiLiteralExpression) {
                    val v = element.value
                    if (v is String) {
                        val sql = StringUtil.unquoteString(v)
                        SqlTableExtractor.extract(sql).forEach { hit ->
                            add(
                                table = hit.table,
                                op = hit.op,
                                evidence = "SQL literal",
                                confidence = TableUsage.Confidence.LOW
                            )
                        }
                    }
                }
                super.visitElement(element)
            }
        })

        // 2) MyBatis mapper call -> XML mapped SQL
        method.accept(object : PsiRecursiveElementWalkingVisitor() {
            override fun visitElement(element: com.intellij.psi.PsiElement) {
                if (element is PsiMethodCallExpression) {
                    val resolved = element.resolveMethod()
                    if (resolved != null) {
                        val usages = MyBatisXmlTableResolver.resolveTablesForMapperMethod(
                            project = project,
                            mapperMethod = resolved,
                            scope = GlobalSearchScope.projectScope(project)
                        )
                        for (u in usages) add(u.table, u.op, u.evidence, u.confidence)
                    }
                }
                super.visitElement(element)
            }
        })

        // Normalize READ+WRITE into READ_WRITE if both exist
        val tables = out.values.groupBy { it.table.lowercase() }
        val merged = mutableListOf<TableUsage>()
        for ((_, list) in tables) {
            val hasR = list.any { it.op == TableUsage.Op.READ }
            val hasW = list.any { it.op == TableUsage.Op.WRITE }
            if (hasR && hasW) {
                val best = list.minBy { it.confidence.ordinal }
                merged.add(
                    TableUsage(
                        table = best.table,
                        op = TableUsage.Op.READ_WRITE,
                        evidence = best.evidence,
                        confidence = best.confidence
                    )
                )
            } else {
                merged.addAll(list)
            }
        }

        return merged.sortedWith(compareBy({ it.table.lowercase() }, { it.op.name }))
    }
}

