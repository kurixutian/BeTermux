package com.newtermux.ai

import com.newtermux.features.NewTermuxSettings

enum class AiProviderKind(
    val id: String,
    val displayName: String,
    val defaultModel: String,
    val defaultBaseUrl: String
) {
    OPENAI_COMPATIBLE(
        id = NewTermuxSettings.AI_PROVIDER_OPENAI,
        displayName = "OpenAI Compatible",
        defaultModel = "gpt-4o-mini",
        defaultBaseUrl = "https://api.openai.com/v1"
    ),
    ANTHROPIC(
        id = NewTermuxSettings.AI_PROVIDER_ANTHROPIC,
        displayName = "Anthropic",
        defaultModel = "claude-3-5-sonnet-latest",
        defaultBaseUrl = "https://api.anthropic.com/v1"
    ),
    GEMINI(
        id = NewTermuxSettings.AI_PROVIDER_GEMINI,
        displayName = "Google Gemini",
        defaultModel = "gemini-2.0-flash",
        defaultBaseUrl = "https://generativelanguage.googleapis.com/v1beta"
    );

    companion object {
        fun fromId(id: String?): AiProviderKind =
            entries.firstOrNull { it.id == id } ?: OPENAI_COMPATIBLE
    }
}

enum class AiAction(val id: String, val label: String) {
    EXPLAIN_ERROR("explain", "Explain error"),
    PROPOSE_FIX("fix", "Propose fix")
}

data class AiConfig(
    val enabled: Boolean,
    val provider: AiProviderKind,
    val model: String,
    val baseUrl: String,
    val apiKey: String,
    val maxTokens: Int = NewTermuxSettings.AI_MAX_TOKENS_DEFAULT
) {
    fun effectiveModel(): String = model.trim().ifEmpty { provider.defaultModel }
    fun effectiveBaseUrl(): String = baseUrl.trim().trimEnd('/').ifEmpty { provider.defaultBaseUrl }
}

data class AiBlockContext(
    val blockId: Long,
    val command: String,
    val exitCode: Int?,
    val cwd: String?,
    val durationMs: Long?,
    val outputTail: String
)

data class AiRequest(
    val action: AiAction,
    val blockContext: AiBlockContext,
    val config: AiConfig
)

data class AiResponse(
    val explanation: String,
    val suggestedCommand: String?
)

sealed class AiSuggestionState {
    abstract val blockId: Long
    abstract val action: AiAction

    data class Loading(
        override val blockId: Long,
        override val action: AiAction
    ) : AiSuggestionState()

    data class DisabledOrUnconfigured(
        override val blockId: Long,
        override val action: AiAction,
        val reason: String
    ) : AiSuggestionState()

    data class Error(
        override val blockId: Long,
        override val action: AiAction,
        val message: String
    ) : AiSuggestionState()

    data class Ready(
        override val blockId: Long,
        override val action: AiAction,
        val explanation: String,
        val suggestedCommand: String?
    ) : AiSuggestionState()
}
