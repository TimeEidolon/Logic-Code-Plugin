package com.github.timeeidolon.logiccodeplugin.llm

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

class LlmClient(private val config: LlmConfig) {

    /**
     * Non-stream response (OpenAI-compatible).
     */
    fun chatCompletion(prompt: String, timeoutMs: Int = 600_000): String {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("Missing API key. Configure it in Settings → Tools → Logic Code Report or set LLM_API_KEY env variable.")
        }

        val endpoint = if (config.baseUrl.endsWith("/chat/completions")) config.baseUrl
        else "${config.baseUrl.trimEnd('/')}/chat/completions"

        val payload = mapOf(
            "model" to config.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You generate precise Java project analysis reports in Markdown."),
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.2
        )

        val gson = Gson()
        val jsonBody = gson.toJson(payload)

        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs

        OutputStreamWriter(connection.outputStream).use { it.write(jsonBody) }

        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = responseStream.bufferedReader().use { it.readText() }

        if (responseCode !in 200..299) {
            throw RuntimeException("LLM API returned $responseCode: $responseText")
        }

        val jsonObject = JsonParser.parseString(responseText).asJsonObject
        return jsonObject
            .getAsJsonArray("choices")
            .get(0).asJsonObject
            .getAsJsonObject("message")
            .get("content").asString
    }

    /**
     * Streamed response (SSE, OpenAI-compatible): parses `data: {...}` chunks and emits delta content.
     * Falls back to non-stream parsing if the server doesn't use SSE.
     */
    fun chatCompletionStream(
        prompt: String,
        timeoutMs: Int = 600_000,
        onDelta: (String) -> Unit
    ): String {
        if (config.apiKey.isBlank()) {
            throw IllegalStateException("Missing API key. Configure it in Settings → Tools → Logic Code Report or set LLM_API_KEY env variable.")
        }

        val endpoint = if (config.baseUrl.endsWith("/chat/completions")) config.baseUrl
        else "${config.baseUrl.trimEnd('/')}/chat/completions"

        val payload = mapOf(
            "model" to config.model,
            "messages" to listOf(
                mapOf("role" to "system", "content" to "You generate precise Java project analysis reports in Markdown."),
                mapOf("role" to "user", "content" to prompt)
            ),
            "temperature" to 0.2,
            "stream" to true
        )

        val gson = Gson()
        val jsonBody = gson.toJson(payload)

        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "text/event-stream")
        connection.doOutput = true
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs

        OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(jsonBody) }

        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseTextIfError = if (responseCode !in 200..299) {
            responseStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
        } else null

        if (responseCode !in 200..299) {
            throw RuntimeException("LLM API returned $responseCode: $responseTextIfError")
        }

        val full = StringBuilder()

        // SSE: each line is "data: {...}" and ends with "data: [DONE]"
        responseStream.bufferedReader(StandardCharsets.UTF_8).useLines { lines ->
            var sawSse = false
            for (line in lines) {
                val trimmed = line.trim()
                if (trimmed.isEmpty()) continue

                if (trimmed.startsWith("data:")) {
                    sawSse = true
                    val data = trimmed.removePrefix("data:").trim()
                    if (data == "[DONE]") break

                    val json = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                    val choice0 = json.getAsJsonArray("choices")?.get(0)?.asJsonObject ?: continue

                    val deltaObj = choice0.getAsJsonObject("delta")
                    val content = deltaObj?.get("content")?.takeIf { !it.isJsonNull }?.asString
                    if (!content.isNullOrEmpty()) {
                        onDelta(content)
                        full.append(content)
                    }
                } else if (!sawSse) {
                    // Some providers might ignore stream=true and return normal JSON in one shot.
                    full.append(line).append('\n')
                }
            }
        }

        val text = full.toString()
        return if (text.ltrim().startsWith("{")) {
            // Non-SSE fallback: parse as normal OpenAI JSON and return message content.
            val jsonObject = JsonParser.parseString(text).asJsonObject
            jsonObject
                .getAsJsonArray("choices")
                .get(0).asJsonObject
                .getAsJsonObject("message")
                .get("content").asString
        } else {
            text
        }
    }

    data class ValidationResult(
        val ok: Boolean,
        val message: String
    )

    fun validateApiKey(timeoutMs: Int = 12_000): ValidationResult {
        if (config.apiKey.isBlank()) {
            return ValidationResult(ok = false, message = "Missing API key")
        }

        // Most OpenAI-compatible providers support GET /models under the same base URL.
        val base = when {
            config.baseUrl.endsWith("/chat/completions") ->
                config.baseUrl.substringBeforeLast("/chat/completions")
            else -> config.baseUrl
        }.trimEnd('/')
        val endpoint = "$base/models"

        val connection = URI(endpoint).toURL().openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.connectTimeout = timeoutMs
        connection.readTimeout = timeoutMs

        val responseCode = connection.responseCode
        val responseStream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = responseStream?.bufferedReader()?.use { it.readText() }.orEmpty()

        return if (responseCode in 200..299) {
            ValidationResult(ok = true, message = "OK")
        } else {
            val short = responseText.take(300).replace("\n", " ").replace("\r", " ").trim()
            ValidationResult(ok = false, message = "HTTP $responseCode${if (short.isNotBlank()) ": $short" else ""}")
        }
    }

    private fun String.ltrim(): String {
        var i = 0
        while (i < length && this[i].isWhitespace()) i++
        return substring(i)
    }
}
