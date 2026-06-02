package com.github.timeeidolon.logiccodeplugin.bridge

import com.github.timeeidolon.logiccodeplugin.cache.MethodPsiResolver
import com.github.timeeidolon.logiccodeplugin.db.MethodTableUsageService
import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.intellij.openapi.project.Project
import com.intellij.openapi.application.ReadAction
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope

/**
 * Java PSI-backed helpers. This class lives in :lang-java and is called via reflection from :core.
 */
object JavaPsiBridge {
    @JvmStatic
    fun resolveMethodSource(project: Project, id: MethodLogicCacheService.MethodId): JavaFeatureBridge.ResolvedMethodSource? {
        val method = MethodPsiResolver.resolvePsiMethod(project, id) ?: return null
        val vf = method.containingFile?.virtualFile ?: return null
        val source = method.text ?: return null
        return JavaFeatureBridge.ResolvedMethodSource(
            filePath = vf.path,
            fileStamp = vf.modificationStamp,
            source = source
        )
    }

    @JvmStatic
    fun resolveMethodTables(project: Project, id: MethodLogicCacheService.MethodId): List<JavaFeatureBridge.TableUsageHint> {
        val method = MethodPsiResolver.resolvePsiMethod(project, id) ?: return emptyList()
        val svc = MethodTableUsageService.getInstance(project)
        val usages = svc.getOrCollect(method)
        return usages.map {
            JavaFeatureBridge.TableUsageHint(
                table = it.table,
                op = it.op.name,
                evidence = it.evidence,
                confidence = it.confidence.name
            )
        }
    }

    /**
     * Best-effort resolver for entrypoints referenced only by class simple name + method name (from guide).
     * Prefer exact class simple name match; if multiple overloads exist, pick the first one.
     */
    @JvmStatic
    fun resolveMethodTablesByName(project: Project, classSimpleName: String, methodName: String): List<JavaFeatureBridge.TableUsageHint> {
        val method = ReadAction.compute<com.intellij.psi.PsiMethod?, Throwable> {
            val facade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)
            val classes = facade.findClasses(classSimpleName, scope)
            val clazz = classes.firstOrNull { it.name == classSimpleName } ?: classes.firstOrNull() ?: return@compute null
            clazz.findMethodsByName(methodName, true).firstOrNull()
        } ?: return emptyList()

        val svc = MethodTableUsageService.getInstance(project)
        val usages = svc.getOrCollect(method)
        return usages.map {
            JavaFeatureBridge.TableUsageHint(it.table, it.op.name, it.evidence, it.confidence.name)
        }
    }
}

