package com.termux.shared.compose

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.termux.shared.theme.NightMode
import com.termux.shared.theme.ThemeUtils
import kotlin.math.roundToInt

/** Self-contained Material3 theme for `termux-shared` Compose surfaces without depending on `:app`. */
@Composable
internal fun SharedComposeTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val nightMode = NightMode.getAppNightMode()
    val darkTheme = ThemeUtils.shouldEnableDarkTheme(context, nightMode.name) ||
        (nightMode == NightMode.SYSTEM && isSystemInDarkTheme())
    val colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        CompositionLocalProvider(LocalContentColor provides colorScheme.onSurface) {
            content()
        }
    }
}

private inline fun <reified T : Context> findContext(context: Context): T? {
    var cur: Context? = context
    while (cur is ContextWrapper) {
        if (cur is T) return cur
        cur = cur.baseContext
    }
    return cur as? T
}

/** Unwraps [ContextWrapper] chains to find the host [ComponentActivity], or `null` otherwise. */
internal fun findComponentActivity(context: Context): ComponentActivity? = findContext(context)

/** Host utilities for Compose dialogs in `termux-shared`, accessible from Java callers. */
object SharedComposeDialogHost {
    /** Returns `true` when [context] belongs to a finishing or destroyed [Activity]. */
    @JvmStatic
    fun isFinishingOrDestroyed(context: Context?): Boolean {
        val activity = context?.let { findContext<Activity>(it) } ?: return false
        return activity.isFinishing || activity.isDestroyed
    }
}

/**
 * Displays a Material3 Compose dialog inside a [Dialog] attached to the host [ComponentActivity].
 * Returns `null` when [context] has no active [ComponentActivity] so callers can fall back cleanly.
 */
@Suppress("DEPRECATION")
internal fun showComposeDialog(
    context: Context,
    cancelable: Boolean = true,
    canceledOnTouchOutside: Boolean = cancelable,
    onDismiss: DialogInterface.OnDismissListener? = null,
    content: @Composable (Dialog) -> Unit,
): Dialog? {
    val owner = findComponentActivity(context) ?: return null
    if (owner.isFinishing || owner.isDestroyed || owner.lifecycle.currentState == Lifecycle.State.DESTROYED) {
        return null
    }

    val dialog = Dialog(owner)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCancelable(cancelable)
    dialog.setCanceledOnTouchOutside(canceledOnTouchOutside)

    val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onDestroy(lifecycleOwner: LifecycleOwner) {
            if (dialog.isShowing) {
                dialog.dismiss()
            }
            lifecycleOwner.lifecycle.removeObserver(this)
        }
    }
    dialog.setOnDismissListener { dismissedDialog ->
        owner.lifecycle.removeObserver(lifecycleObserver)
        onDismiss?.onDismiss(dismissedDialog)
    }

    val composeView = ComposeView(owner).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewTreeViewModelStoreOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent {
            SharedComposeTheme {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp,
                ) {
                    content(dialog)
                }
            }
        }
    }

    dialog.setContentView(composeView)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setGravity(Gravity.CENTER)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    dialog.show()
    owner.lifecycle.addObserver(lifecycleObserver)
    val dm = owner.resources.displayMetrics
    dialog.window?.setLayout(
        minOf((dm.widthPixels * 0.92f).toInt(), (480f * dm.density).roundToInt()),
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    return dialog
}
