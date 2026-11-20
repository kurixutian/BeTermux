package com.termux.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.betermux.compose.MenuItemDivider
import com.betermux.compose.outlinedMenuCard
import com.betermux.theme.LocalWarpPalette
import com.termux.R

@Composable
internal fun TerminalToolbar(
    compact: Boolean,
    filesOpen: Boolean,
    rightDrawerEnabled: Boolean,
    onDrawer: () -> Unit,
    onToggleRightDrawer: () -> Unit,
    onOpenProjectDir: () -> Unit,
    onOpenFileManager: () -> Unit,
    onLocalhost: () -> Unit,
    onKeys: () -> Unit,
    onQuickCommands: () -> Unit,
    onNewBrowserTab: () -> Unit,
    onPackageManager: () -> Unit,
    onClearTerminal: () -> Unit,
    onUploadImage: () -> Unit,
    onSpeechToText: () -> Unit,
    onToggleAutocorrect: () -> Unit,
    onTipsAndShortcuts: () -> Unit,
    onSettings: () -> Unit,
) {
    val palette = LocalWarpPalette.current
    var filesMenuOpen by remember { mutableStateOf(false) }
    var moreMenuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(rightDrawerEnabled) {
        if (rightDrawerEnabled) {
            filesMenuOpen = false
        }
    }

    Row(
        modifier = Modifier
            .then(if (compact) Modifier.wrapContentWidth() else Modifier.fillMaxWidth())
            .heightIn(min = 48.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!compact) {
            ToolbarAction(
                icon = R.drawable.ic_tab_drawer,
                description = stringResource(R.string.toolbar_workspace_drawer),
                tint = palette.textPrimary,
                onClick = onDrawer,
            )
            Spacer(Modifier.weight(1f))
        }
        ToolbarAction(
            icon = R.drawable.ic_keyboard,
            description = stringResource(R.string.toolbar_extra_keys),
            tint = palette.textPrimary,
            onClick = onKeys,
        )
        ToolbarAction(
            icon = R.drawable.ic_localhost,
            description = stringResource(R.string.toolbar_localhost),
            tint = palette.textPrimary,
            onClick = onLocalhost,
        )

        val filesDescriptionRes = when {
            rightDrawerEnabled && filesOpen -> R.string.close_project_files_drawer
            rightDrawerEnabled -> R.string.open_project_files_drawer
            else -> R.string.files_menu_description
        }
        val filesIconRes = if (rightDrawerEnabled && filesOpen) {
            R.drawable.ic_folder_open
        } else {
            R.drawable.ic_folder
        }
        val filesTint = if (rightDrawerEnabled && filesOpen) {
            MaterialTheme.colorScheme.primary
        } else {
            palette.textPrimary
        }

        Box {
            ToolbarAction(
                icon = filesIconRes,
                description = stringResource(filesDescriptionRes),
                tint = filesTint,
                onClick = {
                    if (rightDrawerEnabled) {
                        onToggleRightDrawer()
                    } else {
                        filesMenuOpen = !filesMenuOpen
                    }
                },
            )
            DropdownMenu(
                expanded = filesMenuOpen && !rightDrawerEnabled,
                onDismissRequest = { filesMenuOpen = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                OutlinedMenuItems(
                    items = listOf(
                        stringResource(R.string.menu_project_directory) to onOpenProjectDir,
                        stringResource(R.string.menu_file_manager) to onOpenFileManager,
                    ),
                    onDismiss = { filesMenuOpen = false },
                )
            }
        }

        Box {
            ToolbarAction(
                icon = R.drawable.ic_more_vert,
                description = stringResource(R.string.toolbar_more_options),
                tint = palette.textPrimary,
                onClick = { moreMenuOpen = !moreMenuOpen },
            )
            DropdownMenu(
                expanded = moreMenuOpen,
                onDismissRequest = { moreMenuOpen = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                OutlinedMenuItems(
                    items = listOf(
                        stringResource(R.string.menu_quick_commands) to onQuickCommands,
                        stringResource(R.string.toolbar_localhost) to onLocalhost,
                        stringResource(R.string.menu_new_dev_browser_tab) to onNewBrowserTab,
                        stringResource(R.string.menu_package_manager) to onPackageManager,
                        stringResource(R.string.menu_clear_terminal) to onClearTerminal,
                        stringResource(R.string.menu_upload_image) to onUploadImage,
                        stringResource(R.string.menu_speech_to_text) to onSpeechToText,
                        stringResource(R.string.menu_toggle_autocorrect) to onToggleAutocorrect,
                        stringResource(R.string.menu_tips_and_shortcuts) to onTipsAndShortcuts,
                        stringResource(R.string.action_open_settings) to onSettings,
                    ),
                    onDismiss = { moreMenuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun ToolbarAction(icon: Int, description: String, tint: Color, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
internal fun OutlinedMenuItems(
    items: List<Pair<String, () -> Unit>>,
    onDismiss: () -> Unit,
    leadingDivider: Boolean = false,
) {
    val palette = LocalWarpPalette.current
    items.forEachIndexed { index, (label, action) ->
        if (leadingDivider || index > 0) {
            MenuItemDivider()
        }
        DropdownMenuItem(
            text = {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textPrimary,
                )
            },
            onClick = {
                onDismiss()
                action()
            },
        )
    }
}
