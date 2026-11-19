package com.betermux.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.theme.LocalWarpPalette

/**
 * Honest, non-intrusive status strip overlaid on the terminal container when the active
 * workspace is [WorkspaceSleepState.SLEEPING], [WorkspaceSleepState.SUSPENDING],
 * [WorkspaceSleepState.RESUMING], or [WorkspaceSleepState.ERROR].
 */
@Composable
fun WorkspaceHibernationBanner(
    sleepState: WorkspaceSleepState,
    onWakeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (sleepState == WorkspaceSleepState.AWAKE) return

    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(10.dp)

    val borderColor = when (sleepState) {
        WorkspaceSleepState.ERROR -> palette.error
        WorkspaceSleepState.SUSPENDING -> palette.warning
        WorkspaceSleepState.RESUMING -> palette.info
        WorkspaceSleepState.SLEEPING -> palette.borderStrong
        WorkspaceSleepState.AWAKE -> palette.borderSubtle
    }

    val message = when (sleepState) {
        WorkspaceSleepState.SLEEPING ->
            "Workspace sleeping (zzz) — input paused. Remote SSH sessions may drop during long sleep."
        WorkspaceSleepState.SUSPENDING -> "Suspending workspace…"
        WorkspaceSleepState.RESUMING -> "Waking workspace…"
        WorkspaceSleepState.ERROR ->
            "Hibernation error — some processes could not be verified. Tap Retry Wake or check sessions."
        WorkspaceSleepState.AWAKE -> ""
    }

    val actionLabel = when (sleepState) {
        WorkspaceSleepState.SLEEPING -> "Wake"
        WorkspaceSleepState.ERROR -> "Retry Wake"
        else -> null
    }

    val actionDescription = when (sleepState) {
        WorkspaceSleepState.SLEEPING -> "Wake workspace"
        WorkspaceSleepState.ERROR -> "Retry waking workspace"
        else -> ""
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .clip(shape)
            .background(palette.surfaceElevated.copy(alpha = 0.96f), shape)
            .border(1.dp, borderColor, shape)
            .padding(start = 12.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .testTag("workspace_hibernation_banner"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            color = if (sleepState == WorkspaceSleepState.ERROR) palette.error else palette.textPrimary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
        if (actionLabel != null) {
            TextButton(
                onClick = onWakeClick,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                colors = ButtonDefaults.textButtonColors(
                    contentColor = if (sleepState == WorkspaceSleepState.ERROR) palette.error else accent
                ),
                modifier = Modifier
                    .testTag("workspace_wake_banner_button")
                    .semantics { contentDescription = actionDescription }
            ) {
                Text(
                    text = actionLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
