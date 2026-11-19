package com.betermux.theme

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

/**
 * Observes OS animation scale and accessibility settings so Warp transitions
 * shorten or disable cleanly when reduced motion is preferred.
 */
object MotionPreferences {

    @JvmStatic
    fun shouldReduceMotion(context: Context): Boolean {
        val animScale = try {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1.0f,
            )
        } catch (_: SecurityException) {
            1.0f
        }
        if (animScale == 0f) return true

        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return am != null && am.isEnabled && am.isTouchExplorationEnabled
    }
}

@Composable
fun rememberReduceMotion(context: Context = LocalContext.current): Boolean {
    var reduced by remember(context) { mutableStateOf(MotionPreferences.shouldReduceMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = MotionPreferences.shouldReduceMotion(context)
            }
        }
        val uri = Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE)
        context.contentResolver.registerContentObserver(uri, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return reduced
}
