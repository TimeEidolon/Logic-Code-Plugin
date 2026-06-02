package com.github.timeeidolon.logiccodeplugin.bridge

import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Core module must not hard-depend on Java plugin classes.
 * This bridge uses reflection to call implementations provided by :lang-java when available.
 */
object JavaFeatureBridge {
    private val log = Logger.getInstance(JavaFeatureBridge::class.java)

    data class ResolvedMethodSource(
        val filePath: String,
        val fileStamp: Long,
        val source: String
    )

    data class TableUsageHint(
        val table: String,
        val op: String,
        val evidence: String,
        val confidence: String
    )

    fun tryNavigateFlowNode(project: Project, label: String) {
        // Implemented by :lang-java (uses Java PSI). If Java plugin isn't available, this is a no-op.
        runCatching {
            val cls = Class.forName("com.github.timeeidolon.logiccodeplugin.nav.FlowNodeNavigator")
            val m = cls.getMethod("tryNavigate", Project::class.java, String::class.java)
            m.invoke(null, project, label)
        }.onFailure { t ->
            // Only log at debug level: navigation is best-effort and optional.
            log.debug("Flow navigation not available: ${t.message}", t)
        }
    }

    fun resolveMethodSource(project: Project, id: MethodLogicCacheService.MethodId): ResolvedMethodSource? {
        // Implemented by :lang-java as com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge
        return runCatching {
            val cls = Class.forName("com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge")
            val m = cls.getMethod("resolveMethodSource", Project::class.java, MethodLogicCacheService.MethodId::class.java)
            m.invoke(null, project, id) as? ResolvedMethodSource
        }.getOrElse { t ->
            log.debug("Java PSI bridge not available: ${t.message}", t)
            null
        }
    }

    fun resolveMethodTables(project: Project, id: MethodLogicCacheService.MethodId): List<TableUsageHint> {
        // Implemented by :lang-java as com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge
        return runCatching {
            val cls = Class.forName("com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge")
            val m = cls.getMethod("resolveMethodTables", Project::class.java, MethodLogicCacheService.MethodId::class.java)
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, project, id) as? List<TableUsageHint>).orEmpty()
        }.getOrElse { t ->
            log.debug("Java PSI bridge not available (tables): ${t.message}", t)
            emptyList()
        }
    }

    fun resolveMethodTablesByName(project: Project, classSimpleName: String, methodName: String): List<TableUsageHint> {
        // Implemented by :lang-java as com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge
        return runCatching {
            val cls = Class.forName("com.github.timeeidolon.logiccodeplugin.bridge.JavaPsiBridge")
            val m = cls.getMethod("resolveMethodTablesByName", Project::class.java, String::class.java, String::class.java)
            @Suppress("UNCHECKED_CAST")
            (m.invoke(null, project, classSimpleName, methodName) as? List<TableUsageHint>).orEmpty()
        }.getOrElse { t ->
            log.debug("Java PSI bridge not available (tables by name): ${t.message}", t)
            emptyList()
        }
    }
}

