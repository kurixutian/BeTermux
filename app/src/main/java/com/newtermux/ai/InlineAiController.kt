package com.newtermux.ai

import android.content.Context
import com.newtermux.blocks.TerminalBlock
import com.newtermux.features.NewTermuxSettings
import com.termux.terminal.TerminalBuffer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Coordinates inline AI block assistance without any separate chat tab or background polling.
 *
 * Enforces Amendment A2 guardrails:
 * - Max 1 concurrent in-flight request globally (cancel-on-new).
 * - 0 automatic retries (manual Retry action only).
 * - Configurable max tokens (default 1024 via [NewTermuxSettings.getAiMaxTokens]).
 */
class InlineAiController(
    context: Context,
    private val transport: AiTransport = OkHttpAiTransport(),
    private val configProvider: (() -> AiConfig)? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate
) {
    private data class RequestSnapshot(
        val action: AiAction,
        val blockContext: AiBlockContext
    )

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + mainDispatcher)
    private val lock = Any()

    private var activeJob: Job? = null
    private var activeCall: CancellableAiCall? = null
    private var activeRequestSeq: Long = 0L

    private val lastSnapshotByBlock = LinkedHashMap<Long, RequestSnapshot>()

    private val suggestionsFlow = MutableStateFlow<Map<Long, AiSuggestionState>>(emptyMap())
    val suggestions: StateFlow<Map<Long, AiSuggestionState>> = suggestionsFlow.asStateFlow()

    private val inFlightCountFlow = MutableStateFlow(0)
    val inFlightCount: StateFlow<Int> = inFlightCountFlow.asStateFlow()

    fun currentConfig(): AiConfig {
        configProvider?.let { return it() }
        val provider = AiProviderKind.fromId(NewTermuxSettings.getAiProvider(appContext))
        return AiConfig(
            enabled = NewTermuxSettings.isAiEnabled(appContext),
            provider = provider,
            model = NewTermuxSettings.getAiModel(appContext),
            baseUrl = NewTermuxSettings.getAiBaseUrl(appContext),
            apiKey = AiKeyStore.getApiKey(appContext, provider),
            maxTokens = NewTermuxSettings.getAiMaxTokens(appContext)
        )
    }

    fun requestForBlock(
        block: TerminalBlock,
        action: AiAction,
        buffer: TerminalBuffer?,
        sessionCwd: String?
    ) {
        val blockCtx = AiContextBuilder.buildFromBuffer(block, buffer, sessionCwd)
        dispatchContext(blockCtx, action)
    }

    fun retryBlock(blockId: Long) {
        val snap = synchronized(lock) { lastSnapshotByBlock[blockId] } ?: return
        dispatchContext(snap.blockContext, snap.action)
    }

    fun dismissBlock(blockId: Long) {
        synchronized(lock) {
            val current = suggestionsFlow.value[blockId]
            if (current is AiSuggestionState.Loading) {
                cancelLocked()
            }
            suggestionsFlow.value = suggestionsFlow.value - blockId
        }
    }

    fun cancelActive() {
        synchronized(lock) {
            val loadingIds = suggestionsFlow.value
                .filterValues { it is AiSuggestionState.Loading }
                .keys
            cancelLocked()
            if (loadingIds.isNotEmpty()) {
                suggestionsFlow.value = suggestionsFlow.value - loadingIds
            }
        }
    }

    fun destroy() {
        cancelActive()
        scope.cancel()
    }

    private fun dispatchContext(blockCtx: AiBlockContext, action: AiAction) {
        val config = currentConfig()
        synchronized(lock) {
            lastSnapshotByBlock[blockCtx.blockId] = RequestSnapshot(action, blockCtx)
            if (lastSnapshotByBlock.size > 64) {
                val oldestKey = lastSnapshotByBlock.keys.firstOrNull()
                if (oldestKey != null) lastSnapshotByBlock.remove(oldestKey)
            }
        }

        if (!config.enabled) {
            synchronized(lock) {
                cancelLocked()
                val cleaned = suggestionsFlow.value.filterValues { it !is AiSuggestionState.Loading }
                suggestionsFlow.value = cleaned + (
                    blockCtx.blockId to AiSuggestionState.DisabledOrUnconfigured(
                        blockId = blockCtx.blockId,
                        action = action,
                        reason = "AI Assistant is turned off. Enable it in Settings to use ${action.label}."
                    )
                    )
            }
            return
        }

        if (config.apiKey.isBlank()) {
            synchronized(lock) {
                cancelLocked()
                val cleaned = suggestionsFlow.value.filterValues { it !is AiSuggestionState.Loading }
                suggestionsFlow.value = cleaned + (
                    blockCtx.blockId to AiSuggestionState.DisabledOrUnconfigured(
                        blockId = blockCtx.blockId,
                        action = action,
                        reason = "Add a ${config.provider.displayName} API key in Settings to use ${action.label}."
                    )
                    )
            }
            return
        }

        val mySeq: Long
        synchronized(lock) {
            cancelLocked()
            mySeq = ++activeRequestSeq
            inFlightCountFlow.value = 1
            val cleaned = suggestionsFlow.value.filterValues { it !is AiSuggestionState.Loading }
            suggestionsFlow.value = cleaned + (
                blockCtx.blockId to AiSuggestionState.Loading(
                    blockId = blockCtx.blockId,
                    action = action
                )
                )
        }

        val request = AiRequest(action = action, blockContext = blockCtx, config = config)
        val job = scope.launch {
            val result = try {
                val response = withContext(ioDispatcher) {
                    transport.execute(request) { call ->
                        synchronized(lock) {
                            if (mySeq == activeRequestSeq) {
                                activeCall = call
                            } else {
                                call.cancel()
                            }
                        }
                    }
                }
                Result.success(response)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Result.failure(t)
            } finally {
                synchronized(lock) {
                    if (mySeq == activeRequestSeq) {
                        activeCall = null
                        activeJob = null
                        inFlightCountFlow.value = 0
                    }
                }
            }

            synchronized(lock) {
                if (mySeq != activeRequestSeq) return@synchronized
                val nextState = result.fold(
                    onSuccess = { resp ->
                        AiSuggestionState.Ready(
                            blockId = blockCtx.blockId,
                            action = action,
                            explanation = resp.explanation,
                            suggestedCommand = resp.suggestedCommand
                        )
                    },
                    onFailure = { err ->
                        AiSuggestionState.Error(
                            blockId = blockCtx.blockId,
                            action = action,
                            message = err.message?.takeIf { it.isNotBlank() } ?: "AI request failed."
                        )
                    }
                )
                suggestionsFlow.value = suggestionsFlow.value + (blockCtx.blockId to nextState)
            }
        }

        synchronized(lock) {
            if (mySeq == activeRequestSeq && activeJob == null && inFlightCountFlow.value == 1) {
                activeJob = job
            }
        }
    }

    private fun cancelLocked() {
        activeRequestSeq++
        activeCall?.cancel()
        activeCall = null
        activeJob?.cancel()
        activeJob = null
        inFlightCountFlow.value = 0
    }
}
