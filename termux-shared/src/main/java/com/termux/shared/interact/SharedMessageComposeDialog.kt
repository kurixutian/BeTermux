package com.termux.shared.interact

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.net.Uri
import android.text.SpannableString
import android.text.style.URLSpan
import android.text.util.Linkify
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.termux.shared.compose.showComposeDialog

/** Compose dialog host for [MessageDialogUtils]. */
object SharedMessageComposeDialog {
    @JvmStatic
    fun show(
        context: Context,
        titleText: String?,
        messageText: String?,
        positiveText: String,
        onPositiveButton: DialogInterface.OnClickListener?,
        negativeText: String?,
        onNegativeButton: DialogInterface.OnClickListener?,
        onDismiss: DialogInterface.OnDismissListener?,
    ): Dialog? = showComposeDialog(
        context = context,
        onDismiss = onDismiss,
    ) { dialog ->
        val handled = remember { booleanArrayOf(false) }
        fun dismissAfter(action: () -> Unit) {
            if (handled[0]) return
            handled[0] = true
            // Run button callback before dismiss so state flags update prior to onDismiss.
            try {
                action()
            } finally {
                dialog.dismiss()
            }
        }

        SharedMessageDialogContent(
            titleText = titleText,
            messageText = messageText,
            positiveText = positiveText,
            negativeText = negativeText,
            onPositive = {
                dismissAfter { onPositiveButton?.onClick(dialog, DialogInterface.BUTTON_POSITIVE) }
            },
            onNegative = {
                dismissAfter { onNegativeButton?.onClick(dialog, DialogInterface.BUTTON_NEGATIVE) }
            },
        )
    }
}

@Composable
internal fun SharedMessageDialogContent(
    titleText: String?,
    messageText: String?,
    positiveText: String,
    negativeText: String?,
    onPositive: () -> Unit,
    onNegative: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (!titleText.isNullOrEmpty()) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
        }

        if (!messageText.isNullOrEmpty()) {
            val uriHandler = LocalUriHandler.current
            val context = LocalContext.current
            val linkColor = MaterialTheme.colorScheme.primary
            val linkInteractionListener = remember(uriHandler, context) {
                LinkInteractionListener { link ->
                    val url = (link as? LinkAnnotation.Url)?.url ?: return@LinkInteractionListener
                    try {
                        uriHandler.openUri(url)
                    } catch (_: Exception) {
                        ShareUtils.openUrl(context, url)
                    }
                }
            }
            val annotatedMessage = remember(messageText, linkColor, linkInteractionListener) {
                linkifyMessage(messageText, linkColor, linkInteractionListener)
            }

            Box(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SelectionContainer {
                    Text(
                        text = annotatedMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (negativeText != null) {
                TextButton(onClick = onNegative) {
                    Text(negativeText)
                }
            }
            Button(onClick = onPositive) {
                Text(positiveText)
            }
        }
    }
}

internal fun linkifyMessage(
    text: String,
    linkColor: Color,
    linkInteractionListener: LinkInteractionListener? = null,
): AnnotatedString {
    val spannable = SpannableString(text)
    Linkify.addLinks(spannable, Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES)
    val spans = spannable.getSpans(0, text.length, URLSpan::class.java)
        .sortedBy { spannable.getSpanStart(it) }

    return buildAnnotatedString {
        append(text)
        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                textDecoration = TextDecoration.Underline,
            )
        )
        for (span in spans) {
            val start = spannable.getSpanStart(span)
            val end = spannable.getSpanEnd(span)
            val rawUrl = span.url ?: continue
            if (start < 0 || end <= start) continue
            val matchedText = text.substring(start, end)
            // Skip Unix file path segments (e.g. /tmp/script.sh) falsely matched as ccTLDs without a scheme.
            if (start > 0 && text[start - 1] == '/' &&
                !matchedText.contains("://") &&
                !rawUrl.startsWith("mailto:", ignoreCase = true)
            ) {
                continue
            }
            val normalizedUrl = Uri.parse(rawUrl).normalizeScheme().toString()
            addLink(
                url = LinkAnnotation.Url(
                    url = normalizedUrl,
                    styles = linkStyles,
                    linkInteractionListener = linkInteractionListener,
                ),
                start = start,
                end = end,
            )
        }
    }
}
