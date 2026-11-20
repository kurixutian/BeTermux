package com.termux.app

import android.app.Activity
import android.app.Dialog
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.betermux.compose.ComposeDialogHost
import com.betermux.compose.MenuItemDivider
import com.betermux.theme.LocalWarpPalette

/** Java-callable Compose dialog helpers for [TermuxActivity] and terminal clients. */
object TermuxDialogsCompose {

    fun interface IndexCallback {
        fun onSelect(index: Int)
    }

    fun interface EditCommandCallback {
        fun onSave(name: String, command: String)
    }

    fun interface SelectedIndicesCallback {
        fun onSave(selectedIndices: IntArray)
    }

    @JvmStatic
    fun showConfirmDialog(
        activity: Activity,
        title: String?,
        message: String,
        positiveLabel: String?,
        negativeLabel: String?,
        showAlertIcon: Boolean,
        onConfirm: Runnable?,
    ) {
        ComposeDialogHost.show(activity) { dialog ->
            ConfirmDialogContent(
                title = title,
                message = message,
                positiveLabel = positiveLabel,
                negativeLabel = negativeLabel,
                showAlertIcon = showAlertIcon,
                onDismiss = dialog::dismiss,
                onConfirm = {
                    dialog.dismiss()
                    onConfirm?.run()
                },
            )
        }
    }

    @JvmStatic
    @JvmOverloads
    fun showActionListDialog(
        activity: Activity,
        title: String?,
        items: Array<String>,
        onClick: IndexCallback,
        onLongClick: IndexCallback?,
        onClickLabel: String? = null,
        onLongClickLabel: String? = null,
    ) {
        val list = items.toList()
        ComposeDialogHost.show(activity) { dialog ->
            ActionListDialogContent(
                title = title,
                items = list,
                onItemClick = { idx ->
                    dialog.dismiss()
                    onClick.onSelect(idx)
                },
                onItemLongClick = onLongClick?.let { cb ->
                    { idx ->
                        dialog.dismiss()
                        cb.onSelect(idx)
                    }
                },
                onClickLabel = onClickLabel,
                onLongClickLabel = onLongClickLabel,
            )
        }
    }

    @JvmStatic
    fun showQuickCommandsDialog(
        activity: Activity,
        items: Array<String>,
        canAdd: Boolean,
        onRun: IndexCallback,
        onEdit: IndexCallback,
        onAdd: Runnable,
        onRemoveLast: Runnable,
    ) {
        val list = items.toList()
        ComposeDialogHost.show(activity) { dialog ->
            QuickCommandsDialogContent(
                items = list,
                canAdd = canAdd,
                onRun = { idx ->
                    dialog.dismiss()
                    onRun.onSelect(idx)
                },
                onEdit = { idx ->
                    dialog.dismiss()
                    onEdit.onSelect(idx)
                },
                onAdd = {
                    dialog.dismiss()
                    onAdd.run()
                },
                onRemoveLast = {
                    dialog.dismiss()
                    onRemoveLast.run()
                },
                onClose = dialog::dismiss,
            )
        }
    }

    @JvmStatic
    fun showEditDrawerButtonDialog(
        activity: Activity,
        indexOneBased: Int,
        initialName: String,
        initialCmd: String,
        onSave: EditCommandCallback,
        onReset: Runnable,
    ) {
        ComposeDialogHost.show(activity) { dialog ->
            EditDrawerButtonDialogContent(
                indexOneBased = indexOneBased,
                initialName = initialName,
                initialCmd = initialCmd,
                onSave = { name, cmd ->
                    dialog.dismiss()
                    onSave.onSave(name, cmd)
                },
                onReset = {
                    dialog.dismiss()
                    onReset.run()
                },
                onCancel = dialog::dismiss,
            )
        }
    }

    @JvmStatic
    fun showMakeScriptDialog(
        activity: Activity,
        entries: Array<String>,
        onSave: SelectedIndicesCallback,
    ) {
        val list = entries.toList()
        ComposeDialogHost.show(activity) { dialog ->
            MakeScriptDialogContent(
                entries = list,
                onSave = { indices ->
                    dialog.dismiss()
                    onSave.onSave(indices)
                },
                onCancel = dialog::dismiss,
            )
        }
    }

    @JvmStatic
    fun showTipsAndShortcutsDialog(activity: Activity) {
        ComposeDialogHost.show(activity) { dialog ->
            TipsAndShortcutsDialogContent(onDismiss = dialog::dismiss)
        }
    }

    @JvmStatic
    fun showProgressDialog(activity: Activity, message: String): Dialog? =
        ComposeDialogHost.show(
            activity,
            cancelable = false,
            canceledOnTouchOutside = false,
        ) { _ ->
            ProgressDialogContent(message = message)
        }
}

@Composable
internal fun ConfirmDialogContent(
    title: String?,
    message: String,
    positiveLabel: String?,
    negativeLabel: String?,
    showAlertIcon: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    val resolvedTitle = title?.takeIf { it.isNotBlank() }
    val resolvedNegative = negativeLabel?.takeIf { it.isNotBlank() }
    val resolvedPositive = positiveLabel?.takeIf { it.isNotBlank() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (showAlertIcon || resolvedTitle != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (showAlertIcon) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = palette.warning,
                    )
                }
                if (resolvedTitle != null) {
                    DialogTitle(text = resolvedTitle, modifier = Modifier.weight(1f, fill = false))
                }
            }
        }

        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (resolvedTitle != null) palette.textSecondary else palette.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        )

        DialogButtonBar {
            if (resolvedNegative != null) {
                TextButton(onClick = onDismiss) {
                    Text(resolvedNegative)
                }
            }
            if (resolvedPositive != null) {
                Button(onClick = onConfirm) {
                    Text(resolvedPositive)
                }
            }
            if (resolvedNegative == null && resolvedPositive == null) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        }
    }
}

@Composable
internal fun ActionListDialogContent(
    title: String?,
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onItemLongClick: ((Int) -> Unit)?,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!title.isNullOrBlank()) {
            DialogTitle(
                text = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
            )
            MenuItemDivider()
        }
        DialogActionList(
            items = items,
            onItemClick = onItemClick,
            onItemLongClick = onItemLongClick,
            onClickLabel = onClickLabel,
            onLongClickLabel = onLongClickLabel,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
        )
    }
}

@Composable
internal fun QuickCommandsDialogContent(
    items: List<String>,
    canAdd: Boolean,
    onRun: (Int) -> Unit,
    onEdit: (Int) -> Unit,
    onAdd: () -> Unit,
    onRemoveLast: () -> Unit,
    onClose: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    Column(modifier = Modifier.fillMaxWidth()) {
        DialogTitle(
            text = "Quick Commands",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        if (items.isEmpty()) {
            Text(
                text = "No quick commands yet. Tap + Add to create one.",
                style = MaterialTheme.typography.bodyMedium,
                color = palette.textSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
            )
        } else {
            MenuItemDivider()
            DialogActionList(
                items = items,
                onItemClick = onRun,
                onItemLongClick = onEdit,
                onClickLabel = "Run command",
                onLongClickLabel = "Edit shortcut",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
            )
            MenuItemDivider()
        }
        DialogButtonBar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            if (items.isNotEmpty()) {
                TextButton(onClick = onRemoveLast) {
                    Text("− Remove Last")
                }
            }
            TextButton(onClick = onClose) {
                Text("Close")
            }
            Button(onClick = onAdd, enabled = canAdd) {
                Text("+ Add")
            }
        }
    }
}

@Composable
internal fun EditDrawerButtonDialogContent(
    indexOneBased: Int,
    initialName: String,
    initialCmd: String,
    onSave: (String, String) -> Unit,
    onReset: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember(indexOneBased, initialName) { mutableStateOf(initialName) }
    var cmd by remember(indexOneBased, initialCmd) { mutableStateOf(initialCmd) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DialogTitle(text = "Edit Shortcut $indexOneBased")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Button label") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = cmd,
                onValueChange = { cmd = it },
                label = { Text("Command") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        DialogButtonBar {
            TextButton(onClick = onReset) {
                Text("Reset")
            }
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = { onSave(name.trim(), cmd.trim()) }) {
                Text("Save")
            }
        }
    }
}

@Composable
internal fun MakeScriptDialogContent(
    entries: List<String>,
    onSave: (IntArray) -> Unit,
    onCancel: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    val checked = remember(entries) {
        mutableStateListOf<Boolean>().apply { repeat(entries.size) { add(false) } }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        DialogTitle(
            text = "Make Script — select commands",
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 16.dp),
        )
        if (entries.isNotEmpty()) {
            MenuItemDivider()
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            entries.forEachIndexed { index, entry ->
                val isChecked = checked[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .toggleable(
                            value = isChecked,
                            role = Role.Checkbox,
                            onValueChange = { checked[index] = it },
                        )
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = null,
                    )
                    Text(
                        text = entry,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (entries.isNotEmpty()) {
            MenuItemDivider()
        }
        DialogButtonBar(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
            Button(onClick = {
                val selected = entries.indices.filter { checked[it] }.toIntArray()
                onSave(selected)
            }) {
                Text("Save Script")
            }
        }
    }
}

private data class TipSection(val title: String, val body: String)

private val TIP_SECTIONS = listOf(
    TipSection(
        title = "🌐 Clickable Links & Paths",
        body = "Tap any URL or file path directly in the terminal to view it in the Dev Browser or File Viewer. Multi-line wrapped links are supported.",
    ),
    TipSection(
        title = "🚀 Dev Browser & DevTools",
        body = "Live localhost servers appear automatically in the top bar and browser port bar. Tap the ⋮ browser menu to zoom in/out, view history, or configure settings.",
    ),
    TipSection(
        title = "🖼️ Paste / Upload Images to CLI",
        body = "Copy any screenshot to your clipboard and paste directly into the terminal, or use Upload / Paste Image in the + or ⋮ menu.",
    ),
    TipSection(
        title = "📁 File Manager & Search",
        body = "Tap Files in the toolbar. Toggle hidden files with the eye icon, or use the search bar to filter files instantly.",
    ),
    TipSection(
        title = "🛡️ Tab Safety & Management",
        body = "Tap + to create terminal or browser tabs. Long-press any tab to rename or close it.",
    ),
    TipSection(
        title = "⌨️ Extra Keys & Menu",
        body = "Toggle the keyboard shortcuts bar anytime. Tap ⋮ More for Clear Terminal, Package Manager, Voice Input, and Settings.",
    ),
)

@Composable
internal fun TipsAndShortcutsDialogContent(onDismiss: () -> Unit) {
    val palette = LocalWarpPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DialogTitle(text = "⚡ BeTermux Tips & Shortcuts")
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TIP_SECTIONS.forEach { tip ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = tip.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = tip.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
            }
            HorizontalDivider(color = palette.borderSubtle)
            Text(
                text = "BeTermux v1.0.0 • Forked by Kurixutian • Based on Termux Open Source Project",
                style = MaterialTheme.typography.bodySmall,
                color = palette.textMuted,
            )
        }
        Button(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text("Got It")
        }
    }
}

@Composable
internal fun ProgressDialogContent(message: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CircularProgressIndicator()
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = LocalWarpPalette.current.textPrimary,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

@Composable
private fun DialogTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        color = LocalWarpPalette.current.textPrimary,
        modifier = modifier.semantics { heading() },
    )
}

@Composable
private fun DialogActionList(
    items: List<String>,
    onItemClick: (Int) -> Unit,
    onItemLongClick: ((Int) -> Unit)?,
    modifier: Modifier = Modifier,
    onClickLabel: String? = null,
    onLongClickLabel: String? = null,
) {
    val palette = LocalWarpPalette.current
    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        items.forEachIndexed { index, label ->
            if (index > 0) MenuItemDivider()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .combinedClickable(
                        onClickLabel = onClickLabel,
                        onLongClickLabel = onLongClickLabel,
                        onClick = { onItemClick(index) },
                        onLongClick = onItemLongClick?.let { cb -> { cb(index) } },
                    )
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = palette.textPrimary,
                )
            }
        }
    }
}

@Composable
private fun DialogButtonBar(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}
