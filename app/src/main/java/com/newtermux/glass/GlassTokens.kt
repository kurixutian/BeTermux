package com.newtermux.glass

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Design tokens for BeTermux's Liquid Glass system.
 *
 * Tuned for an obsidian-first terminal workspace: low-glare dark glass body,
 * adaptive blur/tint scaling by surface size, subtle specular rim highlight,
 * and gentle spring physics for press feedback.
 */
object GlassTokens {

    /** Recording scale for page backdrop (1/3 = 9x fewer pixels processed). */
    const val BACKDROP_RESOLUTION_SCALE = 0.33f

    /** Size thresholds (dp, smaller dimension). */
    const val THIN_MAX_DP = 56f
    const val LARGE_MIN_DP = 200f

    /** Blur radius (dp) interpolated between thin bars/chips and large sheets/cards. */
    const val THIN_BLUR_DP = 6f
    const val LARGE_BLUR_DP = 16f

    /** Base obsidian backdrop and glass body tints. */
    val BACKGROUND = Color(0xFF0D1117)
    val SURFACE_BASE = Color(0xFF141820)
    val GLASS_TINT = Color(0xFF1C1C24).copy(alpha = 0.46f)
    const val LARGE_TINT_ALPHA = 0.64f

    /** Vibrancy color matrix adjustments for backdrop sampling. */
    const val VIBRANCY_SATURATION = 1.35f
    const val VIBRANCY_BRIGHTNESS = -0.02f

    /** Specular rim highlight (vertical top-to-bottom sheen). */
    val HIGHLIGHT_WIDTH = 0.85.dp
    const val HIGHLIGHT_TOP_ALPHA = 0.34f
    const val HIGHLIGHT_BOTTOM_ALPHA = 0.08f

    /** Soft drop shadow outside elevated glass. */
    val SHADOW_COLOR = Color.Black.copy(alpha = 0.30f)

    /** API 33+ AGSL lens refraction parameters. */
    const val THIN_LENS_HEIGHT_DP = 18f
    const val LARGE_LENS_HEIGHT_DP = 28f
    const val THIN_LENS_AMOUNT_MULT = 1.45f
    const val LARGE_LENS_AMOUNT_MULT = 1.10f
    const val CHROMATIC_ABERRATION = true
    const val PRESS_BULGE = 0.35f

    /** Press interaction spring physics. */
    const val PRESS_SCALE = 0.97f
    val PRESS_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.78f, stiffness = 520f)
    val RELEASE_SPRING: SpringSpec<Float> = spring(dampingRatio = 0.72f, stiffness = 360f)

    /** Standard shapes for glass surfaces. */
    val ShapeCard = RoundedCornerShape(16.dp)
    val ShapePill = RoundedCornerShape(28.dp)
    val ShapeChip = RoundedCornerShape(12.dp)
    val ShapeSheet = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)

    fun blurForSize(minDimensionDp: Float): Float {
        val t = ((minDimensionDp - THIN_MAX_DP) / (LARGE_MIN_DP - THIN_MAX_DP)).coerceIn(0f, 1f)
        return THIN_BLUR_DP + (LARGE_BLUR_DP - THIN_BLUR_DP) * t
    }

    fun tintAlphaForSize(minDimensionDp: Float): Float {
        val baseAlpha = GLASS_TINT.alpha
        val t = ((minDimensionDp - THIN_MAX_DP) / (LARGE_MIN_DP - THIN_MAX_DP)).coerceIn(0f, 1f)
        return baseAlpha + (LARGE_TINT_ALPHA - baseAlpha) * t
    }

    fun lensParamsForSize(minDimensionDp: Float): Pair<Float, Float> {
        val t = ((minDimensionDp - THIN_MAX_DP) / (LARGE_MIN_DP - THIN_MAX_DP)).coerceIn(0f, 1f)
        val height = THIN_LENS_HEIGHT_DP + (LARGE_LENS_HEIGHT_DP - THIN_LENS_HEIGHT_DP) * t
        val mult = THIN_LENS_AMOUNT_MULT + (LARGE_LENS_AMOUNT_MULT - THIN_LENS_AMOUNT_MULT) * t
        return height to (height * mult)
    }
}
