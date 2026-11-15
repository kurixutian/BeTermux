package com.newtermux.theme

import androidx.compose.ui.graphics.Color
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Semantic color tokens for BeTermux's curated Warp-inspired design system:
 * warm near-black charcoal surfaces, off-white typography, crisp 1dp structural
 * borders, and zero translucency or blur dependencies.
 */
data class WarpPalette(
    val key: String,
    val displayName: String,
    val subtitle: String,
    val isHighContrast: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceInteractive: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val borderSubtle: Color,
    val borderStrong: Color,
    val defaultAccent: Color,
    val success: Color,
    val warning: Color,
    val error: Color,
    val info: Color,
)

object CuratedThemes {

    const val KEY_WARM_DARK = "warm_dark"
    const val KEY_SOLARIZED_DARK = "solarized_dark"
    const val KEY_HIGH_CONTRAST = "high_contrast"
    const val KEY_CUSTOM = "custom"

    val WarmDark = WarpPalette(
        key = KEY_WARM_DARK,
        displayName = "Warm Dark",
        subtitle = "Charcoal & warm sand",
        isHighContrast = false,
        background = Color(0xFF121210),
        surface = Color(0xFF1A1917),
        surfaceElevated = Color(0xFF22201D),
        surfaceInteractive = Color(0xFF2B2824),
        textPrimary = Color(0xFFFAF9F6),
        textSecondary = Color(0xFFACA79E),
        textMuted = Color(0xFF8C877E),
        borderSubtle = Color(0xFF2D2B27),
        borderStrong = Color(0xFF423E38),
        defaultAccent = Color(0xFFD49B54),
        success = Color(0xFF5FB878),
        warning = Color(0xFFE0A94F),
        error = Color(0xFFE56B5E),
        info = Color(0xFF6BA6E8),
    )

    val SolarizedDark = WarpPalette(
        key = KEY_SOLARIZED_DARK,
        displayName = "Solarized Dark",
        subtitle = "Classic calibrated teal-slate",
        isHighContrast = false,
        background = Color(0xFF002B36),
        surface = Color(0xFF073642),
        surfaceElevated = Color(0xFF0E414E),
        surfaceInteractive = Color(0xFF164D5C),
        textPrimary = Color(0xFFFAF9F6),
        textSecondary = Color(0xFFA3B3B3),
        textMuted = Color(0xFF8A9A9A),
        borderSubtle = Color(0xFF184A57),
        borderStrong = Color(0xFF296270),
        defaultAccent = Color(0xFF268BD2),
        success = Color(0xFF859900),
        warning = Color(0xFFB58900),
        error = Color(0xFFDC322F),
        info = Color(0xFF2AA198),
    )

    val HighContrast = WarpPalette(
        key = KEY_HIGH_CONTRAST,
        displayName = "High Contrast",
        subtitle = "True black AAA legibility",
        isHighContrast = true,
        background = Color(0xFF000000),
        surface = Color(0xFF0A0A0A),
        surfaceElevated = Color(0xFF141414),
        surfaceInteractive = Color(0xFF1F1F1F),
        textPrimary = Color(0xFFFAF9F6),
        textSecondary = Color(0xFFDAD9D4),
        textMuted = Color(0xFFB5B4AE),
        borderSubtle = Color(0xFF707070),
        borderStrong = Color(0xFFB8B8B8),
        defaultAccent = Color(0xFF66B2FF),
        success = Color(0xFF5CE67E),
        warning = Color(0xFFFFD452),
        error = Color(0xFFFF6B6B),
        info = Color(0xFF70C8FF),
    )

    val ALL: List<WarpPalette> = listOf(WarmDark, SolarizedDark, HighContrast)

    @JvmStatic
    fun normalizeKey(key: String?): String = when (key) {
        KEY_SOLARIZED_DARK -> KEY_SOLARIZED_DARK
        KEY_HIGH_CONTRAST -> KEY_HIGH_CONTRAST
        KEY_CUSTOM -> KEY_CUSTOM
        else -> KEY_WARM_DARK
    }

    fun resolve(key: String?): WarpPalette = when (normalizeKey(key)) {
        KEY_SOLARIZED_DARK -> SolarizedDark
        KEY_HIGH_CONTRAST -> HighContrast
        else -> WarmDark
    }

    /** WCAG 2.x relative luminance for an opaque sRGB color. */
    fun relativeLuminance(color: Color): Double {
        fun linearize(c: Float): Double {
            val d = c.toDouble()
            return if (d <= 0.04045) d / 12.92 else ((d + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)
    }

    /** WCAG 2.x contrast ratio in `[1.0, 21.0]`. */
    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = relativeLuminance(foreground)
        val l2 = relativeLuminance(background)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }
}
