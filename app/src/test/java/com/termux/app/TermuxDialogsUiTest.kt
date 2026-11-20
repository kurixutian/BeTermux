package com.termux.app

import android.content.Context
import android.graphics.Color as AndroidColor
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.compose.ComposeDialogHost
import com.betermux.features.BeTermuxSettings
import com.betermux.features.ColorPickerContent
import com.termux.R
import kotlin.math.roundToInt
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class TermuxDialogsUiTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun bootstrapErrorAbortAndRetryInvokeDistinctCallbacks() {
        var aborted = 0
        var retried = 0

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                BootstrapErrorContent(
                    onAbort = { aborted++ },
                    onRetry = { retried++ },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.bootstrap_error_abort)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, aborted)
            assertEquals(0, retried)
        }

        composeRule.onNodeWithText(context.getString(R.string.bootstrap_error_try_again)).performClick()
        composeRule.runOnIdle {
            assertEquals(1, aborted)
            assertEquals(1, retried)
        }
    }

    @Test
    fun colorPickerPreservesHueAndSaturationAcrossZeroBrightness() {
        var selectedColor: Int? = null

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                ColorPickerContent(
                    initialColor = AndroidColor.RED,
                    initialStyle = "hsv",
                    onStyleChange = {},
                    onDismiss = {},
                    onConfirm = { selectedColor = it },
                )
            }
        }

        composeRule.onNodeWithText(context.getString(R.string.color_picker_next)).performClick()

        val brightnessLabel = context.getString(R.string.color_picker_brightness)
        composeRule.onNodeWithContentDescription(brightnessLabel)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("#000000").assertExists()

        composeRule.onNodeWithContentDescription(brightnessLabel)
            .performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(1f) }
        composeRule.waitForIdle()

        composeRule.onNodeWithText(context.getString(R.string.color_picker_ok))
            .performClick()

        composeRule.runOnIdle {
            val picked = requireNotNull(selectedColor)
            assertTrue(AndroidColor.red(picked) > 200)
            assertTrue(AndroidColor.green(picked) < 30)
            assertTrue(AndroidColor.blue(picked) < 30)
        }
    }

    @Test
    fun quickCommandsDialogShowsEmptyStateAndAddRemoveRunEditActions() {
        var items by mutableStateOf<List<String>>(emptyList())
        var canAdd by mutableStateOf(true)
        val runIndices = mutableListOf<Int>()
        val editIndices = mutableListOf<Int>()
        var addCalls = 0
        var removeLastCalls = 0
        var closeCalls = 0

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                QuickCommandsDialogContent(
                    items = items,
                    canAdd = canAdd,
                    onRun = { runIndices += it },
                    onEdit = { editIndices += it },
                    onAdd = { addCalls++ },
                    onRemoveLast = { removeLastCalls++ },
                    onClose = { closeCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("No quick commands yet. Tap + Add to create one.").assertIsDisplayed()
        composeRule.onNodeWithText("− Remove Last").assertDoesNotExist()
        composeRule.onNodeWithText("+ Add").assertIsEnabled().performClick()
        composeRule.onNodeWithText("Close").performClick()

        composeRule.runOnIdle {
            assertEquals(1, addCalls)
            assertEquals(1, closeCalls)
        }

        items = listOf("git status", "npm test")
        canAdd = false
        composeRule.waitForIdle()

        composeRule.onNodeWithText("No quick commands yet. Tap + Add to create one.").assertDoesNotExist()
        composeRule.onNodeWithText("+ Add").assertIsNotEnabled()
        composeRule.onNodeWithText("git status").assertIsDisplayed().performClick()
        composeRule.onNodeWithText("npm test").assertIsDisplayed().performTouchInput { longClick() }
        composeRule.onNodeWithText("− Remove Last").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(listOf(0), runIndices)
            assertEquals(listOf(1), editIndices)
            assertEquals(1, removeLastCalls)
        }
    }

    @Test
    fun editDrawerButtonDialogTrimsOnSaveAndResets() {
        var saved: Pair<String, String>? = null
        var resetCalls = 0
        var cancelCalls = 0

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                EditDrawerButtonDialogContent(
                    indexOneBased = 2,
                    initialName = "Old",
                    initialCmd = "make",
                    onSave = { name, cmd -> saved = name to cmd },
                    onReset = { resetCalls++ },
                    onCancel = { cancelCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("Edit Shortcut 2").assertIsDisplayed()
        composeRule.onNodeWithText("Button label").performTextReplacement("  Build  ")
        composeRule.onNodeWithText("Command").performTextReplacement("  ./gradlew assembleDebug  ")
        composeRule.onNodeWithText("Save").performClick()

        composeRule.runOnIdle { assertEquals("Build" to "./gradlew assembleDebug", saved) }

        composeRule.onNodeWithText("Reset").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, resetCalls)
            assertEquals(1, cancelCalls)
        }
    }

    @Test
    fun makeScriptDialogReturnsSelectedIndices() {
        var savedIndices: IntArray? = null
        var cancelCalls = 0

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                MakeScriptDialogContent(
                    entries = listOf("echo one", "ls -la", "pwd"),
                    onSave = { savedIndices = it },
                    onCancel = { cancelCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("echo one").performClick()
        composeRule.onNodeWithText("pwd").performClick()
        composeRule.onNodeWithText("Save Script").performClick()

        composeRule.runOnIdle {
            assertArrayEquals(intArrayOf(0, 2), savedIndices)
            assertEquals(0, cancelCalls)
        }
    }

    @Test
    fun confirmAndActionListDialogsInvokeCallbacks() {
        var stage by mutableIntStateOf(0)
        var confirmCalls = 0
        var dismissCalls = 0
        var okFallbackCalls = 0
        val clickedIndices = mutableListOf<Int>()
        val longClickedIndices = mutableListOf<Int>()

        composeRule.setContent {
            BeTermuxComposeTheme(context) {
                when (stage) {
                    0 -> ConfirmDialogContent(
                        title = "Confirm Reset",
                        message = "Reset workspace?",
                        positiveLabel = "Confirm",
                        negativeLabel = "Cancel",
                        showAlertIcon = true,
                        onDismiss = { dismissCalls++ },
                        onConfirm = { confirmCalls++ },
                    )
                    1 -> ConfirmDialogContent(
                        title = null,
                        message = "Notice only",
                        positiveLabel = null,
                        negativeLabel = null,
                        showAlertIcon = false,
                        onDismiss = { okFallbackCalls++ },
                        onConfirm = { confirmCalls++ },
                    )
                    else -> ActionListDialogContent(
                        title = "Session Actions",
                        items = listOf("Rename", "Close"),
                        onItemClick = { clickedIndices += it },
                        onItemLongClick = { longClickedIndices += it },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Confirm Reset").assertIsDisplayed()
        composeRule.onNodeWithText("Reset workspace?").assertIsDisplayed()
        composeRule.onNodeWithText("Confirm").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            assertEquals(1, confirmCalls)
            assertEquals(1, dismissCalls)
        }

        stage = 1
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Notice only").assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(android.R.string.ok)).performClick()
        composeRule.runOnIdle { assertEquals(1, okFallbackCalls) }

        stage = 2
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Session Actions").assertIsDisplayed()
        composeRule.onNodeWithText("Rename").performClick()
        composeRule.onNodeWithText("Close").performTouchInput { longClick() }
        composeRule.runOnIdle {
            assertEquals(listOf(0), clickedIndices)
            assertEquals(listOf(1), longClickedIndices)
        }
    }

    @Test
    @Config(sdk = [28], qualifiers = "w800dp-h600dp")
    fun composeDialogHostCapsWidthTo480DpAndDismissesOnOwnerDestroy() {
        val controller: ActivityController<ComponentActivity> =
            Robolectric.buildActivity(ComponentActivity::class.java).setup()
        val owner = controller.get()
        var dismissCalls = 0

        val dialog = ComposeDialogHost.show(owner, onDismiss = { dismissCalls++ }) {
            Text("Host lifecycle test")
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertNotNull(dialog)
        val shownDialog = requireNotNull(dialog)
        assertTrue(shownDialog.isShowing)

        val dm = owner.resources.displayMetrics
        val maxCardWidthPx = (480f * dm.density * (BeTermuxSettings.getUiZoomPercent(owner) / 100f)).roundToInt()
        assertTrue(dm.widthPixels > maxCardWidthPx)
        val windowWidth = requireNotNull(shownDialog.window).attributes.width
        assertTrue(windowWidth in 1..maxCardWidthPx)

        controller.pause().stop().destroy()
        Shadows.shadowOf(Looper.getMainLooper()).idle()

        assertEquals(Lifecycle.State.DESTROYED, owner.lifecycle.currentState)
        assertFalse(shownDialog.isShowing)
        assertEquals(1, dismissCalls)
    }
}
