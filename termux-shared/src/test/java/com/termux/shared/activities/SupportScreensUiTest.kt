package com.termux.shared.activities

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.LinkAnnotation
import com.termux.shared.R
import com.termux.shared.compose.SharedComposeDialogHost
import com.termux.shared.compose.SharedComposeTheme
import com.termux.shared.compose.findComponentActivity
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.interact.SharedMessageDialogContent
import com.termux.shared.interact.linkifyMessage
import com.termux.shared.termux.interact.SharedTextInputDialogContent
import com.termux.shared.termux.interact.TextInputDialogUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SupportScreensUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reportComposeScreenShowsTitleOverflowMenuAndRespectsCanSave() {
        var canSave by mutableStateOf(false)
        var shareClicks = 0
        var copyClicks = 0
        var saveClicks = 0

        composeRule.setContent {
            SharedComposeTheme {
                ReportComposeScreen(
                    title = "Crash Report",
                    canSave = canSave,
                    onShare = { shareClicks++ },
                    onCopy = { copyClicks++ },
                    onSave = { saveClicks++ },
                ) { modifier ->
                    Box(modifier = modifier.fillMaxSize()) {
                        Text("Report Body")
                    }
                }
            }
        }

        composeRule.onNodeWithText("Crash Report").assertIsDisplayed()
        composeRule.onNodeWithText("Report Body").assertIsDisplayed()

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Copy").assertIsDisplayed()
        composeRule.onNodeWithText("Save To File").assertIsDisplayed().assertIsNotEnabled()

        composeRule.onNodeWithText("Share").performClick()
        composeRule.runOnIdle { assertEquals(1, shareClicks) }

        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Copy").performClick()
        composeRule.runOnIdle { assertEquals(1, copyClicks) }

        composeRule.runOnIdle { canSave = true }
        composeRule.onNodeWithContentDescription("More options").performClick()
        composeRule.onNodeWithText("Save To File").assertIsEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, saveClicks) }
    }

    @Test
    fun sharedMessageDialogContentRendersAndInvokesButtons() {
        var positiveClicks = 0
        var negativeClicks = 0

        composeRule.setContent {
            SharedComposeTheme {
                SharedMessageDialogContent(
                    titleText = "Warning",
                    messageText = "Visit https://example.com or email help@example.com for details.",
                    positiveText = "Yes",
                    negativeText = "No",
                    onPositive = { positiveClicks++ },
                    onNegative = { negativeClicks++ },
                )
            }
        }

        composeRule.onNodeWithText("Warning").assertIsDisplayed()
        composeRule.onNodeWithText("Visit https://example.com or email help@example.com for details.")
            .assertIsDisplayed()

        composeRule.onNodeWithText("Yes").performClick()
        composeRule.onNodeWithText("No").performClick()
        composeRule.runOnIdle {
            assertEquals(1, positiveClicks)
            assertEquals(1, negativeClicks)
        }
    }

    @Test
    fun sharedMessageDialogContentHidesNegativeButtonWhenNull() {
        composeRule.setContent {
            SharedComposeTheme {
                SharedMessageDialogContent(
                    titleText = "Notice",
                    messageText = "Operation finished.",
                    positiveText = "OK",
                    negativeText = null,
                    onPositive = {},
                    onNegative = {},
                )
            }
        }

        composeRule.onNodeWithText("Notice").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
    }

    @Test
    fun sharedTextInputDialogContentEditsTextAndInvokesButtons() {
        var positiveText: String? = null
        var neutralText: String? = null
        var negativeText: String? = null

        composeRule.setContent {
            SharedComposeTheme {
                SharedTextInputDialogContent(
                    titleText = "File received",
                    initialText = "draft.txt",
                    positiveText = "Edit",
                    neutralText = "Open directory",
                    negativeText = "Cancel",
                    onPositive = { positiveText = it },
                    onNeutral = { neutralText = it },
                    onNegative = { negativeText = it },
                )
            }
        }

        composeRule.onNodeWithText("File received").assertIsDisplayed()
        composeRule.onNodeWithText("draft.txt").assertIsDisplayed()

        composeRule.onNodeWithText("draft.txt").performTextReplacement("updated.txt")
        composeRule.onNodeWithText("Edit").performClick()
        composeRule.onNodeWithText("Open directory").performClick()
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.runOnIdle {
            assertEquals("updated.txt", positiveText)
            assertEquals("updated.txt", neutralText)
            assertEquals("updated.txt", negativeText)
        }
    }

    @Test
    fun findComponentActivityReturnsNullForApplicationContext() {
        assertNull(findComponentActivity(RuntimeEnvironment.getApplication()))
    }

    @Test
    fun linkifyMessagePreservesAtUrlsAndEmailsWithoutLinkifyingFilePaths() {
        val text = "Open https://user@example.com/a or mail help@example.com not /tmp/script.sh"
        val annotated = linkifyMessage(text, Color.Blue)
        val links = annotated.getLinkAnnotations(0, text.length)

        assertEquals(2, links.size)
        assertEquals("https://user@example.com/a", text.substring(links[0].start, links[0].end))
        assertEquals("https://user@example.com/a", (links[0].item as LinkAnnotation.Url).url)
        assertEquals("help@example.com", text.substring(links[1].start, links[1].end))
        assertEquals("mailto:help@example.com", (links[1].item as LinkAnnotation.Url).url)
    }

    @Test
    fun textInputDialogRunsCallbackBeforeOnDismissAndGuardsFinishingActivity() {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val events = mutableListOf<String>()
        var finishOnDismiss = true

        TextInputDialogUtils.textInput(
            activity,
            R.string.title_report_text,
            "initial.txt",
            android.R.string.ok,
            { text ->
                // Simulate FileReceiverActivity clearing mFinishOnDismissNameDialog during callback.
                finishOnDismiss = false
                events += "positive:$text"
            },
            0,
            null,
            0,
            null,
        ) {
            events += "dismiss:$finishOnDismiss"
        }

        Shadows.shadowOf(Looper.getMainLooper()).idle()
        composeRule.onNodeWithText(activity.getString(android.R.string.ok)).performClick()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(listOf("positive:initial.txt", "dismiss:false"), events)

        activity.finish()
        assertTrue(SharedComposeDialogHost.isFinishingOrDestroyed(activity))
        TextInputDialogUtils.textInput(
            activity,
            R.string.title_report_text,
            "ignored.txt",
            android.R.string.ok,
            { events += "unexpected" },
            0,
            null,
            0,
            null,
        ) {
            events += "unexpected-dismiss"
        }
        assertEquals(2, events.size)
    }

    @Test
    fun messageDialogDismissesOnDestroyAndGuardsFinishingActivity() {
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val activity = controller.get()
        var dismissCount = 0

        MessageDialogUtils.showMessage(activity, "Title", "Body") { dismissCount++ }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        controller.pause().stop().destroy()
        Shadows.shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, dismissCount)
        assertTrue(SharedComposeDialogHost.isFinishingOrDestroyed(activity))

        MessageDialogUtils.showMessage(activity, "Title", "Body") { dismissCount++ }
        assertEquals(1, dismissCount)

        val finishingActivity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        finishingActivity.finish()
        assertTrue(SharedComposeDialogHost.isFinishingOrDestroyed(finishingActivity))
        MessageDialogUtils.showMessage(finishingActivity, "Title", "Body") { dismissCount++ }
        assertEquals(1, dismissCount)
    }
}
