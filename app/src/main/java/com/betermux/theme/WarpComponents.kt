package com.betermux.theme

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val LocalWarpPalette = staticCompositionLocalOf { CuratedThemes.WarmDark }

/**
 * Root canvas background for BeTermux screens: solid warm charcoal (or active
 * curated theme background) with optional cached user wallpaper + scrim overlay.
 * Zero blurs, shaders, or per-frame allocations.
 */
@Composable
fun WarpBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val palette = LocalWarpPalette.current
    BackgroundImageStore.ensureInitialized(context)
    val bgVersion = BackgroundImageStore.version
    val scrimAlpha = BackgroundImageStore.scrimAlphaState

    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(bgVersion) {
        bitmap = if (BackgroundImageStore.hasImage(context)) {
            BackgroundImageStore.loadBitmap(context)
        } else {
            null
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        val loaded = bitmap
        if (loaded != null && !loaded.isRecycled) {
            Image(
                bitmap = loaded.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.background.copy(alpha = scrimAlpha)),
            )
        }
        content()
    }
}

/**
 * Flat structural surface modifier with crisp 1dp border and zero drop shadows.
 */
fun Modifier.warpSurface(
    shape: Shape = RoundedCornerShape(10.dp),
    color: Color? = null,
    borderColor: Color? = null,
    borderWidth: Dp = 1.dp,
): Modifier = composed {
    val palette = LocalWarpPalette.current
    val bg = color ?: palette.surface
    val stroke = borderColor ?: palette.borderSubtle
    this
        .clip(shape)
        .background(bg, shape)
        .border(borderWidth, stroke, shape)
}

/**
 * Flat structural top header bar with status-bar inset and hairline bottom border.
 */
@Composable
fun WarpTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalWarpPalette.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = palette.surface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .heightIn(min = 52.dp)
                    .padding(horizontal = if (onBack != null) 4.dp else 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = palette.textPrimary,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        }
    }
}

/**
 * Warp-styled structural card with clean hairline border and zero shadow/glow.
 */
@Composable
fun WarpCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalWarpPalette.current
    val fill = if (selected) palette.surfaceElevated else palette.surface
    val border = if (selected) MaterialTheme.colorScheme.primary else palette.borderSubtle
    val clickModifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier

    Column(
        modifier = modifier
            .clip(shape)
            .background(fill, shape)
            .border(1.dp, border, shape)
            .then(clickModifier),
        content = content,
    )
}

/**
 * Warp-styled docked modal sheet surface.
 */
@Composable
fun WarpBottomSheet(
    modifier: Modifier = Modifier,
    showHandle: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated, shape)
            .border(1.dp, palette.borderStrong, shape)
            .navigationBarsPadding(),
    ) {
        if (showHandle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(palette.borderStrong),
                )
            }
        }
        content()
    }
}

data class WarpSegmentedItem(val label: String)

/**
 * Flat segmented switcher with crisp selected border and high-contrast text.
 */
@Composable
fun WarpSegmentedBar(
    items: List<WarpSegmentedItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWarpPalette.current
    val outerShape = RoundedCornerShape(10.dp)
    val itemShape = RoundedCornerShape(8.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outerShape)
            .background(palette.surface, outerShape)
            .border(1.dp, palette.borderSubtle, outerShape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items.forEachIndexed { index, item ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 38.dp)
                    .clip(itemShape)
                    .background(
                        color = if (selected) palette.surfaceInteractive else Color.Transparent,
                        shape = itemShape,
                    )
                    .then(
                        if (selected) {
                            Modifier.border(
                                1.dp,
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f),
                                itemShape,
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = item.label,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * Compact flat chip for status labels and actions.
 */
@Composable
fun WarpChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val palette = LocalWarpPalette.current
    val activeColor = accent ?: MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(8.dp)
    val bg = if (selected) activeColor.copy(alpha = 0.16f) else palette.surfaceElevated
    val stroke = if (selected) activeColor.copy(alpha = 0.7f) else palette.borderSubtle
    val labelColor = if (selected) activeColor else palette.textPrimary

    Row(
        modifier = modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .background(bg, shape)
            .border(1.dp, stroke, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            Box(modifier = Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                leadingIcon()
            }
        }
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
