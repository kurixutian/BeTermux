package com.termux.app

import android.content.Context
import android.view.ViewGroup
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.compose.outlinedMenuCard
import com.betermux.theme.LocalWarpPalette
import com.termux.R

enum class ChromeTabKind {
    TERMINAL,
    BROWSER,
    FILE,
    LOCALHOST,
}

data class ChromeTabItem(
    val key: String,
    val kind: ChromeTabKind,
    val index: Int,
    val handle: String?,
    val label: String,
    val iconRes: Int,
    val status: Int,
    val progress: Int,
    val showClose: Boolean,
)

data class LivePortItem(
    val port: Int,
    val processName: String?,
    val pid: Int,
)

interface TermuxChromeCallbacks {
    fun onOpenWorkspaceDrawer()
    fun onToggleRightDrawer()
    fun onOpenProjectDirectory()
    fun onOpenFileManager()
    fun onOpenLocalhost()
    fun onToggleExtraKeys()
    fun onReopenDeck()
    fun onQuickCommands()
    fun onNewBrowserTab()
    fun onOpenPackageManager()
    fun onClearTerminal()
    fun onUploadImage()
    fun onSpeechToText()
    fun onToggleAutocorrect()
    fun onTipsAndShortcuts()
    fun onOpenSettings()
    fun onNewTerminalTab()
    fun onSelectTab(item: ChromeTabItem)
    fun onCloseTab(item: ChromeTabItem)
    fun onRenameTerminalTab(handle: String)
    fun onCloseTerminalTab(handle: String)
    fun onReloadBrowserTab(index: Int)
    fun onCloseOtherBrowserTabs(index: Int)
    fun onCloseOtherFileTabs(index: Int)
    fun onShareFileTab(index: Int)
    fun onCopyFileTabPath(index: Int)
    fun onCloseLocalhostTab()
    fun onOpenLivePort(port: Int)
    fun onStopLivePort(port: Int)
}

/**
 * Owns the Compose top chrome (toolbar + unified tab strip) and bottom status line views,
 * keeping both non-focusable so hardware/soft keyboard input stays anchored on the terminal.
 */
class TermuxChromeController(
    context: Context,
    topChromeView: ComposeView,
    statusLineView: ComposeView,
    private val callbacks: TermuxChromeCallbacks,
) {
    private var tabs by mutableStateOf<List<ChromeTabItem>>(emptyList())
    private var livePorts by mutableStateOf<List<LivePortItem>>(emptyList())
    private var activeTabKey by mutableStateOf<String?>(null)
    private var showSessionTabs by mutableStateOf(true)
    private var filesOpen by mutableStateOf(false)
    private var rightDrawerEnabled by mutableStateOf(false)
    private var showDeckButtonState by mutableStateOf(false)
    private var topVisible by mutableStateOf(true)
    private var statusVisible by mutableStateOf(false)

    init {
        configureHostView(topChromeView)
        configureHostView(statusLineView)

        topChromeView.setContent {
            if (topVisible) {
                BeTermuxComposeTheme(context) {
                    TermuxTopChrome(
                        tabs = tabs,
                        livePorts = livePorts,
                        activeTabKey = activeTabKey,
                        showSessionTabs = showSessionTabs,
                        filesOpen = filesOpen,
                        rightDrawerEnabled = rightDrawerEnabled,
                        callbacks = callbacks,
                    )
                }
            }
        }

        statusLineView.setContent {
            if (statusVisible) {
                BeTermuxComposeTheme(context) {
                    TermuxStatusLine(
                        tabs = tabs,
                        livePorts = livePorts,
                        activeTabKey = activeTabKey,
                        filesOpen = filesOpen,
                        rightDrawerEnabled = rightDrawerEnabled,
                        showDeckButton = showDeckButtonState,
                        callbacks = callbacks,
                    )
                }
            }
        }
    }

    private fun configureHostView(view: ComposeView) {
        view.isFocusable = false
        view.isFocusableInTouchMode = false
        view.descendantFocusability = ViewGroup.FOCUS_BLOCK_DESCENDANTS
        view.setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
    }

    fun updateTabs(tabs: List<ChromeTabItem>, livePorts: List<LivePortItem>, activeTabKey: String?) {
        this.tabs = tabs.toList()
        this.livePorts = livePorts.toList()
        this.activeTabKey = activeTabKey
    }

    fun updateTabStatuses(keys: List<String>, statuses: IntArray, progresses: IntArray) {
        val count = minOf(keys.size, statuses.size, progresses.size)
        if (count == 0 || tabs.isEmpty()) return
        val indexByKey = HashMap<String, Int>(count)
        for (i in 0 until count) {
            indexByKey[keys[i]] = i
        }
        var changed = false
        val updated = tabs.map { tab ->
            val idx = indexByKey[tab.key]
            if (idx != null) {
                val nextStatus = statuses[idx]
                val nextProgress = progresses[idx]
                if (tab.status != nextStatus || tab.progress != nextProgress) {
                    changed = true
                    tab.copy(status = nextStatus, progress = nextProgress)
                } else {
                    tab
                }
            } else {
                tab
            }
        }
        if (changed) {
            tabs = updated
        }
    }

    fun setSessionTabsVisible(visible: Boolean) {
        showSessionTabs = visible
    }

    fun setRightDrawerState(open: Boolean, rightEnabled: Boolean) {
        filesOpen = open
        rightDrawerEnabled = rightEnabled
    }

    fun setShowDeckButton(show: Boolean) {
        showDeckButtonState = show
    }

    fun setVisibleModes(topVisible: Boolean, statusVisible: Boolean) {
        this.topVisible = topVisible
        this.statusVisible = statusVisible
    }
}

@Composable
internal fun TermuxTopChrome(
    tabs: List<ChromeTabItem>,
    livePorts: List<LivePortItem>,
    activeTabKey: String?,
    showSessionTabs: Boolean,
    filesOpen: Boolean,
    rightDrawerEnabled: Boolean,
    callbacks: TermuxChromeCallbacks,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWarpPalette.current
    Column(modifier = modifier.fillMaxWidth().background(palette.surface)) {
        TerminalToolbar(
            compact = false,
            filesOpen = filesOpen,
            rightDrawerEnabled = rightDrawerEnabled,
            onDrawer = callbacks::onOpenWorkspaceDrawer,
            onToggleRightDrawer = callbacks::onToggleRightDrawer,
            onOpenProjectDir = callbacks::onOpenProjectDirectory,
            onOpenFileManager = callbacks::onOpenFileManager,
            onLocalhost = callbacks::onOpenLocalhost,
            onKeys = callbacks::onToggleExtraKeys,
            onQuickCommands = callbacks::onQuickCommands,
            onNewBrowserTab = callbacks::onNewBrowserTab,
            onPackageManager = callbacks::onOpenPackageManager,
            onClearTerminal = callbacks::onClearTerminal,
            onUploadImage = callbacks::onUploadImage,
            onSpeechToText = callbacks::onSpeechToText,
            onToggleAutocorrect = callbacks::onToggleAutocorrect,
            onTipsAndShortcuts = callbacks::onTipsAndShortcuts,
            onSettings = callbacks::onOpenSettings,
        )
        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        if (showSessionTabs) {
            TermuxTabStripRow(
                tabs = tabs,
                livePorts = livePorts,
                activeTabKey = activeTabKey,
                includeAddButton = true,
                callbacks = callbacks,
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        }
    }
}

@Composable
internal fun TermuxStatusLine(
    tabs: List<ChromeTabItem>,
    livePorts: List<LivePortItem>,
    activeTabKey: String?,
    filesOpen: Boolean,
    rightDrawerEnabled: Boolean,
    showDeckButton: Boolean,
    callbacks: TermuxChromeCallbacks,
    modifier: Modifier = Modifier,
) {
    val palette = LocalWarpPalette.current
    Column(modifier = modifier.fillMaxWidth().background(palette.surface)) {
        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val actionMaxWidth = (maxWidth - 112.dp).coerceAtLeast(144.dp)
            Row(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TermuxTabStripRow(
                    tabs = tabs,
                    livePorts = livePorts,
                    activeTabKey = activeTabKey,
                    includeAddButton = false,
                    callbacks = callbacks,
                    modifier = Modifier.weight(1f).widthIn(min = 112.dp),
                )
                Row(
                    modifier = Modifier
                        .widthIn(max = actionMaxWidth)
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TerminalToolbar(
                        compact = true,
                        filesOpen = filesOpen,
                        rightDrawerEnabled = rightDrawerEnabled,
                        onDrawer = callbacks::onOpenWorkspaceDrawer,
                        onToggleRightDrawer = callbacks::onToggleRightDrawer,
                        onOpenProjectDir = callbacks::onOpenProjectDirectory,
                        onOpenFileManager = callbacks::onOpenFileManager,
                        onLocalhost = callbacks::onOpenLocalhost,
                        onKeys = callbacks::onToggleExtraKeys,
                        onQuickCommands = callbacks::onQuickCommands,
                        onNewBrowserTab = callbacks::onNewBrowserTab,
                        onPackageManager = callbacks::onOpenPackageManager,
                        onClearTerminal = callbacks::onClearTerminal,
                        onUploadImage = callbacks::onUploadImage,
                        onSpeechToText = callbacks::onSpeechToText,
                        onToggleAutocorrect = callbacks::onToggleAutocorrect,
                        onTipsAndShortcuts = callbacks::onTipsAndShortcuts,
                        onSettings = callbacks::onOpenSettings,
                    )
                    if (showDeckButton) {
                        IconButton(
                            onClick = callbacks::onReopenDeck,
                            modifier = Modifier.size(48.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_dual_screen),
                                contentDescription = stringResource(R.string.show_controls_second_screen),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
        HorizontalDivider(thickness = 1.dp, color = palette.borderSubtle)
    }
}

@Composable
internal fun TermuxTabStripRow(
    tabs: List<ChromeTabItem>,
    livePorts: List<LivePortItem>,
    activeTabKey: String?,
    includeAddButton: Boolean,
    callbacks: TermuxChromeCallbacks,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(activeTabKey, tabs.size) {
        val key = activeTabKey ?: return@LaunchedEffect
        val activeIdx = tabs.indexOfFirst { it.key == key }
        if (activeIdx >= 0) {
            listState.animateScrollToItem(activeIdx)
        }
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(tabs, key = { it.key }) { item ->
            ChromeTabPill(
                item = item,
                isActive = item.key == activeTabKey,
                callbacks = callbacks,
            )
        }
        items(livePorts.take(3), key = { "port_${it.port}" }) { srv ->
            LivePortChip(
                server = srv,
                callbacks = callbacks,
            )
        }
        if (includeAddButton) {
            item(key = "add_tab_button") {
                AddTabChip(callbacks = callbacks)
            }
        }
    }
}

@Composable
private fun ChromeTabPill(
    item: ChromeTabItem,
    isActive: Boolean,
    callbacks: TermuxChromeCallbacks,
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(9.dp)
    val statusColor = when (item.status) {
        TermuxActivity.TAB_STATUS_PROCESSING -> palette.warning
        TermuxActivity.TAB_STATUS_FINISHED -> palette.success
        else -> palette.textMuted
    }
    val background = if (isActive) palette.surfaceElevated else palette.surface
    val borderColor = if (isActive) MaterialTheme.colorScheme.primary else palette.borderSubtle
    val borderWidth = if (isActive) 1.5.dp else 1.dp
    val kindLabel = stringResource(
        when (item.kind) {
            ChromeTabKind.TERMINAL -> R.string.tab_kind_terminal
            ChromeTabKind.BROWSER -> R.string.tab_kind_browser
            ChromeTabKind.FILE -> R.string.tab_kind_file
            ChromeTabKind.LOCALHOST -> R.string.tab_kind_localhost
        }
    )
    val statusLabel = stringResource(
        when (item.status) {
            TermuxActivity.TAB_STATUS_PROCESSING -> R.string.tab_status_processing
            TermuxActivity.TAB_STATUS_FINISHED -> R.string.tab_status_finished
            else -> R.string.tab_status_standby
        }
    )
    val tabStateDescription = "$kindLabel, $statusLabel"

    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = 34.dp)
                .clip(shape)
                .combinedClickable(
                    onClick = { callbacks.onSelectTab(item) },
                    onLongClick = {
                        if (item.kind == ChromeTabKind.LOCALHOST) {
                            if (item.showClose) {
                                callbacks.onCloseLocalhostTab()
                            }
                        } else {
                            menuExpanded = true
                        }
                    },
                )
                .drawWithContent {
                    drawContent()
                    if (item.status == TermuxActivity.TAB_STATUS_PROCESSING) {
                        val barHeight = 2.dp.toPx()
                        val fraction = item.progress.coerceIn(12, 100) / 100f
                        drawRect(
                            color = if (item.progress in 1..99) palette.info else palette.warning,
                            topLeft = Offset(0f, size.height - barHeight),
                            size = Size(size.width * fraction, barHeight),
                        )
                    }
                }
                .semantics(mergeDescendants = true) {
                    selected = isActive
                    stateDescription = tabStateDescription
                },
            shape = shape,
            color = background,
            border = BorderStroke(borderWidth, borderColor),
        ) {
            Row(
                modifier = Modifier.padding(start = 9.dp, end = if (item.showClose) 5.dp else 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Canvas(Modifier.size(7.dp)) {
                    drawCircle(statusColor)
                }
                Icon(
                    painter = painterResource(item.iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (isActive) MaterialTheme.colorScheme.primary else palette.textSecondary,
                )
                Text(
                    text = item.label,
                    modifier = Modifier
                        .widthIn(max = 128.dp)
                        .padding(vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isActive) palette.textPrimary else palette.textSecondary,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.showClose) {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                        IconButton(
                            onClick = { callbacks.onCloseTab(item) },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_tab_close),
                                contentDescription = stringResource(R.string.close_tab),
                                tint = palette.textMuted,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }
        }

        if (item.kind != ChromeTabKind.LOCALHOST) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.outlinedMenuCard(),
            ) {
                val menuItems = when (item.kind) {
                    ChromeTabKind.TERMINAL -> listOf(
                        stringResource(R.string.menu_rename) to { item.handle?.let(callbacks::onRenameTerminalTab); Unit },
                        stringResource(R.string.menu_close) to { item.handle?.let(callbacks::onCloseTerminalTab); Unit },
                    )
                    ChromeTabKind.BROWSER -> listOf(
                        stringResource(R.string.menu_reload) to { callbacks.onReloadBrowserTab(item.index) },
                        stringResource(R.string.menu_new_browser_tab) to callbacks::onNewBrowserTab,
                        stringResource(R.string.menu_close_tab) to { callbacks.onCloseTab(item) },
                        stringResource(R.string.menu_close_others) to { callbacks.onCloseOtherBrowserTabs(item.index) },
                    )
                    ChromeTabKind.FILE -> listOf(
                        stringResource(R.string.menu_close_tab) to { callbacks.onCloseTab(item) },
                        stringResource(R.string.menu_close_others) to { callbacks.onCloseOtherFileTabs(item.index) },
                        stringResource(R.string.menu_share_file) to { callbacks.onShareFileTab(item.index) },
                        stringResource(R.string.menu_copy_path) to { callbacks.onCopyFileTabPath(item.index) },
                    )
                    ChromeTabKind.LOCALHOST -> emptyList()
                }
                OutlinedMenuItems(
                    items = menuItems,
                    onDismiss = { menuExpanded = false },
                )
            }
        }
    }
}

@Composable
private fun LivePortChip(
    server: LivePortItem,
    callbacks: TermuxChromeCallbacks,
) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(9.dp)
    val localhostPortLabel = stringResource(R.string.localhost_port_label)
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = 31.dp)
                .clip(shape)
                .combinedClickable(
                    onClick = { callbacks.onOpenLivePort(server.port) },
                    onLongClick = { menuExpanded = true },
                )
                .semantics(mergeDescendants = true) {
                    stateDescription = localhostPortLabel
                },
            shape = shape,
            color = palette.success.copy(alpha = 0.14f),
            border = BorderStroke(1.dp, palette.success.copy(alpha = 0.45f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Canvas(Modifier.size(7.dp)) {
                    drawCircle(palette.success)
                }
                Text(
                    text = ":${server.port}",
                    modifier = Modifier.padding(vertical = 4.dp),
                    color = palette.success,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            val header = ":${server.port}" +
                if (!server.processName.isNullOrBlank()) " • ${server.processName} (PID ${server.pid})" else ""
            DropdownMenuItem(
                text = {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.labelMedium,
                        color = palette.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                onClick = {},
                enabled = false,
            )
            OutlinedMenuItems(
                items = listOf(
                    stringResource(R.string.menu_stop_server) to { callbacks.onStopLivePort(server.port) },
                    stringResource(R.string.menu_open_dev_browser) to { callbacks.onOpenLivePort(server.port) },
                    stringResource(R.string.menu_open_localhost_tab) to callbacks::onOpenLocalhost,
                ),
                onDismiss = { menuExpanded = false },
                leadingDivider = true,
            )
        }
    }
}

@Composable
private fun AddTabChip(callbacks: TermuxChromeCallbacks) {
    val palette = LocalWarpPalette.current
    val shape = RoundedCornerShape(9.dp)
    val newTabDescription = stringResource(R.string.new_tab_action)
    var menuExpanded by remember { mutableStateOf(false) }

    Box {
        Surface(
            modifier = Modifier
                .heightIn(min = 36.dp)
                .widthIn(min = 36.dp)
                .clip(shape)
                .clickable { menuExpanded = true }
                .semantics { contentDescription = newTabDescription },
            shape = shape,
            color = palette.surface,
            border = BorderStroke(1.dp, palette.borderSubtle),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "+",
                    modifier = Modifier.clearAndSetSemantics {},
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                )
            }
        }

        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false },
            modifier = Modifier.outlinedMenuCard(),
        ) {
            OutlinedMenuItems(
                items = listOf(
                    stringResource(R.string.menu_new_terminal_tab) to callbacks::onNewTerminalTab,
                    stringResource(R.string.menu_new_dev_browser_tab) to callbacks::onNewBrowserTab,
                    stringResource(R.string.toolbar_localhost) to callbacks::onOpenLocalhost,
                    stringResource(R.string.files_menu_description) to callbacks::onOpenFileManager,
                    stringResource(R.string.menu_upload_image) to callbacks::onUploadImage,
                ),
                onDismiss = { menuExpanded = false },
            )
        }
    }
}
