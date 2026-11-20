package com.termux.app

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.theme.LocalWarpPalette
import com.termux.R

/** Compose suggestion strip with a tiny Java-facing update API. */
class AutoCorrectComposeBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {
    private var suggestion by mutableStateOf<String?>(null)
    private var onApply: Runnable? = null
    private var onDismiss: Runnable? = null

    init {
        isFocusable = false
        isFocusableInTouchMode = false
        descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS

        ComposeView(context).apply {
            isFocusable = false
            isFocusableInTouchMode = false
            descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BeTermuxComposeTheme(context) {
                    val current = suggestion
                    if (current != null) {
                        AutoCorrectBarContent(
                            suggestion = current,
                            onApply = { onApply?.run() },
                            onDismiss = { onDismiss?.run() },
                        )
                    }
                }
            }
        }.also { addView(it, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)) }
        visibility = View.GONE
    }

    fun bindActions(apply: Runnable, dismiss: Runnable) {
        onApply = apply
        onDismiss = dismiss
    }

    fun showSuggestion(value: String) {
        suggestion = value
        visibility = View.VISIBLE
    }

    fun hideSuggestion() {
        visibility = View.GONE
        suggestion = null
    }
}

@Composable
internal fun AutoCorrectBarContent(
    suggestion: String,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surfaceElevated),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = stringResource(R.string.autocorrect_did_you_mean, suggestion),
                modifier = Modifier.weight(1f),
                color = palette.textPrimary,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = onApply) {
                Text(stringResource(R.string.autocorrect_apply), color = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.autocorrect_dismiss),
                    tint = palette.textSecondary,
                )
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
    }
}
