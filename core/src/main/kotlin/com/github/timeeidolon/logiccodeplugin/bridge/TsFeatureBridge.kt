package com.github.timeeidolon.logiccodeplugin.bridge

import com.github.timeeidolon.logiccodeplugin.project.ProjectKindHeuristics
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project

/**
 * Optional :lang-ts features invoked from :core via reflection (no compile dependency on TS/JavaScript PSI).
 */
object TsFeatureBridge {
    private val log = Logger.getInstance(TsFeatureBridge::class.java)

    /**
     * Writes `FRONTEND_ROUTE_GUIDE.md` when [ProjectKindHeuristics] sees frontend markers and
     * [com.github.timeeidolon.logiccodeplugin.ts.FrontendRouteGuideWriter] is on the classpath.
     */
    fun tryWriteFrontendRouteGuideIfApplicable(project: Project) {
        if (!ProjectKindHeuristics.signals(project).hasFrontendMarkers) return
        runCatching {
            val cls = Class.forName("com.github.timeeidolon.logiccodeplugin.ts.FrontendRouteGuideWriter")
            val m = cls.getMethod("write", Project::class.java)
            m.invoke(null, project)
        }.onFailure { t ->
            log.debug("Frontend route guide writer unavailable: ${t.message}", t)
        }
    }
}
