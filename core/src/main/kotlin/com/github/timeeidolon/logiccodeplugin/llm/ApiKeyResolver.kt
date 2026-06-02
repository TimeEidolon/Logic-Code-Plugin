package com.github.timeeidolon.logiccodeplugin.llm

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.ide.passwordSafe.PasswordSafe
object ApiKeyResolver {

    private const val CREDENTIAL_SERVICE = "LogicCodeReport.llmApiKey"

    private fun credentialAttributes(): CredentialAttributes = CredentialAttributes(CREDENTIAL_SERVICE)

    fun hasStoredKey(): Boolean = !getStoredKeyOnly().isNullOrBlank()

    fun getStoredKeyOnly(): String? =
        PasswordSafe.instance.getPassword(credentialAttributes())?.takeIf { it.isNotBlank() }

    fun setStoredKey(value: String) {
        PasswordSafe.instance.setPassword(credentialAttributes(), value)
    }

    fun clearStoredKey() {
        PasswordSafe.instance.setPassword(credentialAttributes(), null)
    }

    /**
     * Returns API key from PasswordSafe first, then environment variables.
     */
    fun resolve(): String? {
        getStoredKeyOnly()?.let { return it }
        return System.getenv("LLM_API_KEY")?.takeIf { it.isNotBlank() }
            ?: System.getenv("OPENAI_API_KEY")?.takeIf { it.isNotBlank() }
    }
}

