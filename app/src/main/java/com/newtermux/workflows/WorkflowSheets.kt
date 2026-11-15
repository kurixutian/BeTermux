package com.newtermux.workflows

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.theme.LocalWarpPalette
import com.newtermux.theme.WarpBottomSheet
import com.newtermux.theme.WarpChip

sealed class WorkflowSheetMode {
    object Hidden : WorkflowSheetMode()
    object Picker : WorkflowSheetMode()
    data class Editor(val initialWorkflow: Workflow, val isNew: Boolean) : WorkflowSheetMode()
    data class ParamFill(val workflow: Workflow) : WorkflowSheetMode()
}

/**
 * Unified Compose sheet overlay for browsing, creating/editing, parameterizing,
 * and sharing workflows with calm Warp charcoal surfaces.
 */
@Composable
fun WorkflowSheetOverlay(
    mode: WorkflowSheetMode,
    workflows: List<Workflow>,
    onDismiss: () -> Unit,
    onOpenNew: () -> Unit,
    onSelectForInsert: (Workflow) -> Unit,
    onSaveWorkflow: (Workflow) -> Unit,
    onDeleteWorkflow: (Workflow) -> Unit,
    onShareWorkflow: (Workflow) -> Unit,
    onEditWorkflow: (Workflow) -> Unit,
    onInsertRenderedCommand: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (mode is WorkflowSheetMode.Hidden) return

    BackHandler(enabled = true) { onDismiss() }

    val palette = LocalWarpPalette.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background.copy(alpha = 0.68f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
        )
        WarpBottomSheet {
            when (mode) {
                is WorkflowSheetMode.Picker -> {
                    WorkflowPickerContent(
                        workflows = workflows,
                        onDismiss = onDismiss,
                        onOpenNew = onOpenNew,
                        onUseWorkflow = { wf ->
                            if (wf.extractTokens().isEmpty()) {
                                onInsertRenderedCommand(wf.commandTemplate)
                            } else {
                                onSelectForInsert(wf)
                            }
                        },
                        onEditWorkflow = onEditWorkflow,
                        onShareWorkflow = onShareWorkflow,
                        onDeleteWorkflow = onDeleteWorkflow
                    )
                }
                is WorkflowSheetMode.ParamFill -> {
                    WorkflowParamFillContent(
                        workflow = mode.workflow,
                        onBack = onDismiss,
                        onInsert = { rendered -> onInsertRenderedCommand(rendered) }
                    )
                }
                is WorkflowSheetMode.Editor -> {
                    WorkflowEditorContent(
                        initial = mode.initialWorkflow,
                        isNew = mode.isNew,
                        onCancel = onDismiss,
                        onSave = { updated -> onSaveWorkflow(updated) }
                    )
                }
                WorkflowSheetMode.Hidden -> Unit
            }
        }
    }
}

@Composable
private fun WorkflowPickerContent(
    workflows: List<Workflow>,
    onDismiss: () -> Unit,
    onOpenNew: () -> Unit,
    onUseWorkflow: (Workflow) -> Unit,
    onEditWorkflow: (Workflow) -> Unit,
    onShareWorkflow: (Workflow) -> Unit,
    onDeleteWorkflow: (Workflow) -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(workflows, searchQuery) {
        val q = searchQuery.trim().lowercase()
        if (q.isEmpty()) workflows else workflows.filter { wf ->
            wf.name.lowercase().contains(q) ||
                wf.description.lowercase().contains(q) ||
                wf.commandTemplate.lowercase().contains(q) ||
                wf.tags.any { it.lowercase().contains(q) }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 520.dp)
            .padding(horizontal = 14.dp)
            .testTag("workflow_picker_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Workflows",
                color = palette.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            WarpChip(
                label = "New",
                accent = accent,
                selected = true,
                leadingIcon = {
                    Icon(Icons.Default.Add, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                },
                onClick = onOpenNew,
                modifier = Modifier
                    .testTag("workflow_new_button")
                    .semantics { contentDescription = "Create new workflow" }
            )
            Spacer(modifier = Modifier.width(6.dp))
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .size(32.dp)
                    .semantics { contentDescription = "Close workflows sheet" }
            ) {
                Icon(Icons.Default.Close, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(16.dp))
            Box(modifier = Modifier.weight(1f)) {
                if (searchQuery.isEmpty()) {
                    Text("Search saved workflows…", color = palette.textMuted, fontSize = 13.sp)
                }
                BasicTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("workflow_picker_search")
                        .semantics { contentDescription = "Search workflows" }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (workflows.isEmpty()) {
                        "No workflows yet. Long-press any command block or tap New to save one."
                    } else {
                        "No workflows match \"$searchQuery\""
                    },
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                contentPadding = PaddingValues(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { wf ->
                    WorkflowListCard(
                        workflow = wf,
                        onUse = { onUseWorkflow(wf) },
                        onEdit = { onEditWorkflow(wf) },
                        onShare = { onShareWorkflow(wf) },
                        onDelete = { onDeleteWorkflow(wf) }
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkflowListCard(
    workflow: Workflow,
    onUse: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val tokens = remember(workflow.commandTemplate) { workflow.extractTokens() }
    val shape = RoundedCornerShape(10.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface, shape)
            .border(1.dp, palette.borderSubtle, shape)
            .clickable(onClick = onUse)
            .padding(10.dp)
            .testTag("workflow_card_${workflow.id}")
            .semantics { contentDescription = "Workflow ${workflow.name}" }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    color = palette.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (workflow.description.isNotBlank()) {
                    Text(
                        text = workflow.description,
                        color = palette.textSecondary,
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            if (tokens.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = accent.copy(alpha = 0.16f),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.55f))
                ) {
                    Text(
                        text = "${tokens.size} var${if (tokens.size == 1) "" else "s"}",
                        color = accent,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = workflow.commandTemplate,
            color = palette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.5.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp)
        )

        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                for (tag in workflow.tags.take(3)) {
                    Text(
                        text = "#$tag",
                        color = palette.textMuted,
                        fontSize = 10.5.sp
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(30.dp).semantics { contentDescription = "Edit workflow ${workflow.name}" }
                ) {
                    Icon(Icons.Default.Edit, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onShare,
                    modifier = Modifier.size(30.dp).semantics { contentDescription = "Share workflow ${workflow.name}" }
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(30.dp).semantics { contentDescription = "Delete workflow ${workflow.name}" }
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = palette.error, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                WarpChip(
                    label = if (tokens.isEmpty()) "Insert" else "Fill…",
                    accent = accent,
                    selected = true,
                    leadingIcon = {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = accent, modifier = Modifier.size(14.dp))
                    },
                    onClick = onUse,
                    modifier = Modifier
                        .testTag("workflow_use_${workflow.id}")
                        .semantics { contentDescription = "Insert workflow ${workflow.name}" }
                )
            }
        }
    }
}

@Composable
private fun WorkflowParamFillContent(
    workflow: Workflow,
    onBack: () -> Unit,
    onInsert: (String) -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    val tokens = remember(workflow.commandTemplate) { workflow.extractTokens() }
    val values = remember(workflow.id) {
        mutableStateMapOf<String, String>().apply {
            for (tok in tokens) put(tok.name, tok.defaultValue)
        }
    }
    val preview = workflow.renderCommand(values)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .testTag("workflow_param_sheet")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(32.dp).semantics { contentDescription = "Back to workflows" }
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = palette.textPrimary, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = workflow.name,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        for (token in tokens) {
            Text(
                text = token.name,
                color = palette.textSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            BasicTextField(
                value = values[token.name] ?: "",
                onValueChange = { values[token.name] = it },
                singleLine = true,
                textStyle = TextStyle(color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.surface, RoundedCornerShape(8.dp))
                    .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .testTag("workflow_token_${token.name}")
                    .semantics { contentDescription = "Value for ${token.name}" }
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        Text(text = "Preview", color = palette.textMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = preview,
            color = palette.textPrimary,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.5.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.background, RoundedCornerShape(8.dp))
                .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
                .padding(10.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary)) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = accent,
                modifier = Modifier
                    .clickable { onInsert(preview) }
                    .testTag("workflow_insert_rendered")
                    .semantics { contentDescription = "Insert rendered workflow into input bar" }
            ) {
                Text(
                    text = "Insert into Input",
                    color = palette.background,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun WorkflowEditorContent(
    initial: Workflow,
    isNew: Boolean,
    onCancel: () -> Unit,
    onSave: (Workflow) -> Unit
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    var name by remember(initial.id) { mutableStateOf(initial.name) }
    var description by remember(initial.id) { mutableStateOf(initial.description) }
    var tagsRaw by remember(initial.id) { mutableStateOf(initial.tags.joinToString(", ")) }
    var command by remember(initial.id) { mutableStateOf(initial.commandTemplate) }

    val tokens = remember(command) { WorkflowMarkdownParser.extractTokens(command) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .navigationBarsPadding()
            .testTag("workflow_editor_sheet")
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (isNew) "Save Workflow" else "Edit Workflow",
                color = palette.textPrimary,
                fontSize = 15.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close workflow editor", tint = palette.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        Spacer(modifier = Modifier.height(10.dp))

        EditorLabeledField("Name", name, { name = it }, "Deploy staging API", tag = "workflow_editor_name")
        Spacer(modifier = Modifier.height(8.dp))
        EditorLabeledField("Description (optional)", description, { description = it }, "Short summary", tag = "workflow_editor_desc")
        Spacer(modifier = Modifier.height(8.dp))
        EditorLabeledField("Tags (comma separated)", tagsRaw, { tagsRaw = it }, "git, docker, deploy", tag = "workflow_editor_tags")
        Spacer(modifier = Modifier.height(8.dp))

        Text("Command Template ({{placeholder=default}} supported)", color = palette.textSecondary, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(4.dp))
        BasicTextField(
            value = command,
            onValueChange = { command = it },
            textStyle = TextStyle(color = palette.textPrimary, fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 84.dp, max = 160.dp)
                .background(palette.surface, RoundedCornerShape(8.dp))
                .border(1.dp, accent.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                .padding(10.dp)
                .testTag("workflow_editor_command")
                .semantics { contentDescription = "Workflow command template" }
        )

        if (tokens.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Tokens: " + tokens.joinToString(", ") {
                    if (it.defaultValue.isEmpty()) it.name else "${it.name}=${it.defaultValue}"
                },
                color = accent,
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel, colors = ButtonDefaults.textButtonColors(contentColor = palette.textSecondary)) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            val valid = name.isNotBlank() && command.isNotBlank()
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (valid) accent else palette.surfaceInteractive,
                modifier = Modifier
                    .clickable(enabled = valid) {
                        val tags = tagsRaw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                        onSave(
                            initial.copy(
                                name = name.trim(),
                                description = description.trim(),
                                tags = tags,
                                commandTemplate = command.trimEnd('\r', '\n')
                            )
                        )
                    }
                    .testTag("workflow_editor_save")
                    .semantics { contentDescription = "Save workflow" }
            ) {
                Text(
                    text = "Save",
                    color = if (valid) palette.background else palette.textMuted,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorLabeledField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    tag: String
) {
    val palette = LocalWarpPalette.current
    val accent = MaterialTheme.colorScheme.primary
    Text(text = label, color = palette.textSecondary, fontSize = 12.sp)
    Spacer(modifier = Modifier.height(4.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.surface, RoundedCornerShape(8.dp))
            .border(1.dp, palette.borderSubtle, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = palette.textMuted, fontSize = 13.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = palette.textPrimary, fontSize = 13.sp),
            cursorBrush = SolidColor(accent),
            modifier = Modifier
                .fillMaxWidth()
                .testTag(tag)
                .semantics { contentDescription = label }
        )
    }
}
