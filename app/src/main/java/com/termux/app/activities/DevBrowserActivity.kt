package com.termux.app.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.newtermux.compose.NewTermuxComposeTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TreeSet
import java.util.function.BiConsumer
import java.util.function.Consumer

data class HistoryEntry(
    val title: String,
    val url: String,
    val timestamp: Long
)

private const val PREFS_BROWSER = "newtermux_dev_browser"
private const val PREF_KEY_HISTORY = "history_json"
private const val PREF_KEY_DESKTOP_DEFAULT = "desktop_default"
private const val PREF_KEY_JS_ENABLED = "js_enabled"
private const val PREF_KEY_DOM_STORAGE = "dom_storage"
private const val PREF_KEY_PAGE_ZOOM = "page_zoom"

object LocalhostPortScanner {
    private val COMMON_DEV_PORTS = intArrayOf(
        3000, 3001, 4000, 4173, 4200, 4321, 5000, 5173, 5174, 5500, 8000, 8080, 8081, 8787, 8888, 9000
    )

    @JvmStatic
    fun scanLivePorts(): List<Int> {
        val found = TreeSet<Int>()
        parseProcNetTcp("/proc/net/tcp", found)
        parseProcNetTcp("/proc/net/tcp6", found)
        for (port in COMMON_DEV_PORTS) {
            if (port in found) continue
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress("127.0.0.1", port), 15)
                    found.add(port)
                }
            } catch (_: Exception) {}
        }
        return found.toList()
    }

    private fun parseProcNetTcp(path: String, out: MutableSet<Int>) {
        try {
            val file = File(path)
            if (!file.canRead()) return
            file.forEachLine { line ->
                val parts = line.trim().split(Regex("\\s+"))
                if (parts.size >= 4 && parts[3].equals("0A", ignoreCase = true)) {
                    val localAddr = parts[1]
                    val ipHex = localAddr.substringBefore(':').uppercase(Locale.ROOT)
                    val portHex = localAddr.substringAfter(':', "")
                    val isLocalOrAny = ipHex == "0100007F" ||
                        ipHex == "00000000" ||
                        ipHex == "00000000000000000000000001000000" ||
                        ipHex == "00000000000000000000000000000000" ||
                        ipHex == "0000000000000000FFFF00000100007F"
                    if (isLocalOrAny && portHex.isNotEmpty()) {
                        val port = portHex.toIntOrNull(16)
                        if (port != null && port in 1024..65535) {
                            out.add(port)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }
}

private val historyDateFormat by lazy { SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()) }
private fun formatHistoryDate(timestamp: Long): String = try {
    historyDateFormat.format(Date(timestamp))
} catch (e: Exception) {
    ""
}

private fun isLocalDevUrl(urlStr: String?): Boolean {
    if (urlStr.isNullOrBlank()) return true
    return try {
        val uri = Uri.parse(urlStr)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        host == "localhost" ||
            host == "127.0.0.1" ||
            host == "::1" ||
            host == "10.0.2.2" ||
            host.endsWith(".local") ||
            host.startsWith("192.168.") ||
            host.startsWith("10.") ||
            (host.startsWith("172.") && host.split(".").getOrNull(1)?.toIntOrNull() in 16..31)
    } catch (_: Exception) {
        false
    }
}

private fun loadBrowserHistory(context: Context): MutableList<HistoryEntry> {
    val prefs = context.getSharedPreferences(PREFS_BROWSER, Context.MODE_PRIVATE)
    val jsonStr = prefs.getString(PREF_KEY_HISTORY, null) ?: return mutableListOf()
    return try {
        val arr = JSONArray(jsonStr)
        val list = mutableListOf<HistoryEntry>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                HistoryEntry(
                    title = obj.optString("title", "Untitled"),
                    url = obj.optString("url", ""),
                    timestamp = obj.optLong("timestamp", 0L)
                )
            )
        }
        list
    } catch (e: Exception) {
        mutableListOf()
    }
}

private fun saveBrowserHistory(context: Context, list: List<HistoryEntry>) {
    try {
        val arr = JSONArray()
        for (item in list.take(100)) {
            val obj = JSONObject()
            obj.put("title", item.title)
            obj.put("url", item.url)
            obj.put("timestamp", item.timestamp)
            arr.put(obj)
        }
        val prefs = context.getSharedPreferences(PREFS_BROWSER, Context.MODE_PRIVATE)
        prefs.edit().putString(PREF_KEY_HISTORY, arr.toString()).apply()
    } catch (ignored: Exception) {}
}

data class ElementInfo(
    val tagName: String,
    val selector: String,
    val outerHTML: String,
    val stylesJson: String
)

data class ConsoleLogItem(
    val level: ConsoleMessage.MessageLevel,
    val message: String,
    val sourceId: String,
    val lineNumber: Int
)

class DevBrowserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val startUrl = intent?.dataString
            ?: intent?.getStringExtra("INITIAL_URL")
            ?: "https://www.google.com"
        setContent {
            NewTermuxComposeTheme(this) {
                EmbeddedDevBrowser(
                    initialUrl = startUrl,
                    onClose = { finish() }
                )
            }
        }
    }
}

object DevBrowserBridge {
    @JvmStatic
    fun attachTo(
        composeView: ComposeView,
        context: Context,
        initialUrl: String,
        onTitleChanged: Consumer<String>,
        onLoadingChanged: Consumer<Boolean>,
        onClose: Runnable,
        onBackStateChanged: BiConsumer<Boolean, Runnable>,
        onReloadProvided: Consumer<Runnable>
    ) {
        composeView.setContent {
            NewTermuxComposeTheme(context) {
                EmbeddedDevBrowser(
                    initialUrl = initialUrl.ifBlank { "https://www.google.com" },
                    onTitleChanged = { onTitleChanged.accept(it) },
                    onLoadingChanged = { onLoadingChanged.accept(it) },
                    onClose = { onClose.run() },
                    onBackStateChanged = { canBack, goBack ->
                        onBackStateChanged.accept(canBack, Runnable { goBack() })
                    },
                    onReloadProvided = { reloadFn ->
                        onReloadProvided.accept(Runnable { reloadFn() })
                    }
                )
            }
        }
    }
}

private const val INSPECTOR_JS = """
(function() {
    if (window.__betermux_inspector_installed) return;
    window.__betermux_inspector_installed = true;
    window.__betermux_inspect_mode = false;

    var overlay = document.createElement('div');
    overlay.id = '__betermux_inspect_overlay';
    overlay.style.position = 'fixed';
    overlay.style.pointerEvents = 'none';
    overlay.style.border = '2px solid #8B5CF6';
    overlay.style.backgroundColor = 'rgba(139, 92, 246, 0.25)';
    overlay.style.zIndex = '2147483647';
    overlay.style.display = 'none';
    overlay.style.transition = 'all 0.08s ease';
    overlay.style.borderRadius = '3px';
    (document.body || document.documentElement).appendChild(overlay);

    function updateOverlay(el) {
        var r = el.getBoundingClientRect();
        overlay.style.left = r.left + 'px';
        overlay.style.top = r.top + 'px';
        overlay.style.width = r.width + 'px';
        overlay.style.height = r.height + 'px';
        overlay.style.display = 'block';
    }

    document.addEventListener('click', function(e) {
        if (!window.__betermux_inspect_mode) return;
        e.preventDefault();
        e.stopPropagation();
        var el = e.target;
        if (!el || el === overlay) return;

        updateOverlay(el);

        var outerHTML = el.outerHTML || '';
        var tagName = (el.tagName || '').toLowerCase();
        var id = el.id ? '#' + el.id : '';
        var classes = (typeof el.className === 'string' && el.className.trim()) 
            ? '.' + el.className.trim().split(/\s+/).join('.') : '';
        var selector = tagName + id + classes;

        var cs = window.getComputedStyle(el);
        var styles = {
            display: cs.display,
            width: cs.width,
            height: cs.height,
            color: cs.color,
            backgroundColor: cs.backgroundColor,
            fontSize: cs.fontSize,
            padding: cs.padding,
            margin: cs.margin
        };

        if (window.DevToolsBridge && window.DevToolsBridge.onElementSelected) {
            window.DevToolsBridge.onElementSelected(tagName, selector, outerHTML, JSON.stringify(styles));
        }
    }, true);

    window.__setInspectMode = function(enabled) {
        window.__betermux_inspect_mode = enabled;
        if (!enabled && overlay) overlay.style.display = 'none';
    };
})();
"""

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EmbeddedDevBrowser(
    initialUrl: String = "https://www.google.com",
    onTitleChanged: ((String) -> Unit)? = null,
    onLoadingChanged: ((Boolean) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onBackStateChanged: ((Boolean, () -> Unit) -> Unit)? = null,
    onReloadProvided: (((() -> Unit)) -> Unit)? = null
) {
    val context = LocalContext.current
    var urlText by remember { mutableStateOf(initialUrl) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isDesktopMode by remember { mutableStateOf(false) }
    val desktopUserAgent = remember {
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
    val mobileUserAgent = remember {
        try {
            WebSettings.getDefaultUserAgent(context)
                .replace("; wv", "")
                .replace("Version/4.0 ", "")
        } catch (_: Exception) {
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        }
    }

    var inspectMode by remember { mutableStateOf(false) }
    var selectedElement by remember { mutableStateOf<ElementInfo?>(null) }
    var showDevToolsSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val browserPrefs = remember { context.getSharedPreferences(PREFS_BROWSER, Context.MODE_PRIVATE) }
    var showHistorySheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    var defaultDesktopMode by remember {
        mutableStateOf(browserPrefs.getBoolean(PREF_KEY_DESKTOP_DEFAULT, false))
    }
    var jsEnabled by remember {
        mutableStateOf(browserPrefs.getBoolean(PREF_KEY_JS_ENABLED, true))
    }
    var domStorage by remember {
        mutableStateOf(browserPrefs.getBoolean(PREF_KEY_DOM_STORAGE, true))
    }
    var pageZoom by remember {
        mutableIntStateOf(browserPrefs.getInt(PREF_KEY_PAGE_ZOOM, 100).coerceIn(50, 300))
    }

    val livePorts = remember { mutableStateListOf<Int>() }
    LaunchedEffect(Unit) {
        while (true) {
            val detected = withContext(Dispatchers.IO) {
                LocalhostPortScanner.scanLivePorts()
            }
            if (detected != livePorts.toList()) {
                livePorts.clear()
                livePorts.addAll(detected)
            }
            delay(2000)
        }
    }

    val historyList = remember { mutableStateListOf<HistoryEntry>().apply { addAll(loadBrowserHistory(context)) } }

    val focusManager = LocalFocusManager.current
    var isUrlFocused by remember { mutableStateOf(false) }

    val consoleLogs = remember { mutableStateListOf<ConsoleLogItem>() }

    val fileChooserHolder = remember {
        object {
            var callback: ValueCallback<Array<Uri>>? = null
        }
    }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val cb = fileChooserHolder.callback
        fileChooserHolder.callback = null
        if (cb != null) {
            val uris: Array<Uri>? = if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val data = result.data
                val clipData = data?.clipData
                if (clipData != null && clipData.itemCount > 0) {
                    Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
                } else if (data?.data != null) {
                    arrayOf(data.data!!)
                } else {
                    null
                }
            } else {
                null
            }
            try {
                cb.onReceiveValue(uris)
            } catch (ignored: Exception) {
                cb.onReceiveValue(null)
            }
        }
    }

    val quickPorts = listOf(":3000", ":5173", ":8000", ":8080", ":5500", "localhost:3000")

    fun applyPageZoom(wv: WebView?, zoom: Int) {
        wv?.settings?.textZoom = zoom
        wv?.evaluateJavascript(
            "if (document.documentElement) { document.documentElement.style.zoom = '${zoom}%'; }",
            null
        )
    }

    fun updateZoom(newZoom: Int) {
        val clamped = newZoom.coerceIn(50, 300)
        pageZoom = clamped
        browserPrefs.edit().putInt(PREF_KEY_PAGE_ZOOM, clamped).apply()
        applyPageZoom(webViewRef, clamped)
    }

    fun evaluateInspection(enabled: Boolean) {
        webViewRef?.evaluateJavascript(INSPECTOR_JS) {
            webViewRef?.evaluateJavascript("__setInspectMode($enabled);", null)
        }
    }

    fun normalizeUrl(input: String): String {
        val trimmed = input.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith(":") -> "http://localhost$trimmed"
            trimmed.startsWith("localhost") -> "http://$trimmed"
            trimmed.startsWith("127.0.0.1") -> "http://$trimmed"
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            trimmed.isEmpty() -> "https://www.google.com"
            else -> "https://www.google.com/search?q=${Uri.encode(trimmed)}"
        }
    }

    fun loadUrl(input: String) {
        val finalUrl = normalizeUrl(input)
        urlText = finalUrl
        currentUrl = finalUrl
        webViewRef?.loadUrl(finalUrl)
    }

    LaunchedEffect(initialUrl) {
        val target = normalizeUrl(initialUrl)
        if (target != currentUrl) {
            urlText = target
            currentUrl = target
            webViewRef?.loadUrl(target)
        }
    }

    LaunchedEffect(inspectMode) {
        evaluateInspection(inspectMode)
    }

    LaunchedEffect(webViewRef) {
        onReloadProvided?.invoke {
            webViewRef?.reload()
        }
    }

    BackHandler {
        if (showHistorySheet) {
            showHistorySheet = false
        } else if (showSettingsSheet) {
            showSettingsSheet = false
        } else if (showDevToolsSheet) {
            showDevToolsSheet = false
        } else if (isUrlFocused) {
            focusManager.clearFocus()
        } else if (selectedElement != null) {
            selectedElement = null
            inspectMode = false
        } else if (canGoBack && webViewRef != null) {
            webViewRef?.goBack()
        } else {
            onClose?.invoke()
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Slim Browser Header Bar (compact height)
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back, Forward, Refresh on the left side of the URL input box
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Web Back",
                            modifier = Modifier.size(18.dp),
                            tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Web Forward",
                            modifier = Modifier.size(18.dp),
                            tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                    }

                    IconButton(
                        onClick = { webViewRef?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = "Reload",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    // Compact URL Input Box
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(32.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            BasicTextField(
                                value = urlText,
                                onValueChange = { urlText = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Uri,
                                    imeAction = ImeAction.Go
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        loadUrl(urlText)
                                        focusManager.clearFocus()
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (urlText.isEmpty()) {
                                        Text(
                                            "localhost:3000",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                            )
                                        )
                                    }
                                    innerTextField()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { focusState ->
                                        isUrlFocused = focusState.isFocused
                                    }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(3.dp))

                    // Inspect Mode Toggle
                    IconButton(
                        onClick = {
                            inspectMode = !inspectMode
                            if (!inspectMode) selectedElement = null
                            Toast.makeText(
                                context,
                                if (inspectMode) "Inspect mode ON: tap any element" else "Inspect mode OFF",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.TouchApp,
                            contentDescription = "Inspect Element",
                            modifier = Modifier.size(18.dp),
                            tint = if (inspectMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Desktop / Mobile Mode Toggle
                    IconButton(
                        onClick = {
                            isDesktopMode = !isDesktopMode
                            webViewRef?.settings?.apply {
                                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                                useWideViewPort = true
                                loadWithOverviewMode = isDesktopMode
                            }
                            webViewRef?.reload()
                            Toast.makeText(
                                context,
                                if (isDesktopMode) "Desktop mode enabled" else "Mobile mode enabled",
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Computer,
                            contentDescription = "Desktop Mode",
                            modifier = Modifier.size(18.dp),
                            tint = if (isDesktopMode) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // DevTools Sheet Toggle
                    IconButton(
                        onClick = { showDevToolsSheet = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Terminal,
                            contentDescription = "DevTools",
                            modifier = Modifier.size(18.dp),
                            tint = if (consoleLogs.any { it.level == ConsoleMessage.MessageLevel.ERROR })
                                MaterialTheme.colorScheme.error
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 3-Dots Menu Button (Zoom, History, Settings, Close)
                    var moreMenuOpen by remember { mutableStateOf(false) }

                    Box {
                        IconButton(
                            onClick = { moreMenuOpen = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = "More options",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = moreMenuOpen,
                            onDismissRequest = { moreMenuOpen = false }
                        ) {
                            // Page Zoom Controls (- | 100% | +)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Zoom",
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    IconButton(
                                        onClick = { updateZoom(pageZoom - 15) },
                                        enabled = pageZoom > 50,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out", modifier = Modifier.size(18.dp))
                                    }
                                    TextButton(
                                        onClick = { updateZoom(100) }
                                    ) {
                                        Text(
                                            "${pageZoom}%",
                                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace)
                                        )
                                    }
                                    IconButton(
                                        onClick = { updateZoom(pageZoom + 15) },
                                        enabled = pageZoom < 300,
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            DropdownMenuItem(
                                text = { Text("History") },
                                leadingIcon = {
                                    Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    showHistorySheet = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Browser Settings") },
                                leadingIcon = {
                                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                                },
                                onClick = {
                                    moreMenuOpen = false
                                    showSettingsSheet = true
                                }
                            )
                            if (onClose != null) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                DropdownMenuItem(
                                    text = { Text("Close Tab") },
                                    leadingIcon = {
                                        Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                                    },
                                    onClick = {
                                        moreMenuOpen = false
                                        onClose.invoke()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Live Localhost & Quick Port Presets Bar
            if (isUrlFocused || livePorts.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (livePorts.isNotEmpty()) "Live:" else "Ports:",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (livePorts.isNotEmpty()) Color(0xFF3FB950) else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (livePorts.isNotEmpty()) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.padding(end = 2.dp)
                        )
                        livePorts.forEach { portNum ->
                            val portStr = ":$portNum"
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = Color(0xFF1A2F23),
                                border = BorderStroke(1.dp, Color(0xFF3FB950)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable {
                                        loadUrl("http://localhost:$portNum")
                                        focusManager.clearFocus()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF3FB950))
                                    )
                                    Text(
                                        portStr,
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.SemiBold
                                        ),
                                        color = Color(0xFF3FB950)
                                    )
                                }
                            }
                        }
                        if (isUrlFocused) {
                            quickPorts.filter { preset ->
                                val num = preset.removePrefix(":").toIntOrNull()
                                num == null || num !in livePorts
                            }.forEach { port ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable {
                                            loadUrl(port)
                                            focusManager.clearFocus()
                                        }
                                ) {
                                    Text(
                                        port,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Web View Container
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT
                            )
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            webViewRef = this

                            try {
                                val cm = CookieManager.getInstance()
                                cm.setAcceptCookie(true)
                                cm.setAcceptThirdPartyCookies(this, true)
                            } catch (_: Exception) {}

                            settings.apply {
                                javaScriptEnabled = jsEnabled
                                javaScriptCanOpenWindowsAutomatically = true
                                domStorageEnabled = domStorage
                                databaseEnabled = true
                                allowFileAccess = true
                                allowContentAccess = true
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                useWideViewPort = true
                                loadWithOverviewMode = isDesktopMode || defaultDesktopMode
                                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                                mediaPlaybackRequiresUserGesture = false
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                textZoom = pageZoom
                                if (defaultDesktopMode) isDesktopMode = true
                                userAgentString = if (isDesktopMode) desktopUserAgent else mobileUserAgent
                            }

                            WebView.setWebContentsDebuggingEnabled(true)

                            addJavascriptInterface(object {
                                @JavascriptInterface
                                fun onElementSelected(
                                    tagName: String,
                                    selector: String,
                                    outerHTML: String,
                                    stylesJson: String
                                ) {
                                    post {
                                        selectedElement = ElementInfo(tagName, selector, outerHTML, stylesJson)
                                    }
                                }
                            }, "DevToolsBridge")

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    onLoadingChanged?.invoke(true)
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    canGoBack = view?.canGoBack() == true
                                    canGoForward = view?.canGoForward() == true
                                    onBackStateChanged?.invoke(canGoBack) { view?.goBack() }
                                    if (url != null) urlText = url
                                    val title = view?.title
                                    if (!title.isNullOrEmpty()) {
                                        onTitleChanged?.invoke(title)
                                    } else if (url != null) {
                                        val host = android.net.Uri.parse(url).host ?: "Dev Browser"
                                        onTitleChanged?.invoke(host)
                                    }
                                    onLoadingChanged?.invoke(false)
                                    evaluateInspection(inspectMode)
                                    if (pageZoom != 100) {
                                        applyPageZoom(view, pageZoom)
                                    }

                                    if (url != null && url != "about:blank") {
                                        val displayTitle = if (!title.isNullOrEmpty()) title else url
                                        if (historyList.firstOrNull()?.url != url) {
                                            historyList.add(0, HistoryEntry(displayTitle, url, System.currentTimeMillis()))
                                            if (historyList.size > 100) {
                                                historyList.removeAt(historyList.size - 1)
                                            }
                                            saveBrowserHistory(context, historyList)
                                        }
                                    }
                                }

                                override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                    val failingUrl = error?.url ?: view?.url
                                    if (isLocalDevUrl(failingUrl)) {
                                        handler?.proceed()
                                    } else {
                                        handler?.cancel()
                                        Toast.makeText(context, "Blocked untrusted SSL certificate", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    if (!title.isNullOrEmpty()) {
                                        onTitleChanged?.invoke(title)
                                    }
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    if (consoleMessage != null) {
                                        consoleLogs.add(
                                            0,
                                            ConsoleLogItem(
                                                level = consoleMessage.messageLevel(),
                                                message = consoleMessage.message(),
                                                sourceId = consoleMessage.sourceId() ?: "",
                                                lineNumber = consoleMessage.lineNumber()
                                            )
                                        )
                                    }
                                    return super.onConsoleMessage(consoleMessage)
                                }

                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?
                                ): Boolean {
                                    fileChooserHolder.callback?.onReceiveValue(null)
                                    fileChooserHolder.callback = filePathCallback

                                    val intent = try {
                                        fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "*/*"
                                        }
                                    } catch (e: Exception) {
                                        Intent(Intent.ACTION_GET_CONTENT).apply {
                                            addCategory(Intent.CATEGORY_OPENABLE)
                                            type = "*/*"
                                        }
                                    }

                                    if (fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE) {
                                        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                                    }

                                    return try {
                                        fileChooserLauncher.launch(intent)
                                        true
                                    } catch (e: Exception) {
                                        try {
                                            val fallback = Intent(Intent.ACTION_GET_CONTENT).apply {
                                                addCategory(Intent.CATEGORY_OPENABLE)
                                                type = "*/*"
                                            }
                                            fileChooserLauncher.launch(fallback)
                                            true
                                        } catch (e2: Exception) {
                                            fileChooserHolder.callback?.onReceiveValue(null)
                                            fileChooserHolder.callback = null
                                            false
                                        }
                                    }
                                }
                            }

                            loadUrl(currentUrl)
                        }
                    },
                    onRelease = { wv ->
                        try {
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.clearHistory()
                            wv.removeAllViews()
                            wv.destroy()
                        } catch (ignored: Exception) {}
                    }
                )

                // Inspect Mode Indicator Banner
                if (inspectMode && selectedElement == null) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp)
                    ) {
                        Text(
                            "Tap any element on the page to inspect and copy snippet",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // In-Page Element Inspector Bottom Bar (Orca ADE style)
            selectedElement?.let { el ->
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "<${el.tagName}>",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold
                                ),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                el.selector,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { selectedElement = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Close Inspector")
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("HTML Snippet", el.outerHTML))
                                    Toast.makeText(context, "Snippet copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Copy Snippet")
                            }

                            OutlinedButton(
                                onClick = {
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("CSS Selector", el.selector))
                                    Toast.makeText(context, "Selector copied!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy Selector")
                            }

                            OutlinedButton(
                                onClick = { showDevToolsSheet = true }
                            ) {
                                Text("DevTools")
                            }
                        }
                    }
                }
            }
        }
    }

    // DevTools Bottom Sheet (Console & DOM Details)
    if (showDevToolsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showDevToolsSheet = false },
            sheetState = sheetState
        ) {
            DevToolsContent(
                selectedElement = selectedElement,
                consoleLogs = consoleLogs,
                onClearConsole = { consoleLogs.clear() },
                onExecuteJs = { code ->
                    consoleLogs.add(
                        0,
                        ConsoleLogItem(ConsoleMessage.MessageLevel.LOG, "> $code", "REPL", 1)
                    )
                    webViewRef?.evaluateJavascript(code) { res ->
                        consoleLogs.add(
                            0,
                            ConsoleLogItem(ConsoleMessage.MessageLevel.DEBUG, "< $res", "REPL", 1)
                        )
                    }
                }
            )
        }
    }

    // Browsing History Sheet
    if (showHistorySheet) {
        ModalBottomSheet(
            onDismissRequest = { showHistorySheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(460.dp)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Browsing History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (historyList.isNotEmpty()) {
                        TextButton(
                            onClick = {
                                historyList.clear()
                                saveBrowserHistory(context, historyList)
                            }
                        ) {
                            Text("Clear All", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (historyList.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No history yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(historyList) { item ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                onClick = {
                                    currentUrl = item.url
                                    urlText = item.url
                                    webViewRef?.loadUrl(item.url)
                                    showHistorySheet = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            item.title.ifBlank { item.url },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            item.url,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            formatHistoryDate(item.timestamp),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            historyList.remove(item)
                                            saveBrowserHistory(context, historyList)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Remove from history",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Browser Settings Sheet
    if (showSettingsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Browser Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Web Preferences",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Default to Desktop Mode", style = MaterialTheme.typography.bodyMedium)
                        Text("Request desktop user-agent by default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = defaultDesktopMode,
                        onCheckedChange = {
                            defaultDesktopMode = it
                            isDesktopMode = it
                            browserPrefs.edit().putBoolean(PREF_KEY_DESKTOP_DEFAULT, it).apply()
                            webViewRef?.settings?.userAgentString = if (it) desktopUserAgent else mobileUserAgent
                            webViewRef?.reload()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("JavaScript", style = MaterialTheme.typography.bodyMedium)
                        Text("Enable JavaScript execution", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = jsEnabled,
                        onCheckedChange = {
                            jsEnabled = it
                            browserPrefs.edit().putBoolean(PREF_KEY_JS_ENABLED, it).apply()
                            webViewRef?.settings?.javaScriptEnabled = it
                            webViewRef?.reload()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("DOM Storage", style = MaterialTheme.typography.bodyMedium)
                        Text("Enable localStorage and sessionStorage", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(
                        checked = domStorage,
                        onCheckedChange = {
                            domStorage = it
                            browserPrefs.edit().putBoolean(PREF_KEY_DOM_STORAGE, it).apply()
                            webViewRef?.settings?.domStorageEnabled = it
                            webViewRef?.reload()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    "Clear Browsing Data",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = {
                        webViewRef?.clearCache(true)
                        CookieManager.getInstance().removeAllCookies(null)
                        WebStorage.getInstance().deleteAllData()
                        webViewRef?.clearHistory()
                        historyList.clear()
                        saveBrowserHistory(context, historyList)
                        Toast.makeText(context, "All browsing data cleared", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear All Browsing Data Now")
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            webViewRef?.clearCache(true)
                            Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Cache", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            CookieManager.getInstance().removeAllCookies(null)
                            Toast.makeText(context, "Cookies cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Cookies", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            WebStorage.getInstance().deleteAllData()
                            Toast.makeText(context, "Storage cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear Storage", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = {
                            historyList.clear()
                            saveBrowserHistory(context, historyList)
                            webViewRef?.clearHistory()
                            Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Clear History", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DevToolsContent(
    selectedElement: ElementInfo?,
    consoleLogs: List<ConsoleLogItem>,
    onClearConsole: () -> Unit,
    onExecuteJs: (String) -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var jsCode by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(460.dp)
            .padding(bottom = 16.dp)
    ) {
        PrimaryTabRow(selectedTabIndex = selectedTab) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("Console (${consoleLogs.size})") }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("Inspected DOM") }
            )
        }

        when (selectedTab) {
            0 -> {
                // Console Tab
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Console Output",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        IconButton(onClick = onClearConsole) {
                            Icon(Icons.Filled.Delete, contentDescription = "Clear Console", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF0D1117), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        if (consoleLogs.isEmpty()) {
                            item {
                                Text(
                                    "No console messages logged yet.",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = Color(0xFF8B949E)
                                )
                            }
                        } else {
                            items(consoleLogs) { log ->
                                val textColor = when (log.level) {
                                    ConsoleMessage.MessageLevel.ERROR -> Color(0xFFF85149)
                                    ConsoleMessage.MessageLevel.WARNING -> Color(0xFFD29922)
                                    ConsoleMessage.MessageLevel.DEBUG -> Color(0xFF58A6FF)
                                    else -> Color(0xFFE6EDF3)
                                }
                                Text(
                                    log.message,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = textColor,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // JS REPL Input
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = jsCode,
                            onValueChange = { jsCode = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text("Run JavaScript...", style = MaterialTheme.typography.bodySmall) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (jsCode.isNotBlank()) {
                                        onExecuteJs(jsCode)
                                        jsCode = ""
                                    }
                                }
                            )
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(
                            onClick = {
                                if (jsCode.isNotBlank()) {
                                    onExecuteJs(jsCode)
                                    jsCode = ""
                                }
                            }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = "Execute JS", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            1 -> {
                // DOM & Element Details Tab
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (selectedElement == null) {
                        Text(
                            "No element selected. Tap 'Inspect Element' on the top bar and tap any element on the page.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val el = selectedElement
                        Text(
                            "HTML Code Snippet",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF0D1117),
                            border = BorderStroke(1.dp, Color(0xFF30363D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    el.outerHTML,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    ),
                                    color = Color(0xFFE6EDF3)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Code Snippet", el.outerHTML))
                                Toast.makeText(context, "Snippet copied to clipboard!", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Copy Full HTML Snippet")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "Computed Styles",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        val stylePairs = remember(el.stylesJson) {
                            val list = mutableListOf<Pair<String, String>>()
                            try {
                                val json = JSONObject(el.stylesJson)
                                val keys = json.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    list.add(key to json.optString(key))
                                }
                            } catch (_: Exception) {}
                            list
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                if (stylePairs.isEmpty()) {
                                    Text("No styles available", style = MaterialTheme.typography.bodySmall)
                                } else {
                                    stylePairs.forEach { (key, value) ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                key,
                                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                value,
                                                style = MaterialTheme.typography.bodySmall.copy(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontWeight = FontWeight.Bold
                                                ),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
