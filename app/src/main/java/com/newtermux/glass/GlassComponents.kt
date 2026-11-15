package com.newtermux.glass

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Obsidian page canvas with subtle ambient radial accent glow so glass
 * cards, bars, and sheets have real tonal depth to blur and refract.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(GlassTokens.BACKGROUND)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accent.copy(alpha = 0.14f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.12f),
                        radius = size.maxDimension * 0.65f,
                    )
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF06B6D4).copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.maxDimension * 0.55f,
                    )
                )
            },
        content = content,
    )
}

/**
 * Translucent Liquid Glass top app bar that samples the scrollable page
 * backdrop behind it.
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                elevation = 4.dp,
                tint = GlassTokens.GLASS_TINT.copy(alpha = 0.54f),
                enableShadow = true,
            )
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (navigationIcon != null) {
            navigationIcon()
        } else if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = cs.onSurface,
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = if (onBack == null && navigationIcon == null) 12.dp else 4.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onSurfaceVariant,
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
}

/**
 * Elevated Liquid Glass card surface.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.ShapeCard,
    elevation: Dp = 4.dp,
    tint: Color = GlassTokens.GLASS_TINT,
    borderColor: Color? = null,
    sampleBackdrop: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier.glassSurface(
                shape = shape,
                elevation = elevation,
                tint = tint,
                borderColor = borderColor,
                sampleBackdrop = sampleBackdrop,
                onClick = onClick,
            ),
            content = content,
        )
    }
}

/**
 * Large glass container for bottom sheets and overlay panels.
 */
@Composable
fun GlassBottomSheet(
    modifier: Modifier = Modifier,
    shape: Shape = GlassTokens.ShapeSheet,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .glassSurface(
                    shape = shape,
                    elevation = 12.dp,
                    tint = GlassTokens.GLASS_TINT.copy(alpha = GlassTokens.LARGE_TINT_ALPHA),
                )
                .padding(20.dp),
            content = content,
        )
    }
}

@Immutable
data class GlassTabItem(
    val label: String,
    val icon: (@Composable () -> Unit)? = null,
)

/**
 * Floating pill tab bar with glass selection pill.
 */
@Composable
fun GlassTabBar(
    selectedIndex: Int,
    onTabSelected: (Int) -> Unit,
    tabs: List<GlassTabItem>,
    modifier: Modifier = Modifier,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .glassSurface(
                shape = GlassTokens.ShapePill,
                elevation = 4.dp,
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            val textColor by animateColorAsState(
                targetValue = if (isSelected) cs.onSurface else cs.onSurfaceVariant,
                label = "glassTabText",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .then(
                        if (isSelected) {
                            Modifier.glassSurface(
                                shape = RoundedCornerShape(24.dp),
                                elevation = 0.dp,
                                tint = indicatorColor.copy(alpha = 0.28f),
                                borderColor = indicatorColor,
                                enableShadow = false,
                                sampleBackdrop = false,
                            )
                        } else Modifier
                    )
                    .clickable { onTabSelected(index) }
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (tab.icon != null) {
                        CompositionLocalProvider(LocalContentColor provides textColor) {
                            tab.icon.invoke()
                        }
                    }
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Compact glass chip for filters, tags, and quick actions.
 */
@Composable
fun GlassChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    val tint = if (selected) cs.primary.copy(alpha = 0.26f) else GlassTokens.GLASS_TINT
    val textColor = if (selected) cs.primary else cs.onSurface

    Row(
        modifier = modifier
            .glassSurface(
                shape = GlassTokens.ShapeChip,
                elevation = 0.dp,
                tint = tint,
                borderColor = if (selected) cs.primary else null,
                enableShadow = false,
                sampleBackdrop = false,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (leadingIcon != null) {
            CompositionLocalProvider(LocalContentColor provides textColor) {
                leadingIcon()
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
