package com.newtermux.compose

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.newtermux.features.NewTermuxColorTheme
import com.newtermux.features.NewTermuxSettings
import com.newtermux.features.NewTermuxTheme
import com.newtermux.theme.CuratedThemes
import com.newtermux.theme.LocalWarpPalette

/** Observable invalidation counter so XML/Java theme changes recompose Compose screens live. */
object WarpThemeState {
    var version by mutableIntStateOf(0)
        private set

    @JvmStatic
    fun notifyChanged() {
        version++
    }
}

private val WarpTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 19.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.4.sp,
    ),
)

/**
 * Shared Warp-inspired Compose theme for all BeTermux overlays, tools, and activities.
 * Derives surfaces and text hierarchy from the active curated palette
 * ([CuratedThemes]) and binds [NewTermuxTheme.getAccentColor] as primary accent.
 */
@Composable
fun NewTermuxComposeTheme(
    context: Context,
    themeKey: String? = null,
    accentOverride: Int? = null,
    content: @Composable () -> Unit,
) {
    WarpThemeState.version
    val resolvedKey = themeKey ?: NewTermuxColorTheme.getCurrentTheme(context)
    val palette = CuratedThemes.resolve(resolvedKey)
    val accent = Color(accentOverride ?: NewTermuxTheme.getAccentColor(context))
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF121210) else palette.textPrimary
    val onContainer = if (accent.luminance() > 0.45f) Color(0xFF1A1917) else palette.textPrimary

    val colorScheme = darkColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.18f),
        onPrimaryContainer = onContainer,
        secondary = palette.info,
        onSecondary = Color(0xFF121210),
        tertiary = palette.success,
        background = palette.background,
        onBackground = palette.textPrimary,
        surface = palette.surface,
        onSurface = palette.textPrimary,
        surfaceVariant = palette.surfaceElevated,
        onSurfaceVariant = palette.textSecondary,
        surfaceContainerLowest = palette.background,
        surfaceContainerLow = palette.surface,
        surfaceContainer = palette.surfaceElevated,
        surfaceContainerHigh = palette.surfaceInteractive,
        outline = palette.borderStrong,
        outlineVariant = palette.borderSubtle,
        error = palette.error,
        onError = Color(0xFF121210),
    )

    val zoomPercent = NewTermuxSettings.getUiZoomPercent(context)
    val zoomedDensity = UiZoomMath.computeZoomedDensity(LocalDensity.current, zoomPercent)

    CompositionLocalProvider(
        LocalWarpPalette provides palette,
        LocalDensity provides zoomedDensity,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = WarpTypography,
            content = content,
        )
    }
}

object UiZoomMath {
    fun clampZoomPercent(percent: Int): Int =
        percent.coerceIn(NewTermuxSettings.UI_ZOOM_MIN_PERCENT, NewTermuxSettings.UI_ZOOM_MAX_PERCENT)

    /**
     * Applies UI zoom (85%..130%) to layout density while preserving OS accessibility {@code fontScale}.
     */
    fun computeZoomedDensity(base: Density, zoomPercent: Int): Density {
        val factor = clampZoomPercent(zoomPercent) / 100f
        return Density(
            density = base.density * factor,
            fontScale = base.fontScale
        )
    }
}
