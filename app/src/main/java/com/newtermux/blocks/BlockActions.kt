package com.newtermux.blocks

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.theme.LocalWarpPalette
import com.termux.shared.interact.ShareUtils

/**
 * Copy, share, and plain-text character selection actions for structured {@link TerminalBlock}s.
 */
object BlockActions {

    @JvmStatic
    fun copyText(context: Context, text: String, label: String = "Copied to clipboard") {
        if (text.isEmpty()) return
        ShareUtils.copyTextToClipboard(context, text, label)
    }

    @JvmStatic
    fun shareText(context: Context, text: String, subject: String = "Terminal block") {
        if (text.isEmpty()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(intent, "Share block"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlockTextSelectionSheet(
    title: String,
    text: String,
    onCopyAll: () -> Unit,
    onShareAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val palette = LocalWarpPalette.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = palette.surfaceElevated,
        contentColor = palette.textPrimary
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = title,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Drag handles to select exact characters or words",
                color = palette.textSecondary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            SelectionContainer(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 120.dp, max = 380.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = text.ifEmpty { "(no output)" },
                    color = palette.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onShareAll,
                    modifier = Modifier.semantics { contentDescription = "Share block text" },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary)
                ) {
                    Text("Share")
                }
                TextButton(
                    onClick = onCopyAll,
                    modifier = Modifier.semantics { contentDescription = "Copy all block text" },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.defaultAccent)
                ) {
                    Text("Copy All")
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.semantics { contentDescription = "Close text selection sheet" },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.textPrimary)
                ) {
                    Text("Done")
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
