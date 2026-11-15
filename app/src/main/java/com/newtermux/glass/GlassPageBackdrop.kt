package com.newtermux.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow

/**
 * Holds a recorded [GraphicsLayer] of the scrollable page content so
 * overlaid glass chrome (top bars, tab bars, bottom sheets) can sample
 * and blur/refract what sits behind them without self-sampling.
 */
@Stable
class GlassPageBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var sourceCoordinates: LayoutCoordinates? = null
    internal var hasRecorded: Boolean = false

    fun offsetRelativeTo(targetCoordinates: LayoutCoordinates): Offset {
        val src = sourceCoordinates ?: return Offset.Zero
        if (!src.isAttached || !targetCoordinates.isAttached) return Offset.Zero
        return try {
            targetCoordinates.localPositionOf(src, Offset.Zero)
        } catch (_: Exception) {
            src.positionInWindow() - targetCoordinates.positionInWindow()
        }
    }
}

val LocalGlassPageBackdrop = staticCompositionLocalOf<GlassPageBackdrop?> { null }

@Composable
fun rememberGlassPageBackdrop(): GlassPageBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassPageBackdrop(layer) }
}

@Composable
fun GlassPageBackdropProvider(
    backdrop: GlassPageBackdrop = rememberGlassPageBackdrop(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalGlassPageBackdrop provides backdrop) {
        content()
    }
}

/**
 * Marks a container (typically the scrollable page body) as the backdrop source
 * recorded into [GlassPageBackdrop]. Glass chrome placed outside this subtree
 * (e.g., topBar, bottomBar, floating sheets) samples this layer cleanly.
 */
@Composable
fun Modifier.glassPageSource(
    backdrop: GlassPageBackdrop? = LocalGlassPageBackdrop.current,
): Modifier {
    if (backdrop == null) return this
    return this
        .onGloballyPositioned { coords ->
            backdrop.sourceCoordinates = coords
        }
        .drawWithContent {
            backdrop.layer.record {
                this@drawWithContent.drawContent()
            }
            backdrop.hasRecorded = true
            drawLayer(backdrop.layer)
        }
}
