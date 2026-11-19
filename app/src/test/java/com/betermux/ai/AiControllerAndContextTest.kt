package com.betermux.ai

import android.graphics.Typeface
import android.os.Looper
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.betermux.blocks.BlockDensity
import com.betermux.blocks.BlockSessionController
import com.betermux.blocks.BlockWorkspaceHost
import com.betermux.blocks.TerminalBlock
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.features.BeTermuxSettings
import com.betermux.input.InputDraftStore
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.Osc133Event
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalRowPainter
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class AiControllerAndContextTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun contextBuilderRedactsSecretsAndPreservesErrorTail() {
        val client = TermuxTerminalSessionClientBase()
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 200, client)
        val emulator = TerminalEmulator(session, 160, 80, 12, 24, 200, client)
        session.attachEmulatorForTesting(emulator)

        val noise = "info: compiling step ".repeat(350) // > 7000 chars
        val payload = buildString {
            append("$noise\r\n")
            append("Authorization: Bearer super_secret_bearer_token_123456\r\n")
            append("OPENAI_API_KEY=sk-proj-abc1234567890xyz\r\n")
            append("-----BEGIN RSA PRIVATE KEY-----\r\n")
            append("MIIBOgIBAAJBALeakedKeyMaterial\r\n")
            append("-----END RSA PRIVATE KEY-----\r\n")
            append("error: undefined symbol main_entry\r\n")
        }.toByteArray(StandardCharsets.UTF_8)
        emulator.append(payload, payload.size)

        val block = TerminalBlock(
            id = 42L,
            command = "curl -H 'Authorization: Bearer cmd_secret_987654321' https://api.example.com",
            promptStartAbsRow = 0L,
            commandStartAbsRow = 0L,
            outputStartAbsRow = 0L,
            endAbsRow = emulator.mainBuffer.screenEndAbsoluteRowExclusive - 1L,
            exitCode = 1,
            timestampMs = 1000L,
            completedAtMs = 2400L,
            cwd = "/data/project"
        )

        val ctx = AiContextBuilder.buildFromBuffer(block, emulator.mainBuffer, "/home")
        assertFalse(ctx.command.contains("cmd_secret_987654321"))
        assertFalse(ctx.outputTail.contains("super_secret_bearer_token_123456"))
        assertFalse(ctx.outputTail.contains("sk-proj-abc1234567890xyz"))
        assertFalse(ctx.outputTail.contains("MIIBOgIBAAJBALeakedKeyMaterial"))
        assertTrue(ctx.outputTail.contains("error: undefined symbol main_entry"))
        assertEquals(1, ctx.exitCode)
        assertEquals(1400L, ctx.durationMs)
    }

    @Test
    fun controllerEnforcesCancelOnNewZeroAutoRetriesMaxTokensAndDisabledState() {
        val ctx = RuntimeEnvironment.getApplication()
        assertEquals(1024, BeTermuxSettings.getAiMaxTokens(ctx))

        var transportCalls = AtomicInteger(0)
        val blockingEntered = CountDownLatch(1)
        val allowFinish1 = CountDownLatch(1)
        val call1Cancelled = AtomicBoolean(false)
        val capturedMaxTokens = mutableListOf<Int>()

        val transport = object : AiTransport {
            override fun execute(request: AiRequest, onCallReady: (CancellableAiCall) -> Unit): AiResponse {
                val n = transportCalls.incrementAndGet()
                capturedMaxTokens.add(request.config.maxTokens)
                return when (n) {
                    1 -> {
                        onCallReady(object : CancellableAiCall {
                            override fun cancel() {
                                call1Cancelled.set(true)
                                allowFinish1.countDown()
                            }
                        })
                        blockingEntered.countDown()
                        allowFinish1.await(2, TimeUnit.SECONDS)
                        throw IOException("cancelled")
                    }
                    2 -> throw IOException("upstream 503")
                    else -> AiResponse("Package index stale.", "pkg update")
                }
            }
        }

        val controller = InlineAiController(
            context = ctx,
            transport = transport,
            mainDispatcher = Dispatchers.Unconfined
        )

        val block1 = TerminalBlock(1L, "npm install", 0L, 0L, 1L, 2L, 1, 10L, 20L, "/")
        val block2 = TerminalBlock(2L, "pkg install git", 3L, 3L, 4L, 5L, 1, 30L, 40L, "/")

        // 1) Disabled state
        BeTermuxSettings.setAiEnabled(ctx, false)
        controller.requestForBlock(block1, AiAction.EXPLAIN_ERROR, null, "/")
        assertTrue(controller.suggestions.value[1L] is AiSuggestionState.DisabledOrUnconfigured)
        assertEquals(0, transportCalls.get())

        // 2) Enabled without API key -> still unconfigured offline state
        BeTermuxSettings.setAiEnabled(ctx, true)
        AiKeyStore.setApiKey(ctx, AiProviderKind.OPENAI_COMPATIBLE, "")
        controller.requestForBlock(block1, AiAction.EXPLAIN_ERROR, null, "/")
        assertTrue(controller.suggestions.value[1L] is AiSuggestionState.DisabledOrUnconfigured)
        assertEquals(0, transportCalls.get())

        // 3) KeyStore persistence + custom maxTokens + cancel-on-new + 0 auto retries
        AiKeyStore.setApiKey(ctx, AiProviderKind.OPENAI_COMPATIBLE, "sk-test-1234567890")
        assertEquals("sk-test-1234567890", AiKeyStore.getApiKey(ctx, AiProviderKind.OPENAI_COMPATIBLE))
        assertEquals("sk-t…7890", AiKeyStore.maskedKeyHint(ctx, AiProviderKind.OPENAI_COMPATIBLE))
        BeTermuxSettings.setAiMaxTokens(ctx, 2048)

        controller.requestForBlock(block1, AiAction.EXPLAIN_ERROR, null, "/")
        assertTrue(blockingEntered.await(2, TimeUnit.SECONDS))
        assertEquals(1, controller.inFlightCount.value)

        // Trigger request on block2 while block1 is still in-flight -> must cancel block1 & hit error ONCE (0 auto retries)
        controller.requestForBlock(block2, AiAction.PROPOSE_FIX, null, "/")
        assertTrue(call1Cancelled.get())

        val deadline = System.currentTimeMillis() + 3000L
        while (controller.suggestions.value[2L] !is AiSuggestionState.Error && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        val err2 = controller.suggestions.value[2L] as AiSuggestionState.Error
        assertTrue(err2.message.contains("503"))
        assertNull(controller.suggestions.value[1L]) // cancelled Loading cleared cleanly
        assertEquals(2, transportCalls.get()) // exact: 1 cancelled + 1 failed (0 automatic retries!)
        assertEquals(0, controller.inFlightCount.value)
        assertEquals(listOf(2048, 2048), capturedMaxTokens)

        // 4) Manual Retry recovers cleanly
        controller.retryBlock(2L)
        val deadline2 = System.currentTimeMillis() + 3000L
        while (controller.suggestions.value[2L] !is AiSuggestionState.Ready && System.currentTimeMillis() < deadline2) {
            Thread.sleep(10)
        }
        val ready2 = controller.suggestions.value[2L] as AiSuggestionState.Ready
        assertEquals("Package index stale.", ready2.explanation)
        assertEquals("pkg update", ready2.suggestedCommand)
        assertEquals(3, transportCalls.get())

        controller.destroy()
    }

    @Test
    fun providerFormattingAndParsingSupportsOpenAiAnthropicAndGemini() {
        val transport = OkHttpAiTransport()
        val ctx = AiBlockContext(7L, "git status", 128, "/repo", 50L, "fatal: not a git repo")

        val openaiReq = transport.buildHttpRequest(
            AiRequest(
                AiAction.PROPOSE_FIX,
                ctx,
                AiConfig(true, AiProviderKind.OPENAI_COMPATIBLE, "", "", "key-oai", 1024)
            )
        )
        assertEquals("https://api.openai.com/v1/chat/completions", openaiReq.url.toString())
        assertEquals("Bearer key-oai", openaiReq.header("Authorization"))

        val anthropicReq = transport.buildHttpRequest(
            AiRequest(
                AiAction.EXPLAIN_ERROR,
                ctx,
                AiConfig(true, AiProviderKind.ANTHROPIC, "claude-3-5-haiku-latest", "", "key-ant", 512)
            )
        )
        assertEquals("https://api.anthropic.com/v1/messages", anthropicReq.url.toString())
        assertEquals("key-ant", anthropicReq.header("x-api-key"))
        assertEquals("2023-06-01", anthropicReq.header("anthropic-version"))

        val geminiReq = transport.buildHttpRequest(
            AiRequest(
                AiAction.PROPOSE_FIX,
                ctx,
                AiConfig(true, AiProviderKind.GEMINI, "", "https://generativelanguage.googleapis.com/v1beta", "key-gem", 1536)
            )
        )
        assertEquals(
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent",
            geminiReq.url.toString()
        )
        assertEquals("key-gem", geminiReq.header("x-goog-api-key"))

        val oaiParsed = AiResponseParser.parseProviderBody(
            AiProviderKind.OPENAI_COMPATIBLE,
            """{"choices":[{"message":{"content":"Directory has no git metadata.\n```sh\n$ git init\n```"}}]}"""
        )
        assertEquals("Directory has no git metadata.", oaiParsed.explanation)
        assertEquals("git init", oaiParsed.suggestedCommand)

        val antParsed = AiResponseParser.parseProviderBody(
            AiProviderKind.ANTHROPIC,
            """{"content":[{"type":"text","text":"Run init inside repo.\n```bash\ngit init\n```"}]}"""
        )
        assertEquals("Run init inside repo.", antParsed.explanation)
        assertEquals("git init", antParsed.suggestedCommand)

        val gemParsed = AiResponseParser.parseProviderBody(
            AiProviderKind.GEMINI,
            """{"candidates":[{"content":{"parts":[{"text":"Initialize git.\nCommand: git init"}]}}]}"""
        )
        assertEquals("Initialize git.", gemParsed.explanation)
        assertEquals("git init", gemParsed.suggestedCommand)
    }

    @Test
    fun inlineAiSuggestionBlockRendersUnderBlockAcceptNeverAutoExecutesEditAndAccessibility() {
        val ctx = RuntimeEnvironment.getApplication()
        BeTermuxSettings.setAiEnabled(ctx, true)
        AiKeyStore.setApiKey(ctx, AiProviderKind.OPENAI_COMPATIBLE, "sk-ui-test-key")

        val blockController = BlockSessionController(scheduleDelayMs = { _, r -> r.run() }, cancelCallback = {})
        val draftStore = InputDraftStore(ctx)
        val client = object : TermuxTerminalSessionClientBase() {
            override fun onTextChanged(changedSession: TerminalSession) = blockController.onSessionTextChanged(changedSession)
            override fun onOsc133Event(session: TerminalSession, event: Osc133Event) = blockController.onOsc133Event(session, event)
        }
        val session = TerminalSession("/bin/sh", "/", emptyArray(), emptyArray(), 200, client)
        val emulator = TerminalEmulator(session, 60, 12, 12, 24, 200, client)
        session.attachEmulatorForTesting(emulator)
        draftStore.setDraft(session.mHandle, "")
        blockController.setActiveSession(session)

        val stream = ("\u001B]133;A\u0007$ \u001B]133;B\u0007apt install curl\r\n\u001B]133;C\u0007E: Unable to locate package curl\r\n\u001B]133;D;100\u0007")
            .toByteArray(StandardCharsets.UTF_8)
        emulator.append(stream, stream.size)

        val targetBlock = blockController.getCurrentState().blocks.first()
        val rowsBeforeAi = emulator.mainBuffer.screenEndAbsoluteRowExclusive
        val transcriptBeforeAi = emulator.mainBuffer.getSelectedTextAbsolute(0L, rowsBeforeAi - 1L)

        val aiController = InlineAiController(
            context = ctx,
            transport = object : AiTransport {
                override fun execute(request: AiRequest, onCallReady: (CancellableAiCall) -> Unit): AiResponse {
                    return AiResponse("Local package index is empty.", "apt update && apt install -y curl")
                }
            },
            ioDispatcher = Dispatchers.Unconfined,
            mainDispatcher = Dispatchers.Unconfined
        )

        val acceptedDrafts = mutableListOf<String>()
        val editedDrafts = mutableListOf<String>()
        val announcements = mutableListOf<String>()

        val painter = TerminalRowPainter(24, Typeface.MONOSPACE)

        composeRule.setContent {
            BeTermuxComposeTheme(ctx) {
                BlockWorkspaceHost(
                    controller = blockController,
                    sessionProvider = { session },
                    painterProvider = { painter },
                    density = BlockDensity.COMFORT,
                    onTapTarget = { _, _, _ -> },
                    onRequestTerminalInputFocus = {},
                    onToggleDensity = {},
                    onToggleRawPty = {},
                    aiSuggestionsFlow = aiController.suggestions,
                    onAskAiAction = { blk, act, buf, cwd -> aiController.requestForBlock(blk, act, buf, cwd) },
                    onAiAcceptCommand = { id, cmd ->
                        draftStore.setDraft(session.mHandle, cmd)
                        acceptedDrafts.add(cmd)
                        aiController.dismissBlock(id)
                    },
                    onAiEditCommand = { id, cmd ->
                        draftStore.setDraft(session.mHandle, cmd)
                        editedDrafts.add(cmd)
                        aiController.dismissBlock(id)
                    },
                    onAiRetry = { id -> aiController.retryBlock(id) },
                    onAiCancel = { aiController.cancelActive() },
                    onAiDismiss = { id -> aiController.dismissBlock(id) },
                    onOpenAiSettings = {},
                    onAiAnnouncement = { announcements.add(it) }
                )
            }
        }

        // Open block menu -> Propose fix
        composeRule.onNodeWithContentDescription("Block actions for apt install curl").performClick()
        composeRule.onNodeWithText("Propose fix").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        val suggestionNode = composeRule.onNodeWithTag("ai_suggestion_${targetBlock.id}")
        suggestionNode.assertIsDisplayed()
        suggestionNode.assert(
            SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)
        )
        assertTrue(announcements.any { it.contains("Local package index is empty.") })
        composeRule.onNodeWithTag("ai_command_${targetBlock.id}", useUnmergedTree = true).assertIsDisplayed()

        // Accept must insert into draft WITHOUT ever appending/executing into the session transcript
        composeRule.onNodeWithTag("ai_accept_${targetBlock.id}").performClick()
        assertEquals(listOf("apt update && apt install -y curl"), acceptedDrafts)
        assertEquals("apt update && apt install -y curl", draftStore.getDraft(session.mHandle))
        assertEquals(rowsBeforeAi, emulator.mainBuffer.screenEndAbsoluteRowExclusive)
        assertEquals(transcriptBeforeAi, emulator.mainBuffer.getSelectedTextAbsolute(0L, rowsBeforeAi - 1L))
        assertEquals(1, blockController.getCurrentState().blocks.size)
        suggestionNode.assertDoesNotExist()

        // Now test Edit flow via block selection bar
        draftStore.setDraft(session.mHandle, "")
        blockController.toggleBlockSelection(targetBlock.id, session.mHandle)
        composeRule.onNodeWithTag("selection_ai_explain").performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.onNodeWithTag("ai_edit_${targetBlock.id}").performClick()
        assertEquals(listOf("apt update && apt install -y curl"), editedDrafts)
        assertEquals("apt update && apt install -y curl", draftStore.getDraft(session.mHandle))
        assertEquals(rowsBeforeAi, emulator.mainBuffer.screenEndAbsoluteRowExclusive)
        assertEquals(transcriptBeforeAi, emulator.mainBuffer.getSelectedTextAbsolute(0L, rowsBeforeAi - 1L))

        aiController.destroy()
    }
}
