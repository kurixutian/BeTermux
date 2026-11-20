package com.termux.shared.termux.interact

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.termux.shared.compose.showComposeDialog

/** Compose dialog host for [TextInputDialogUtils]. */
object SharedTextInputComposeDialog {
    @JvmStatic
    fun show(
        context: Context,
        titleText: String,
        initialText: String,
        positiveText: String,
        onPositive: TextInputDialogUtils.TextSetListener?,
        neutralText: String?,
        onNeutral: TextInputDialogUtils.TextSetListener?,
        negativeText: String,
        onNegative: TextInputDialogUtils.TextSetListener?,
        onDismiss: DialogInterface.OnDismissListener?,
    ): Dialog? = showComposeDialog(
        context = context,
        cancelable = true,
        canceledOnTouchOutside = false,
        onDismiss = onDismiss,
    ) { dialog ->
        val handled = remember { booleanArrayOf(false) }
        fun dismissAfter(action: () -> Unit) {
            if (handled[0]) return
            handled[0] = true
            // Execute callback before dismiss (required e.g. by FileReceiverActivity.mFinishOnDismissNameDialog).
            try {
                action()
            } finally {
                dialog.dismiss()
            }
        }

        SharedTextInputDialogContent(
            titleText = titleText,
            initialText = initialText,
            positiveText = positiveText,
            neutralText = neutralText,
            negativeText = negativeText,
            onPositive = { text ->
                dismissAfter { onPositive?.onTextSet(text) }
            },
            onNeutral = if (neutralText != null && onNeutral != null) {
                { text -> dismissAfter { onNeutral.onTextSet(text) } }
            } else {
                null
            },
            onNegative = { text ->
                dismissAfter { onNegative?.onTextSet(text) }
            },
        )
    }
}

@Composable
internal fun SharedTextInputDialogContent(
    titleText: String,
    initialText: String,
    positiveText: String,
    neutralText: String?,
    negativeText: String,
    onPositive: (String) -> Unit,
    onNeutral: ((String) -> Unit)?,
    onNegative: ((String) -> Unit)?,
) {
    var value by remember(initialText) {
        mutableStateOf(TextFieldValue(initialText, TextRange(initialText.length)))
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = titleText,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { heading() },
        )

        OutlinedTextField(
            value = value,
            onValueChange = { value = it },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .semantics { contentDescription = titleText },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onPositive(value.text) }),
        )

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (neutralText != null && onNeutral != null) {
                TextButton(onClick = { onNeutral(value.text) }) {
                    Text(neutralText)
                }
            }
            TextButton(onClick = { onNegative?.invoke(value.text) }) {
                Text(negativeText)
            }
            Button(onClick = { onPositive(value.text) }) {
                Text(positiveText)
            }
        }
    }
}
