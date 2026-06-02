package com.github.timeeidolon.logiccodeplugin.cache

import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope

object MethodPsiResolver {

    fun resolvePsiMethod(project: Project, id: MethodLogicCacheService.MethodId): PsiMethod? {
        return ReadAction.compute<PsiMethod?, Throwable> {
            val psiFacade = JavaPsiFacade.getInstance(project)
            val scope = GlobalSearchScope.projectScope(project)
            val clazz = psiFacade.findClass(id.classFqn, scope) ?: return@compute null
            val candidates = clazz.findMethodsByName(id.methodName, true).toList()
            if (candidates.isEmpty()) return@compute null

            // Prefer exact parameter type texts match.
            val exact = candidates.firstOrNull { m ->
                val params = m.parameterList.parameters.map { it.type.canonicalText }
                params == id.parameterTypeTexts
            }
            exact ?: candidates.firstOrNull()
        }
    }

    fun methodIdOf(method: PsiMethod): MethodLogicCacheService.MethodId? {
        val classFqn = method.containingClass?.qualifiedName ?: return null
        val paramTypes = method.parameterList.parameters.map { it.type.canonicalText }
        return MethodLogicCacheService.MethodId(
            classFqn = classFqn,
            methodName = method.name,
            parameterTypeTexts = paramTypes
        )
    }
}

