package com.betermux.compose

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.betermux.features.BeTermuxSettings
import com.betermux.theme.LocalWarpPalette
import kotlin.math.roundToInt

/** Shared Compose dialog host for Java and Kotlin callers, matching BeTermux surface styling. */
object ComposeDialogHost {
    fun findComponentActivity(context: Context): ComponentActivity? {
        var cur: Context? = context
        while (cur is ContextWrapper) {
            if (cur is ComponentActivity) return cur
            cur = cur.baseContext
        }
        return cur as? ComponentActivity
    }

    @Suppress("DEPRECATION")
    fun show(
        context: Context,
        cancelable: Boolean = true,
        canceledOnTouchOutside: Boolean = cancelable,
        widthFraction: Float = 0.92f,
        onDismiss: DialogInterface.OnDismissListener? = null,
        content: @Composable (dialog: Dialog) -> Unit,
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
                if (dialog.isShowing) dialog.dismiss()
            }
        }
        dialog.setOnDismissListener { dismissed ->
            owner.lifecycle.removeObserver(lifecycleObserver)
            onDismiss?.onDismiss(dismissed)
        }
        owner.lifecycle.addObserver(lifecycleObserver)
        val composeView = ComposeView(owner).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BeTermuxComposeTheme(owner) {
                    val palette = LocalWarpPalette.current
                    Surface(
                        modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = palette.surface,
                        border = BorderStroke(1.dp, palette.borderSubtle),
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
        val dm = owner.resources.displayMetrics
        val maxCardWidthPx = (480f * dm.density * (BeTermuxSettings.getUiZoomPercent(owner) / 100f)).roundToInt()
        dialog.window?.setLayout(
            minOf((dm.widthPixels * widthFraction).toInt(), maxCardWidthPx),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
        return dialog
    }
}
