package com.github.timeeidolon.logiccodeplugin.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*

@State(
    name = "LogicCodeReportSettings",
    storages = [Storage("logic-code-report.xml")]
)
class ReportPluginSettings : PersistentStateComponent<ReportPluginSettings.State> {

    data class State(
        var llmBaseUrl: String = "https://api.openai.com/v1",
        var llmModel: String = "gpt-4o",
        var outputDir: String = "",
        var maxSourceChars: Int = 180_000,
        var generationMode: GenerationMode = GenerationMode.FULL,
        var fastMaxSourceChars: Int = 40_000,
        var llmConcurrency: Int = 3,
        /** When true, hide TS/JS doc actions if the project has no frontend-like signals (see [com.github.timeeidolon.logiccodeplugin.project.ProjectKindHeuristics]). */
        var hideTsDocsWithoutFrontendSignals: Boolean = true,
        /** When true, hide the Java/Kotlin LLM report action if the project has no JVM/Gradle/Maven-like signals. */
        var hideJavaReportWithoutJvmSignals: Boolean = true,
        /**
         * Bumps when menu-visibility defaults change. Values &lt; 1 mean persisted state from before this field existed:
         * we re-apply smart-menu defaults once so missing XML keys are not interpreted as "both off".
         */
        var menuVisibilityDefaultsVersion: Int = 1,
    )

    enum class GenerationMode { FULL, FAST }

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = if (state.menuVisibilityDefaultsVersion < 1) {
            state.copy(
                hideTsDocsWithoutFrontendSignals = true,
                hideJavaReportWithoutJvmSignals = true,
                menuVisibilityDefaultsVersion = 1,
            )
        } else {
            state
        }
    }

    companion object {
        fun getInstance(): ReportPluginSettings =
            ApplicationManager.getApplication().getService(ReportPluginSettings::class.java)
    }
}
