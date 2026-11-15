package com.newtermux.glass

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeColorFilter
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.newtermux.glass.GlassTokens.GLASS_TINT
import com.newtermux.glass.GlassTokens.HIGHLIGHT_BOTTOM_ALPHA
import com.newtermux.glass.GlassTokens.HIGHLIGHT_TOP_ALPHA
import com.newtermux.glass.GlassTokens.HIGHLIGHT_WIDTH
import com.newtermux.glass.GlassTokens.PRESS_BULGE
import com.newtermux.glass.GlassTokens.PRESS_SCALE
import com.newtermux.glass.GlassTokens.PRESS_SPRING
import com.newtermux.glass.GlassTokens.RELEASE_SPRING
import com.newtermux.glass.GlassTokens.SHADOW_COLOR
import com.newtermux.glass.GlassTokens.VIBRANCY_BRIGHTNESS
import com.newtermux.glass.GlassTokens.VIBRANCY_SATURATION
import com.newtermux.glass.GlassTokens.blurForSize
import com.newtermux.glass.GlassTokens.lensParamsForSize
import com.newtermux.glass.GlassTokens.tintAlphaForSize
import kotlin.math.min

/**
 * Liquid Glass surface modifier with progressive enhancement:
 * - API 33+: RenderEffect blur + AGSL meniscus lens refraction with chromatic aberration
 * - API 31-32: RenderEffect live blur + vibrancy
 * - API < 31 / Reduce Motion / Glass disabled: translucent obsidian frost fallback
 *
 * Designed for zero-allocation draw frames via [drawWithCache] and deferred
 * state reads inside [graphicsLayer] for press spring physics.
 */
@Composable
fun Modifier.glassSurface(
    shape: Shape = GlassTokens.ShapeCard,
    elevation: Dp = 4.dp,
    tint: Color = GLASS_TINT,
    borderColor: Color? = null,
    enableLens: Boolean = true,
    enableHighlight: Boolean = true,
    enableShadow: Boolean = true,
    sampleBackdrop: Boolean = true,
    interactionSource: MutableInteractionSource? = null,
    onClick: (() -> Unit)? = null,
): Modifier {
    val density = LocalDensity.current
    val availability = rememberGlassAvailability()
    val glassEnabled = LocalGlassEnabled.current
    val path = resolveGlassPath(availability, glassEnabled)
    val pageBackdrop = if (sampleBackdrop) LocalGlassPageBackdrop.current else null

    val resolvedInteraction = interactionSource ?: remember(onClick) {
        if (onClick != null) MutableInteractionSource() else null
    }
    val pressedState = resolvedInteraction?.collectIsPressedAsState()
    val isPressed = (pressedState?.value == true) && path != GlassPath.FROST

    val pressScale = animateFloatAsState(
        targetValue = if (isPressed) PRESS_SCALE else 1f,
        animationSpec = if (isPressed) PRESS_SPRING else RELEASE_SPRING,
        label = "glassPressScale",
    )
    val pressProgress = animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec = if (isPressed) PRESS_SPRING else RELEASE_SPRING,
        label = "glassPressBulge",
    )

    var surfaceCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val sampleLayer = if (pageBackdrop != null && path != GlassPath.FROST && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        rememberGraphicsLayer()
    } else null

    val vibrancyFilter = remember(path) {
        if (path == GlassPath.FROST) null
        else {
            val matrix = ColorMatrix().apply {
                setSaturation(VIBRANCY_SATURATION)
                val s = 1f + VIBRANCY_BRIGHTNESS
                setScale(s, s, s, 1f)
            }
            ColorMatrixColorFilter(matrix).asComposeColorFilter()
        }
    }

    return this
        .graphicsLayer {
            val s = pressScale.value
            if (s != 1f) {
                scaleX = s
                scaleY = s
            }
        }
        .then(
            if (enableShadow && elevation > 0.dp) {
                Modifier.shadow(
                    elevation = elevation,
                    shape = shape,
                    ambientColor = SHADOW_COLOR,
                    spotColor = SHADOW_COLOR,
                )
            } else Modifier
        )
        .clip(shape)
        .then(
            if (sampleLayer != null) {
                Modifier.onGloballyPositioned { surfaceCoords = it }
            } else Modifier
        )
        .drawWithCache {
            val minDimDp = min(size.width, size.height) / density.density
            val targetAlpha = tintAlphaForSize(minDimDp)
            val bodyTopColor = tint.copy(alpha = (targetAlpha * 0.88f).coerceIn(0.18f, 0.88f))
            val bodyBottomColor = tint.copy(alpha = (targetAlpha * 1.08f).coerceIn(0.24f, 0.94f))
            val bodyBrush = Brush.verticalGradient(listOf(bodyTopColor, bodyBottomColor))

            val outline = shape.createOutline(size, layoutDirection, this)
            val strokePx = HIGHLIGHT_WIDTH.toPx()
            val highlightBrush = if (enableHighlight) {
                val topCol = borderColor?.copy(alpha = 0.55f) ?: Color.White.copy(alpha = HIGHLIGHT_TOP_ALPHA)
                val botCol = borderColor?.copy(alpha = 0.16f) ?: Color.White.copy(alpha = HIGHLIGHT_BOTTOM_ALPHA)
                Brush.verticalGradient(listOf(topCol, botCol))
            } else null

            val blurPx = blurForSize(minDimDp) * density.density
            val cornerRadiusPx = if (shape is CornerBasedShape) {
                shape.topStart.toPx(size, this)
            } else {
                0f
            }
            val (baseLensH, baseLensAmt) = lensParamsForSize(minDimDp)
            val baseLensHPx = baseLensH * density.density
            val baseLensAmtPx = baseLensAmt * density.density

            val staticBlurEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && path != GlassPath.FROST) {
                AndroidRenderEffect.createBlurEffect(
                    blurPx.coerceAtLeast(1f),
                    blurPx.coerceAtLeast(1f),
                    Shader.TileMode.CLAMP,
                )
            } else null

            onDrawWithContent {
                if (sampleLayer != null && pageBackdrop != null && pageBackdrop.hasRecorded) {
                    val coords = surfaceCoords
                    val offset = if (coords != null && coords.isAttached) {
                        pageBackdrop.offsetRelativeTo(coords)
                    } else Offset.Zero

                    val activeEffect = if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        path == GlassPath.LENS &&
                        enableLens &&
                        staticBlurEffect != null
                    ) {
                        val bulge = 1f + PRESS_BULGE * pressProgress.value
                        val lensEffect = GlassShaders.createLensEffect(
                            widthPx = size.width,
                            heightPx = size.height,
                            cornerRadiusPx = cornerRadiusPx,
                            lensHeightPx = baseLensHPx,
                            lensAmountPx = baseLensAmtPx * bulge,
                        )
                        if (lensEffect != null) {
                            AndroidRenderEffect.createChainEffect(lensEffect, staticBlurEffect)
                        } else {
                            staticBlurEffect
                        }
                    } else {
                        staticBlurEffect
                    }

                    sampleLayer.renderEffect = activeEffect?.asComposeRenderEffect()
                    sampleLayer.colorFilter = vibrancyFilter
                    sampleLayer.record {
                        translate(offset.x, offset.y) {
                            drawLayer(pageBackdrop.layer)
                        }
                    }
                    drawLayer(sampleLayer)
                }

                // Tinted translucent glass body
                drawOutline(outline = outline, brush = bodyBrush)

                // Child content
                drawContent()

                // Specular rim highlight
                if (highlightBrush != null) {
                    drawOutline(
                        outline = outline,
                        brush = highlightBrush,
                        style = Stroke(width = strokePx),
                    )
                }
            }
        }
        .then(
            if (onClick != null && resolvedInteraction != null) {
                Modifier.clickable(
                    interactionSource = resolvedInteraction,
                    indication = null,
                    onClick = onClick,
                )
            } else Modifier
        )
}
