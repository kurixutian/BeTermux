package com.newtermux.workflows

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import com.newtermux.compose.NewTermuxComposeTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class WorkflowParserAndStoreTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun yamlFrontMatterRoundTripAcrossRestart() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo1 = WorkflowRepository(ctx)
        repo1.listWorkflows().forEach { repo1.deleteWorkflow(it.id) }

        val original = Workflow(
            id = "deploy_api",
            name = "Deploy API Service",
            description = "Roll out container to cluster",
            tags = listOf("docker", "deploy"),
            author = "devops",
            version = 2,
            commandTemplate = "kubectl rollout restart deploy/{{service}} -n {{env=staging}}"
        )

        val saved = repo1.saveWorkflow(original)
        val tokens = saved.extractTokens()
        assertEquals(listOf("service", "env"), tokens.map { it.name })
        assertEquals("", tokens[0].defaultValue)
        assertEquals("staging", tokens[1].defaultValue)

        assertEquals(
            "kubectl rollout restart deploy/billing -n staging",
            saved.renderCommand(mapOf("service" to "billing"))
        )

        // Simulate process death / restart with a fresh repository instance
        val repo2 = WorkflowRepository(ctx)
        val reloaded = repo2.getWorkflow(saved.id)
        assertNotNull(reloaded)
        assertEquals("Deploy API Service", reloaded!!.name)
        assertEquals("Roll out container to cluster", reloaded.description)
        assertEquals(listOf("docker", "deploy"), reloaded.tags)
        assertEquals(2, reloaded.version)
        assertEquals(original.commandTemplate, reloaded.commandTemplate)

        assertTrue(repo2.deleteWorkflow(saved.id))
        assertNull(repo2.getWorkflow(saved.id))
    }

    @Test
    fun h1FallbackMarkdownWithoutFrontMatterParses() {
        val fallbackMd = """
            # Tail Nginx Logs

            Follow errors cleanly without wrapping.

            ```sh
            tail -n {{lines=100}} /var/log/nginx/error.log
            ```
        """.trimIndent()

        val parsed = WorkflowMarkdownParser.parse(fallbackMd, fallbackId = "tail_nginx")
        assertNotNull(parsed)
        assertEquals("Tail Nginx Logs", parsed!!.name)
        assertEquals("Follow errors cleanly without wrapping.", parsed.description)
        assertEquals("tail -n {{lines=100}} /var/log/nginx/error.log", parsed.commandTemplate)
        assertEquals(
            "tail -n 250 /var/log/nginx/error.log",
            parsed.renderCommand(mapOf("lines" to "250"))
        )
    }

    @Test
    fun shareIntentExportAndIncomingMarkdownImportRoundTrip() {
        val ctx = RuntimeEnvironment.getApplication()
        val repo = WorkflowRepository(ctx)
        repo.listWorkflows().forEach { repo.deleteWorkflow(it.id) }

        val wf = repo.saveWorkflow(
            Workflow(
                id = "git_clean_branch",
                name = "Prune Merged Branches",
                description = "Delete local branches merged into main",
                tags = listOf("git"),
                commandTemplate = "git branch --merged {{base=main}} | grep -v {{base=main}} | xargs -r git branch -d"
            )
        )

        val chooser = WorkflowShareHelper.buildShareIntent(ctx, wf, repo)
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val innerSend = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(innerSend)
        assertEquals(Intent.ACTION_SEND, innerSend!!.action)
        assertEquals("text/markdown", innerSend.type)
        val exportedText = innerSend.getStringExtra(Intent.EXTRA_TEXT)
        assertNotNull(exportedText)

        // Simulate receiving shared markdown bytes via FileReceiverActivity
        val incomingBytes = exportedText!!.toByteArray(StandardCharsets.UTF_8)
        val imported = WorkflowShareHelper.inspectIncomingBytes(
            bytes = incomingBytes,
            fileName = "Prune_Merged_Branches.md",
            mimeType = "text/markdown"
        )
        assertNotNull(imported)
        assertEquals("Prune Merged Branches", imported!!.name)
        assertEquals(wf.commandTemplate, imported.commandTemplate)

        // Non-workflow binary/text without front-matter or fenced shell should return null
        val plainNote = "Remember to buy milk".toByteArray(StandardCharsets.UTF_8)
        assertNull(WorkflowShareHelper.inspectIncomingBytes(plainNote, "note.txt", "text/plain"))

        // Oversized stream stops at MAX_IMPORT_BYTES + 1 and is flagged oversized
        val oversized = ByteArray(300 * 1024) { 'a'.code.toByte() }
        val bounded = WorkflowShareHelper.readBoundedBytes(oversized.inputStream())
        assertEquals(256 * 1024 + 1, bounded.size)
        assertTrue(WorkflowShareHelper.isOversized(bounded))
        assertNull(WorkflowShareHelper.inspectIncomingBytes(bounded, "big.md", "text/markdown"))
    }

    @Test
    fun workflowSheetOverlayPickerAndParamFillFlow() {
        val ctx = RuntimeEnvironment.getApplication()
        val sample = Workflow(
            id = "ssh_host",
            name = "SSH Tunnel",
            commandTemplate = "ssh -L {{port=8080}}:localhost:{{port=8080}} {{host}}"
        )
        var insertedCommand: String? = null
        composeRule.setContent {
            NewTermuxComposeTheme(ctx) {
                var mode by remember { mutableStateOf<WorkflowSheetMode>(WorkflowSheetMode.Picker) }
                WorkflowSheetOverlay(
                    mode = mode,
                    workflows = listOf(sample),
                    onDismiss = { mode = WorkflowSheetMode.Hidden },
                    onOpenNew = {
                        mode = WorkflowSheetMode.Editor(
                            initialWorkflow = Workflow(id = "new_1", name = "", commandTemplate = ""),
                            isNew = true
                        )
                    },
                    onSelectForInsert = { mode = WorkflowSheetMode.ParamFill(it) },
                    onSaveWorkflow = {},
                    onDeleteWorkflow = {},
                    onShareWorkflow = {},
                    onEditWorkflow = { mode = WorkflowSheetMode.Editor(it, isNew = false) },
                    onInsertRenderedCommand = { insertedCommand = it; mode = WorkflowSheetMode.Hidden }
                )
            }
        }

        composeRule.onNodeWithTag("workflow_picker_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("workflow_use_ssh_host").performClick()
        composeRule.onNodeWithTag("workflow_param_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("workflow_token_host").performScrollTo().performTextInput("prod-box")
        composeRule.onNodeWithTag("workflow_insert_rendered").performScrollTo().performClick()

        assertEquals("ssh -L 8080:localhost:8080 prod-box", insertedCommand)
    }

    @Test
    fun workflowSheetOverlayEditorFlow() {
        val ctx = RuntimeEnvironment.getApplication()
        var savedWorkflow: Workflow? = null

        composeRule.setContent {
            NewTermuxComposeTheme(ctx) {
                WorkflowSheetOverlay(
                    mode = WorkflowSheetMode.Editor(
                        Workflow(id = "draft_1", name = "", commandTemplate = ""),
                        isNew = true
                    ),
                    workflows = emptyList(),
                    onDismiss = {},
                    onOpenNew = {},
                    onSelectForInsert = {},
                    onSaveWorkflow = { savedWorkflow = it },
                    onDeleteWorkflow = {},
                    onShareWorkflow = {},
                    onEditWorkflow = {},
                    onInsertRenderedCommand = {}
                )
            }
        }

        composeRule.onNodeWithTag("workflow_editor_sheet").assertIsDisplayed()
        composeRule.onNodeWithTag("workflow_editor_name").performTextInput("Check Disk")
        composeRule.onNodeWithTag("workflow_editor_command").performScrollTo().performTextInput("df -h {{mount=/}}")
        composeRule.onNodeWithTag("workflow_editor_save").performScrollTo().performClick()

        assertNotNull(savedWorkflow)
        assertEquals("Check Disk", savedWorkflow!!.name)
        assertEquals("df -h {{mount=/}}", savedWorkflow!!.commandTemplate)
    }
}
