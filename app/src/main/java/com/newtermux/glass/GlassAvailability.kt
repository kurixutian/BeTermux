package com.newtermux.glass

import android.content.Context
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext

/**
 * Device rendering capability before user preference is applied.
 */
enum class GlassAvailability {
    /** Android 13+ (API 33+) without "Remove animations": AGSL lens + RenderEffect blur. */
    LENS,
    /** Android 12/12L (API 31-32) without "Remove animations": RenderEffect live blur. */
    LIVE_BLUR,
    /** API < 31 or "Remove animations" enabled: translucent frost fallback. */
    FROST,
}

/**
 * Final resolved rendering path for a glass surface.
 */
enum class GlassPath {
    LENS,
    LIVE_BLUR,
    FROST,
}

/** Whether the user has enabled Liquid Glass in Settings (default true). */
val LocalGlassEnabled = staticCompositionLocalOf { true }

/**
 * Persistence helper for the Liquid Glass user toggle.
 */
object GlassPreferences {
    private const val PREFS_NAME = "newtermux_theme"
    private const val KEY_LIQUID_GLASS = "liquid_glass_enabled"

    fun isGlassEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIQUID_GLASS, true)

    fun setGlassEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LIQUID_GLASS, enabled).apply()
    }
}

@Composable
fun rememberReduceMotion(context: Context = LocalContext.current): Boolean = remember(context) {
    try {
        Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        ) == 0f
    } catch (_: Exception) {
        false
    }
}

@Composable
fun rememberGlassAvailability(): GlassAvailability {
    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion(context)
    return remember(reduceMotion) {
        when {
            reduceMotion -> GlassAvailability.FROST
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> GlassAvailability.LENS
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> GlassAvailability.LIVE_BLUR
            else -> GlassAvailability.FROST
        }
    }
}

fun resolveGlassPath(
    availability: GlassAvailability,
    userSwitchOn: Boolean = true,
): GlassPath = when {
    !userSwitchOn -> GlassPath.FROST
    availability == GlassAvailability.LENS -> GlassPath.LENS
    availability == GlassAvailability.LIVE_BLUR -> GlassPath.LIVE_BLUR
    else -> GlassPath.FROST
}
