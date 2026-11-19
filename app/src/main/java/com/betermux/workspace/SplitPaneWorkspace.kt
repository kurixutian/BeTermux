package com.betermux.workspace

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.theme.LocalWarpPalette

/** Inactive split pane alpha (18% opacity reduction within 15-20% spec target). */
const val INACTIVE_PANE_ALPHA = 0.82f

/**
 * Adaptive split-pane workspace (vertical stack in portrait, side-by-side in landscape)
 * applying 18% opacity dimming on inactive panes and tap-to-focus routing.
 */
@Composable
fun SplitPaneWorkspace(
    primaryTitle: String,
    secondaryTitle: String,
    isPrimaryFocused: Boolean,
    onFocusPrimary: () -> Unit,
    onFocusSecondary: () -> Unit,
    onCloseSplit: () -> Unit,
    primaryContent: @Composable () -> Unit,
    secondaryContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalWarpPalette.current
    val config = LocalConfiguration.current
    val isLandscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(modifier = modifier.fillMaxSize()) {
            SplitPaneContainer(
                title = primaryTitle,
                isFocused = isPrimaryFocused,
                onFocus = onFocusPrimary,
                onCloseSplit = null,
                tag = "split_pane_primary",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = primaryContent
            )
            VerticalDivider(thickness = 1.dp, color = palette.borderStrong)
            SplitPaneContainer(
                title = secondaryTitle,
                isFocused = !isPrimaryFocused,
                onFocus = onFocusSecondary,
                onCloseSplit = onCloseSplit,
                tag = "split_pane_secondary",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                content = secondaryContent
            )
        }
    } else {
        Column(modifier = modifier.fillMaxSize()) {
            SplitPaneContainer(
                title = primaryTitle,
                isFocused = isPrimaryFocused,
                onFocus = onFocusPrimary,
                onCloseSplit = null,
                tag = "split_pane_primary",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = primaryContent
            )
            HorizontalDivider(thickness = 1.dp, color = palette.borderStrong)
            SplitPaneContainer(
                title = secondaryTitle,
                isFocused = !isPrimaryFocused,
                onFocus = onFocusSecondary,
                onCloseSplit = onCloseSplit,
                tag = "split_pane_secondary",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                content = secondaryContent
            )
        }
    }
}

@Composable
private fun SplitPaneContainer(
    title: String,
    isFocused: Boolean,
    onFocus: () -> Unit,
    onCloseSplit: (() -> Unit)?,
    tag: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val paneAlpha = if (isFocused) 1.0f else INACTIVE_PANE_ALPHA
    val borderColor = if (isFocused) accent else palette.borderSubtle
    val borderWidth = if (isFocused) 1.5.dp else 1.dp

    Column(
        modifier = modifier
            .alpha(paneAlpha)
            .border(borderWidth, borderColor)
            .clickable(enabled = !isFocused, onClick = onFocus)
            .testTag(tag)
            .semantics {
                contentDescription = if (isFocused) {
                    "Active pane: $title"
                } else {
                    "Inactive pane: $title, tap to focus"
                }
            }
    ) {
        // Slim structural pane header strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isFocused) palette.surfaceElevated else palette.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (isFocused) accent else palette.textMuted)
                )
                Text(
                    text = title.ifBlank { "Terminal" },
                    color = if (isFocused) palette.textPrimary else palette.textSecondary,
                    fontSize = 11.5.sp,
                    fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onCloseSplit != null) {
                // 48dp accessibility touch target; the Close icon stays 14dp visually.
                IconButton(
                    onClick = onCloseSplit,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("split_pane_close")
                        .semantics { contentDescription = "Close split pane" }
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            } else if (!isFocused) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = palette.surfaceInteractive,
                    border = BorderStroke(1.dp, palette.borderSubtle)
                ) {
                    // More prominent focus affordance on the inactive pane header.
                    Text(
                        text = "Focus",
                        color = palette.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            content()
        }
    }
}
