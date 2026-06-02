package com.github.timeeidolon.logiccodeplugin.db

import com.github.timeeidolon.logiccodeplugin.cache.MethodPsiResolver
import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import java.util.concurrent.ConcurrentHashMap

@Service(Service.Level.PROJECT)
class MethodTableUsageService(private val project: Project) {
    private data class Entry(val fileStamp: Long, val usages: List<TableUsage>)

    private val cache = ConcurrentHashMap<MethodLogicCacheService.MethodId, Entry>()

    fun getOrCollect(method: PsiMethod): List<TableUsage> {
        val id = MethodPsiResolver.methodIdOf(method) ?: return emptyList()
        val stamp = method.containingFile?.virtualFile?.modificationStamp ?: 0L
        val prev = cache[id]
        if (prev != null && prev.fileStamp == stamp) return prev.usages

        val usages = MethodTableUsageCollector.collect(project, method)
        cache[id] = Entry(stamp, usages)
        return usages
    }

    companion object {
        fun getInstance(project: Project): MethodTableUsageService =
            project.getService(MethodTableUsageService::class.java)
    }
}

