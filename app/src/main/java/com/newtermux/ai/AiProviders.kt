package com.newtermux.ai

import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

interface CancellableAiCall {
    fun cancel()
}

interface AiTransport {
    @Throws(IOException::class)
    fun execute(request: AiRequest, onCallReady: (CancellableAiCall) -> Unit = {}): AiResponse
}

object AiPromptFormatter {
    fun systemPrompt(action: AiAction): String = when (action) {
        AiAction.EXPLAIN_ERROR ->
            "You are a concise terminal troubleshooting assistant inside an Android terminal app. " +
                "Explain the cause of the block outcome in 1-3 short plain sentences. " +
                "If a follow-up shell command would help verify or fix it, include ONE runnable command inside a ```sh code block. " +
                "Never use destructive commands unless clearly necessary."
        AiAction.PROPOSE_FIX ->
            "You are a concise terminal command assistant inside an Android terminal app. " +
                "Diagnose the block briefly (1-2 sentences) and propose ONE safe, directly runnable fix command inside a ```sh code block. " +
                "Do not add prompt prefixes like '$ ' inside the code block."
    }

    fun userPrompt(ctx: AiBlockContext, action: AiAction): String = buildString {
        appendLine("Task: ${action.label}")
        appendLine("Command: ${ctx.command.ifBlank { "(unknown)" }}")
        ctx.exitCode?.let { appendLine("Exit code: $it") }
        ctx.cwd?.takeIf { it.isNotBlank() }?.let { appendLine("Working directory: $it") }
        ctx.durationMs?.let { appendLine("Duration ms: $it") }
        appendLine("Output:")
        appendLine(ctx.outputTail.ifBlank { "(no captured output)" })
    }.trim()
}

object AiResponseParser {
    private val FENCED_CMD_REGEX = Regex("```(?:sh|bash|zsh|shell)?\\s*\\n([\\s\\S]*?)\\n```")

    fun parseStructuredText(rawText: String): AiResponse {
        val normalized = rawText.replace("\r\n", "\n").trim()
        if (normalized.isEmpty()) {
            return AiResponse(explanation = "Provider returned an empty response.", suggestedCommand = null)
        }
        val match = FENCED_CMD_REGEX.find(normalized)
        if (match != null) {
            val cmd = sanitizeCommand(match.groupValues[1])
            val explanation = normalized.removeRange(match.range)
                .replace(Regex("\\n{3,}"), "\n\n")
                .trim()
                .ifEmpty { "Suggested command ready to review." }
            return AiResponse(explanation = explanation, suggestedCommand = cmd)
        }

        // Support inline "Command: ..." trailing lines if provider omitted markdown fences.
        val lines = normalized.lines()
        val cmdLineIdx = lines.indexOfLast { line ->
            val trimmed = line.trim()
            trimmed.startsWith("Command:", ignoreCase = true) || trimmed.startsWith("Fix:", ignoreCase = true)
        }
        if (cmdLineIdx >= 0) {
            val rawCmd = lines[cmdLineIdx].substringAfter(':').trim()
            val cleanCmd = sanitizeCommand(rawCmd)
            if (cleanCmd != null) {
                val explanation = lines.filterIndexed { idx, _ -> idx != cmdLineIdx }
                    .joinToString("\n")
                    .trim()
                    .ifEmpty { "Suggested command ready to review." }
                return AiResponse(explanation = explanation, suggestedCommand = cleanCmd)
            }
        }
        return AiResponse(explanation = normalized, suggestedCommand = null)
    }

    fun parseProviderBody(provider: AiProviderKind, responseBody: String): AiResponse {
        val root = JSONObject(responseBody)
        val rawText = when (provider) {
            AiProviderKind.OPENAI_COMPATIBLE -> {
                val choices = root.optJSONArray("choices")
                val msg = choices?.optJSONObject(0)?.optJSONObject("message")
                msg?.optString("content").orEmpty()
            }
            AiProviderKind.ANTHROPIC -> {
                val content = root.optJSONArray("content")
                buildString {
                    if (content != null) {
                        for (i in 0 until content.length()) {
                            val item = content.optJSONObject(i) ?: continue
                            if (item.optString("type") == "text" || item.has("text")) {
                                if (isNotEmpty()) append('\n')
                                append(item.optString("text"))
                            }
                        }
                    }
                }
            }
            AiProviderKind.GEMINI -> {
                val candidates = root.optJSONArray("candidates")
                val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                buildString {
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val part = parts.optJSONObject(i) ?: continue
                            if (part.has("text")) {
                                if (isNotEmpty()) append('\n')
                                append(part.optString("text"))
                            }
                        }
                    }
                }
            }
        }
        return parseStructuredText(rawText)
    }

    private fun sanitizeCommand(raw: String): String? {
        val cleaned = raw.trim()
            .lines()
            .map { line ->
                val t = line.trimEnd()
                if (t.startsWith("$ ")) t.removePrefix("$ ") else t
            }
            .joinToString("\n")
            .trim()
        return cleaned.ifEmpty { null }
    }
}

/**
 * Provider-agnostic OkHttp AI transport.
 * Guardrail (Amendment A2): retryOnConnectionFailure is explicitly false (0 auto retries).
 */
class OkHttpAiTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .retryOnConnectionFailure(false)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()
) : AiTransport {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    override fun execute(request: AiRequest, onCallReady: (CancellableAiCall) -> Unit): AiResponse {
        val httpRequest = buildHttpRequest(request)
        val call: Call = client.newCall(httpRequest)
        onCallReady(object : CancellableAiCall {
            override fun cancel() = call.cancel()
        })
        call.execute().use { resp ->
            val rawBody = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val snippet = extractErrorMessage(rawBody).ifBlank { "HTTP ${resp.code}" }
                throw IOException("${request.config.provider.displayName} error (${resp.code}): $snippet")
            }
            return try {
                AiResponseParser.parseProviderBody(request.config.provider, rawBody)
            } catch (e: Exception) {
                throw IOException("Invalid ${request.config.provider.displayName} response format", e)
            }
        }
    }

    fun buildHttpRequest(request: AiRequest): Request {
        val cfg = request.config
        val sys = AiPromptFormatter.systemPrompt(request.action)
        val usr = AiPromptFormatter.userPrompt(request.blockContext, request.action)
        val model = cfg.effectiveModel()
        val baseUrl = cfg.effectiveBaseUrl()

        return when (cfg.provider) {
            AiProviderKind.OPENAI_COMPATIBLE -> {
                val endpoint = if (baseUrl.endsWith("/chat/completions")) baseUrl else "$baseUrl/chat/completions"
                val payload = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", cfg.maxTokens)
                    put("temperature", 0.2)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "system").put("content", sys))
                        put(JSONObject().put("role", "user").put("content", usr))
                    })
                }
                Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer ${cfg.apiKey}")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }
            AiProviderKind.ANTHROPIC -> {
                val endpoint = if (baseUrl.endsWith("/messages")) baseUrl else "$baseUrl/messages"
                val payload = JSONObject().apply {
                    put("model", model)
                    put("max_tokens", cfg.maxTokens)
                    put("system", sys)
                    put("messages", JSONArray().apply {
                        put(JSONObject().put("role", "user").put("content", usr))
                    })
                }
                Request.Builder()
                    .url(endpoint)
                    .header("x-api-key", cfg.apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }
            AiProviderKind.GEMINI -> {
                val endpoint = if (baseUrl.endsWith(":generateContent")) {
                    baseUrl
                } else {
                    "$baseUrl/models/$model:generateContent"
                }
                val payload = JSONObject().apply {
                    put("systemInstruction", JSONObject().apply {
                        put("parts", JSONArray().put(JSONObject().put("text", sys)))
                    })
                    put("contents", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "user")
                            put("parts", JSONArray().put(JSONObject().put("text", usr)))
                        })
                    })
                    put("generationConfig", JSONObject().apply {
                        put("maxOutputTokens", cfg.maxTokens)
                        put("temperature", 0.2)
                    })
                }
                Request.Builder()
                    .url(endpoint)
                    .header("x-goog-api-key", cfg.apiKey)
                    .post(payload.toString().toRequestBody(jsonMediaType))
                    .build()
            }
        }
    }

    private fun extractErrorMessage(rawBody: String): String {
        if (rawBody.isBlank()) return ""
        return runCatching {
            val obj = JSONObject(rawBody)
            obj.optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() }
                ?: obj.optString("message").takeIf { it.isNotBlank() }
                ?: rawBody.take(180)
        }.getOrDefault(rawBody.take(180))
    }
}
