package com.termux.app

import android.app.Activity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.betermux.compose.ComposeDialogHost
import com.betermux.theme.LocalWarpPalette
import com.termux.R

/** Hosts the bootstrap failure prompt in Compose while the installer keeps owning its actions. */
object BootstrapErrorComposeDialog {
    @JvmStatic
    fun show(activity: Activity, onAbort: Runnable, onRetry: Runnable) {
        ComposeDialogHost.show(activity, cancelable = true, canceledOnTouchOutside = true) { dialog ->
            BootstrapErrorContent(
                onAbort = { dialog.dismiss(); onAbort.run() },
                onRetry = { dialog.dismiss(); onRetry.run() },
            )
        }
    }
}

@Composable
internal fun BootstrapErrorContent(
    onAbort: () -> Unit,
    onRetry: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.bootstrap_error_title),
            style = MaterialTheme.typography.titleLarge,
            color = palette.textPrimary,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = stringResource(R.string.bootstrap_error_body),
            style = MaterialTheme.typography.bodyMedium,
            color = palette.textSecondary,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(onClick = onAbort) {
                Text(stringResource(R.string.bootstrap_error_abort))
            }
            Button(onClick = onRetry) {
                Text(stringResource(R.string.bootstrap_error_try_again))
            }
        }
    }
}
