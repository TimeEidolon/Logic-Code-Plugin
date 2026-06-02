package com.github.timeeidolon.logiccodeplugin.settings

import com.github.timeeidolon.logiccodeplugin.MyBundle
import com.github.timeeidolon.logiccodeplugin.llm.ApiKeyResolver
import com.github.timeeidolon.logiccodeplugin.llm.LlmClient
import com.github.timeeidolon.logiccodeplugin.llm.LlmConfig
import com.github.timeeidolon.logiccodeplugin.project.ProjectKindHeuristics
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.ProjectManager
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.dsl.builder.*
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel

class ReportSettingsConfigurable : Configurable {

    private val settings = ReportPluginSettings.getInstance()
    private var settingsState = settings.state.copy()

    private var llmBaseUrlField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var llmModelField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var outputDirField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var maxSourceCharsField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var fastMaxSourceCharsField: Cell<com.intellij.ui.components.JBTextField>? = null
    private var llmConcurrencyField: Cell<com.intellij.ui.components.JBTextField>? = null

    private val apiKeyField = JBPasswordField().apply { columns = 42 }
    private val clearApiKeyCheckBox = JCheckBox("Remove saved API key")
    private val apiKeyStatusLabel = JLabel()
    private val generationModeBox = JComboBox(ReportPluginSettings.GenerationMode.entries.toTypedArray()).apply {
        selectedItem = settingsState.generationMode
    }

    private val hideTsWithoutFrontend = JCheckBox(MyBundle.message("settings.hideTs.docs"))
    private val hideJavaWithoutJvm = JCheckBox(MyBundle.message("settings.hideJava.report"))
    override fun getDisplayName(): String = "Logic Code Report"

    override fun createComponent(): JComponent {
        hideTsWithoutFrontend.isSelected = settings.state.hideTsDocsWithoutFrontendSignals
        hideJavaWithoutJvm.isSelected = settings.state.hideJavaReportWithoutJvmSignals
        val panel = panel {
        group("LLM Configuration") {
            row("Base URL:") {
                llmBaseUrlField = textField().text(settingsState.llmBaseUrl)
                    .comment("OpenAI-compatible API base URL")
            }
            row("Model:") {
                llmModelField = textField().text(settingsState.llmModel)
                    .comment("Model name (e.g. gpt-4o)")
            }
            row("API Key:") {
                cell(apiKeyField)
                    .align(AlignX.FILL)
                    .comment(
                        "Stored in the IDE password safe (not in project files). " +
                            "Leave empty and click OK to keep the current saved key. " +
                            "If no key is saved, the plugin uses environment variables LLM_API_KEY or OPENAI_API_KEY."
                    )
            }
            row {
                cell(clearApiKeyCheckBox)
            }
            row {
                cell(apiKeyStatusLabel)
            }
        }
        group("Output") {
            row("Output Directory:") {
                outputDirField = textField().text(settingsState.outputDir)
                    .comment("Relative to project root. Leave empty for &lt;project&gt;/docs")
            }
            row("Max Source Chars:") {
                maxSourceCharsField = textField().text(settingsState.maxSourceChars.toString())
                    .comment("Maximum characters of source context to collect")
            }
        }
        group("Performance") {
            row("Generation Mode:") {
                cell(generationModeBox)
                    .comment("FAST uses a smaller prompt and only includes entrypoint-related source files")
            }
            row("FAST Max Source Chars:") {
                fastMaxSourceCharsField = textField().text(settingsState.fastMaxSourceChars.toString())
                    .comment("Source context limit when Generation Mode is FAST")
            }
            row("LLM Concurrency:") {
                llmConcurrencyField = textField().text(settingsState.llmConcurrency.toString())
                    .comment("Parallel requests when FAST generates per-entrypoint sections (suggest 2-5)")
            }
        }
        group(MyBundle.message("settings.menus.group")) {
            row {
                cell(hideTsWithoutFrontend)
            }
            row {
                cell(hideJavaWithoutJvm)
            }
        }
        }
        refreshApiKeyStatus()
        return panel
    }

    override fun isModified(): Boolean {
        val keyModified = apiKeyField.password.isNotEmpty() || clearApiKeyCheckBox.isSelected
        return keyModified ||
            llmBaseUrlField?.component?.text != settings.state.llmBaseUrl ||
            llmModelField?.component?.text != settings.state.llmModel ||
            outputDirField?.component?.text != settings.state.outputDir ||
            maxSourceCharsField?.component?.text != settings.state.maxSourceChars.toString() ||
            (generationModeBox.selectedItem as? ReportPluginSettings.GenerationMode) != settings.state.generationMode ||
            fastMaxSourceCharsField?.component?.text != settings.state.fastMaxSourceChars.toString() ||
            llmConcurrencyField?.component?.text != settings.state.llmConcurrency.toString() ||
            hideTsWithoutFrontend.isSelected != settings.state.hideTsDocsWithoutFrontendSignals ||
            hideJavaWithoutJvm.isSelected != settings.state.hideJavaReportWithoutJvmSignals
    }

    override fun apply() {
        settings.state.llmBaseUrl = llmBaseUrlField?.component?.text ?: settings.state.llmBaseUrl
        settings.state.llmModel = llmModelField?.component?.text ?: settings.state.llmModel
        settings.state.outputDir = outputDirField?.component?.text ?: settings.state.outputDir
        settings.state.maxSourceChars = maxSourceCharsField?.component?.text?.toIntOrNull() ?: 180_000
        settings.state.generationMode =
            (generationModeBox.selectedItem as? ReportPluginSettings.GenerationMode) ?: settings.state.generationMode
        settings.state.fastMaxSourceChars = fastMaxSourceCharsField?.component?.text?.toIntOrNull() ?: 40_000
        settings.state.llmConcurrency = (llmConcurrencyField?.component?.text?.toIntOrNull() ?: 3).coerceIn(1, 12)
        settings.state.hideTsDocsWithoutFrontendSignals = hideTsWithoutFrontend.isSelected
        settings.state.hideJavaReportWithoutJvmSignals = hideJavaWithoutJvm.isSelected
        settingsState = settings.state.copy()

        ProjectManager.getInstance().openProjects.forEach { ProjectKindHeuristics.invalidate(it) }
        when {
            clearApiKeyCheckBox.isSelected -> ApiKeyResolver.clearStoredKey()
            apiKeyField.password.isNotEmpty() -> {
                val key = String(apiKeyField.password)
                ApiKeyResolver.setStoredKey(key)
            }
        }
        apiKeyField.text = ""
        clearApiKeyCheckBox.isSelected = false
        refreshApiKeyStatus()
    }

    override fun reset() {
        llmBaseUrlField?.component?.text = settings.state.llmBaseUrl
        llmModelField?.component?.text = settings.state.llmModel
        outputDirField?.component?.text = settings.state.outputDir
        maxSourceCharsField?.component?.text = settings.state.maxSourceChars.toString()
        generationModeBox.selectedItem = settings.state.generationMode
        fastMaxSourceCharsField?.component?.text = settings.state.fastMaxSourceChars.toString()
        llmConcurrencyField?.component?.text = settings.state.llmConcurrency.toString()
        hideTsWithoutFrontend.isSelected = settings.state.hideTsDocsWithoutFrontendSignals
        hideJavaWithoutJvm.isSelected = settings.state.hideJavaReportWithoutJvmSignals
        apiKeyField.text = ""
        clearApiKeyCheckBox.isSelected = false
        refreshApiKeyStatus()
    }

    private fun refreshApiKeyStatus() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously({
            val stored = ApiKeyResolver.getStoredKeyOnly()
            val masked = stored?.let { maskApiKey(it) }
            val baseUrl = llmBaseUrlField?.component?.text?.takeIf { it.isNotBlank() } ?: settingsState.llmBaseUrl
            val validation = if (!stored.isNullOrBlank()) {
                runCatching {
                    LlmClient(
                        LlmConfig(
                            baseUrl = baseUrl,
                            model = llmModelField?.component?.text?.takeIf { it.isNotBlank() } ?: settingsState.llmModel,
                            apiKey = stored
                        )
                    ).validateApiKey()
                }.getOrElse { LlmClient.ValidationResult(ok = false, message = it.toString()) }
            } else null
            javax.swing.SwingUtilities.invokeLater {
                apiKeyStatusLabel.text = if (!stored.isNullOrBlank()) {
                    val suffix = if (validation?.ok == true) " (validated)" else if (validation != null) " (invalid: ${validation.message})" else ""
                    "Saved API key: $masked$suffix"
                } else {
                    "Saved API key: no (set above, or use LLM_API_KEY / OPENAI_API_KEY)."
                }
            }
        }, "Checking API key status", false, null)
    }

    private fun maskApiKey(key: String): String {
        val trimmed = key.trim()
        if (trimmed.length <= 8) return "****"
        val prefix = trimmed.take(3) // e.g. "sk-"
        val suffix = trimmed.takeLast(4)
        return "$prefix…$suffix"
    }
}
