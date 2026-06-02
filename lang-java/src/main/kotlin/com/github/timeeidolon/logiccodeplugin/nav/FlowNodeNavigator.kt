package com.github.timeeidolon.logiccodeplugin.nav

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.PsiShortNamesCache

/**
 * Resolves arbitrary text clicked in a Flow (Mermaid) diagram and opens the nearest matching Java method.
 * Heuristic: `com.pkg.Foo.bar` patterns first, otherwise `Foo.bar`-style segments (unique match only wins).
 */
object FlowNodeNavigator {

    /** Requires at least one package segment (`a.b.Class.method`). */
    private val fqcnMethodPattern =
        Regex("""(?<![\w\$])(?<fqcn>[\w$]+(?:\.[\w$]+){2,})\.(?<meth>[\w$]+)\b""")
    private val simplePattern =
        Regex("""(?<![\w\$])(?<cls>[\w$]{2,})\.(?<meth>[\w$]{2,})\b""")

    fun tryNavigate(project: Project, rawLabel: String) {
        val text = normalizeLabel(rawLabel)
        ApplicationManager.getApplication().executeOnPooledThread {
            val method =
                ReadAction.compute<PsiMethod?, Throwable> { resolveMethodInReadAction(project, text) }
                    ?: return@executeOnPooledThread

            ApplicationManager.getApplication().invokeLater {
                val vf = method.containingFile.virtualFile ?: return@invokeLater
                val offset = method.nameIdentifier?.textOffset ?: method.textRange.startOffset
                OpenFileDescriptor(project, vf, offset).navigateInEditor(project, true)
            }
        }
    }

    private fun normalizeLabel(s: String): String =
        s.replace('\u3000', ' ')
            .substringBefore("[")
            .substringBefore("{")
            .trim()
            .replace(Regex("\\s+"), " ")

    private fun resolveMethodInReadAction(project: Project, text: String): PsiMethod? {
        fqcnMethodPattern.find(text)?.let { mr ->
            val fqcn = mr.groups["fqcn"]?.value ?: return@let null
            val meth = mr.groups["meth"]?.value ?: return@let null
            val clazz =
                JavaPsiFacade.getInstance(project).findClass(fqcn, GlobalSearchScope.projectScope(project))
                    ?: return@let null
            clazz.findMethodsByName(meth, true).singleOrNull()?.let { return it }
            // overloads — pick declaring order first
            return clazz.findMethodsByName(meth, true).firstOrNull()
        }

        val scope = GlobalSearchScope.projectScope(project)
        val cache = PsiShortNamesCache.getInstance(project)
        val candidates = simplePattern.findAll(text).mapNotNull { mr ->
            val cls = mr.groups["cls"]?.value ?: return@mapNotNull null
            val meth = mr.groups["meth"]?.value ?: return@mapNotNull null
            Triple(mr.range.first, cls, meth)
        }.sortedBy { it.first }

        for ((_, cls, meth) in candidates) {
            if (looksLikeNoise(cls, meth)) continue
            val classes = cache.getClassesByName(cls, scope)
            val methods = classes.flatMap { c -> c.findMethodsByName(meth, true).toList() }.distinct()
            when (methods.size) {
                1 -> return methods[0]
                0 -> continue
                else -> return null
            }
        }
        return null
    }

    private fun looksLikeNoise(cls: String, meth: String): Boolean {
        if (cls.equals("TD", ignoreCase = true)) return true
        if (cls.lowercase().startsWith("flowchart")) return true
        if (meth.equals("prototype", ignoreCase = true)) return true
        return false
    }
}
