package com.github.timeeidolon.logiccodeplugin.cache

import com.github.timeeidolon.logiccodeplugin.llm.MethodLogicCacheService
import com.github.timeeidolon.logiccodeplugin.ui.ReportToolWindowRegistry
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiTreeChangeAdapter
import com.intellij.psi.PsiTreeChangeEvent
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Alarm
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.ConcurrentHashMap

/**
 * Listens for PSI method changes and:
 * - invalidates the changed method cache when its logic fingerprint changes (whitespace/comments ignored)
 * - finds callers via [ReferencesSearch] and marks their caches as STALE (pending refresh)
 *
 * Fingerprints suppress unnecessary work after comment-only / layout-only edits in the method body.
 */
@Service(Service.Level.PROJECT)
class MethodPsiChangeWatcher(private val project: Project) : Disposable {

    private val cache = MethodLogicCacheService.getInstance(project)
    private val alarm = Alarm(Alarm.ThreadToUse.POOLED_THREAD, this)
    private val pending = ConcurrentHashMap.newKeySet<SmartPsiElementPointer<PsiMethod>>()

    /** Serializes draining [pending]; [ConcurrentHashMap] key-set supports concurrent adds but iteration + clear stays atomic. */
    private val drainLock = Any()

    /** Last propagated logic fingerprint per method (only updated when we invalidate + search). */
    private val lastPropagatedFingerprints = ConcurrentHashMap<MethodLogicCacheService.MethodId, Int>()

    private val listener = object : PsiTreeChangeAdapter() {
        override fun childrenChanged(event: PsiTreeChangeEvent) = onPsiChanged(event)
        override fun childAdded(event: PsiTreeChangeEvent) = onPsiChanged(event)
        override fun childRemoved(event: PsiTreeChangeEvent) = onPsiChanged(event)
        override fun childReplaced(event: PsiTreeChangeEvent) = onPsiChanged(event)
        override fun childMoved(event: PsiTreeChangeEvent) = onPsiChanged(event)
        override fun propertyChanged(event: PsiTreeChangeEvent) = onPsiChanged(event)
    }

    init {
        PsiManager.getInstance(project).addPsiTreeChangeListener(listener, this)
    }

    private fun onPsiChanged(event: PsiTreeChangeEvent) {
        if (project.isDisposed) return
        val element = event.child ?: event.parent ?: return
        val method = PsiTreeUtil.getParentOfType(element, PsiMethod::class.java, false) ?: return
        if (!method.isValid) return

        val ptr = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(method)
        pending.add(ptr)

        // debounce + batch keystrokes across the same PSI commit batches
        alarm.cancelAllRequests()
        alarm.addRequest({ processPending() }, 800)
    }

    private fun processPending() {
        if (project.isDisposed) return
        val pointers = synchronized(drainLock) {
            val copy = pending.toList()
            pending.clear()
            copy
        }
        if (pointers.isEmpty()) return

        if (DumbService.isDumb(project)) {
            DumbService.getInstance(project).runWhenSmart { processPointersAsync(pointers) }
            return
        }
        processPointersAsync(pointers)
    }

    private fun processPointersAsync(pointers: List<SmartPsiElementPointer<PsiMethod>>) {
        AppExecutorUtil.getAppExecutorService().execute {
            if (project.isDisposed) return@execute
            ReadAction.run<RuntimeException> {
                val scope = GlobalSearchScope.projectScope(project)
                val byMethodId = LinkedHashMap<MethodLogicCacheService.MethodId, PsiMethod>()
                for (ptr in pointers) {
                    val method = ptr.element ?: continue
                    if (!method.isValid) continue
                    val id = MethodPsiResolver.methodIdOf(method) ?: continue
                    byMethodId[id] = method
                }

                for ((methodId, method) in byMethodId) {
                    val fp = MethodBodyFingerprints.logicStableFingerprint(method)
                    val prevFp = lastPropagatedFingerprints[methodId]
                    if (prevFp == null && cache.get(project, methodId) == null) {
                        lastPropagatedFingerprints[methodId] = fp
                        continue
                    }
                    if (prevFp != null && prevFp == fp) {
                        continue
                    }
                    lastPropagatedFingerprints[methodId] = fp

                    cache.invalidate(project, methodId)

                    val query = ReferencesSearch.search(method, scope)
                    query.forEach { ref ->
                        val caller = PsiTreeUtil.getParentOfType(ref.element, PsiMethod::class.java, false) ?: return@forEach
                        if (!caller.isValid || caller == method) return@forEach
                        val callerId = MethodPsiResolver.methodIdOf(caller) ?: return@forEach
                        cache.markStale(project, callerId, "Callee changed: ${methodId.displayName()}")
                    }
                }
            }
            javax.swing.SwingUtilities.invokeLater {
                ReportToolWindowRegistry.get(project)?.refreshStaleCachesUi()
            }
        }
    }

    override fun dispose() {
        alarm.cancelAllRequests()
    }
}
