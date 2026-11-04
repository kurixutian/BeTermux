package com.termux.app.activities

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.compose.outlinedMenuCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.zip.ZipFile

object FileTabBridge {
    @JvmStatic
    fun attachTo(
        composeView: ComposeView,
        context: Context,
        file: File,
        onClose: Runnable,
        onFileSaved: Runnable
    ) {
        composeView.setContent {
            NewTermuxComposeTheme(context) {
                FileTabViewer(
                    file = file,
                    onClose = { onClose.run() },
                    onFileSaved = { onFileSaved.run() }
                )
            }
        }
    }
}

enum class FileCategory {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    MARKDOWN,
    HTML,
    DOCX,
    PPTX,
    SPREADSHEET,
    CODE_TEXT,
    BINARY
}

fun categorizeFile(file: File): FileCategory {
    if (file.isDirectory) return FileCategory.BINARY
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val name = file.name.lowercase()

    return when {
        ext in listOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg", "ico") -> FileCategory.IMAGE
        ext in listOf("mp4", "mkv", "webm", "avi", "mov", "3gp", "m4v") -> FileCategory.VIDEO
        ext in listOf("mp3", "wav", "ogg", "m4a", "aac", "flac", "opus") -> FileCategory.AUDIO
        ext == "pdf" -> FileCategory.PDF
        ext in listOf("md", "markdown") -> FileCategory.MARKDOWN
        ext in listOf("html", "htm", "xhtml") -> FileCategory.HTML
        ext in listOf("docx", "doc") -> FileCategory.DOCX
        ext in listOf("pptx", "ppt") -> FileCategory.PPTX
        ext in listOf("xlsx", "xls", "csv", "tsv") -> FileCategory.SPREADSHEET
        ext in listOf(
            "txt", "json", "js", "ts", "tsx", "jsx", "py", "c", "cpp", "h", "hpp", "rs", "go",
            "java", "kt", "sh", "bash", "zsh", "xml", "yaml", "yml", "css", "scss", "sass", "properties",
            "conf", "ini", "env", "gradle", "sql", "log", "toml", "lock", "diff", "patch"
        ) || !name.contains(".") || file.length() <= 2 * 1024 * 1024 -> FileCategory.CODE_TEXT
        else -> FileCategory.BINARY
    }
}

@Composable
fun FileTabViewer(
    file: File,
    onClose: () -> Unit,
    onFileSaved: () -> Unit
) {
    val category = remember(file) { categorizeFile(file) }

    when (category) {
        FileCategory.IMAGE -> ImageTabViewer(file = file, onClose = onClose)
        FileCategory.VIDEO -> VideoTabViewer(file = file, onClose = onClose)
        FileCategory.AUDIO -> AudioTabViewer(file = file, onClose = onClose)
        FileCategory.PDF -> PdfTabViewer(file = file, onClose = onClose)
        FileCategory.MARKDOWN -> MarkdownTabViewer(file = file, onClose = onClose, onSaved = onFileSaved)
        FileCategory.HTML -> HtmlTabViewer(file = file, onClose = onClose, onSaved = onFileSaved)
        FileCategory.DOCX -> DocxTabViewer(file = file, onClose = onClose)
        FileCategory.PPTX -> PptxTabViewer(file = file, onClose = onClose)
        FileCategory.SPREADSHEET -> SpreadsheetTabViewer(file = file, onClose = onClose)
        FileCategory.CODE_TEXT -> CodeTextTabViewer(file = file, onClose = onClose, onSaved = onFileSaved)
        FileCategory.BINARY -> BinaryTabViewer(file = file, onClose = onClose)
    }
}

private fun decodeSafeBitmap(file: File, reqWidth: Int = 2048, reqHeight: Int = 2048): Bitmap? {
    return try {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeFile(file.absolutePath, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null

        var inSampleSize = 1
        if (options.outHeight > reqHeight || options.outWidth > reqWidth) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }

        val decodeOptions = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOptions)
    } catch (_: Throwable) {
        null
    }
}

// -------------------------------------------------------------------------------------------------
// 1. IMAGE VIEWER
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    var zoomScale by remember { mutableFloatStateOf(1f) }
    val isSvg = file.name.endsWith(".svg", ignoreCase = true)

    val bitmap = remember(file) {
        if (isSvg) null else decodeSafeBitmap(file)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (bitmap != null) "${bitmap.width} × ${bitmap.height} · ${formatSize(file.length())}" else formatSize(file.length()),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.5f) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out")
                    }
                    IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(4f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In")
                    }
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Image")
                    }
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115)),
            contentAlignment = Alignment.Center
        ) {
            if (isSvg) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.allowFileAccess = true
                            setBackgroundColor(0xFF0F1115.toInt())
                            loadUrl("file://${file.absolutePath}")
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else if (bitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = file.name,
                        modifier = Modifier
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                            .padding(16.dp)
                    )
                }
            } else {
                Text("Failed to decode image.", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 2. VIDEO VIEWER
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Video")
                    }
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        setMediaController(mc)
                        setVideoPath(file.absolutePath)
                        start()
                    }
                },
                onRelease = { videoView ->
                    try {
                        videoView.stopPlayback()
                    } catch (ignored: Exception) {}
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 3. AUDIO VIEWER
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AudioTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var currentPos by remember { mutableIntStateOf(0) }
    var duration by remember { mutableIntStateOf(1) }

    val mediaPlayer = remember(file) {
        try {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
            }
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(mediaPlayer) {
        if (mediaPlayer != null) {
            duration = mediaPlayer.duration.coerceAtLeast(1)
        }
        onDispose {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying && mediaPlayer != null) {
            currentPos = mediaPlayer.currentPosition
            delay(500)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { shareFile(context, file) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Share Audio")
                    }
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115))
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(100.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(54.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(file.name, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))

            Slider(
                value = currentPos.toFloat(),
                onValueChange = {
                    currentPos = it.toInt()
                    mediaPlayer?.seekTo(currentPos)
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatMs(currentPos), style = MaterialTheme.typography.labelSmall)
                Text(formatMs(duration), style = MaterialTheme.typography.labelSmall)
            }

            Spacer(Modifier.height(24.dp))

            IconButton(
                onClick = {
                    if (mediaPlayer != null) {
                        if (mediaPlayer.isPlaying) {
                            mediaPlayer.pause()
                            isPlaying = false
                        } else {
                            mediaPlayer.start()
                            isPlaying = true
                        }
                    }
                },
                modifier = Modifier
                    .size(64.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            ) {
                Icon(
                    if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 4. PDF VIEWER (Native Android PdfRenderer)
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PdfTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    var currentPageIndex by remember { mutableIntStateOf(0) }
    var totalPages by remember { mutableIntStateOf(0) }
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var pdfRenderer by remember { mutableStateOf<PdfRenderer?>(null) }
    var pfd by remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    var zoomScale by remember { mutableFloatStateOf(1f) }

    fun renderPage(index: Int) {
        val renderer = pdfRenderer ?: return
        if (index < 0 || index >= renderer.pageCount) return
        try {
            val page = renderer.openPage(index)
            val w = (page.width * 2).coerceAtLeast(300)
            val h = (page.height * 2).coerceAtLeast(400)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(android.graphics.Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            val oldBmp = currentBitmap
            currentBitmap = bmp
            oldBmp?.recycle()
            currentPageIndex = index
        } catch (e: Exception) {
            Toast.makeText(context, "Page render error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    DisposableEffect(file) {
        try {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(descriptor)
            pfd = descriptor
            pdfRenderer = renderer
            totalPages = renderer.pageCount
            renderPage(0)
        } catch (e: Exception) {
            Toast.makeText(context, "Cannot open PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        }

        onDispose {
            try {
                currentBitmap?.recycle()
                currentBitmap = null
                pdfRenderer?.close()
                pfd?.close()
            } catch (ignored: Exception) {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (totalPages > 0) "Page ${currentPageIndex + 1} of $totalPages" else "PDF Document",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (currentPageIndex > 0) renderPage(currentPageIndex - 1) },
                        enabled = currentPageIndex > 0
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Page")
                    }
                    IconButton(
                        onClick = { if (currentPageIndex < totalPages - 1) renderPage(currentPageIndex + 1) },
                        enabled = currentPageIndex < totalPages - 1
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Page")
                    }
                    IconButton(onClick = { zoomScale = (zoomScale - 0.25f).coerceAtLeast(0.75f) }) {
                        Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom Out")
                    }
                    IconButton(onClick = { zoomScale = (zoomScale + 0.25f).coerceAtMost(3f) }) {
                        Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom In")
                    }
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF1E222B)),
            contentAlignment = Alignment.Center
        ) {
            if (currentBitmap != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = currentBitmap!!.asImageBitmap(),
                        contentDescription = "PDF Page ${currentPageIndex + 1}",
                        modifier = Modifier
                            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
                            .padding(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                    )
                }
            } else {
                Text("Loading PDF...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 5. MARKDOWN & MERMAID VIEWER / EDITOR
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarkdownTabViewer(
    file: File,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var initialText by remember(file) { mutableStateOf(readFileSafe(file)) }
    var text by remember(file) { mutableStateOf(initialText) }
    val isModified = text != initialText
    var showPreview by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isModified) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                        Text(
                            if (showPreview) "Preview (Markdown + Mermaid)" else "Edit Markdown",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { showPreview = !showPreview }) {
                        Icon(
                            if (showPreview) Icons.Filled.Edit else Icons.Filled.Visibility,
                            contentDescription = if (showPreview) "Edit" else "Preview",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!showPreview) {
                        IconButton(
                            onClick = {
                                try {
                                    file.writeText(text)
                                    initialText = text
                                    onSaved()
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = isModified
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = "Save",
                                tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    } else {
                        IconButton(onClick = { shareFile(context, file) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (showPreview) {
                val html = remember(text) { buildMarkdownHtml(text) }
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            setBackgroundColor(0xFF0D1117.toInt())
                        }
                    },
                    update = { wv ->
                        wv.loadDataWithBaseURL("https://localhost/", html, "text/html", "UTF-8", null)
                    },
                    onRelease = { wv ->
                        try {
                            wv.stopLoading()
                            wv.loadUrl("about:blank")
                            wv.destroy()
                        } catch (ignored: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 6. HTML VIEWER / SOURCE EDITOR
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HtmlTabViewer(
    file: File,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    var initialText by remember(file) { mutableStateOf(readFileSafe(file)) }
    var text by remember(file) { mutableStateOf(initialText) }
    val isModified = text != initialText
    var showPreview by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isModified) {
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                        Text(if (showPreview) "HTML Render" else "HTML Source", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { showPreview = !showPreview }) {
                        Icon(
                            if (showPreview) Icons.Filled.Code else Icons.Filled.Visibility,
                            contentDescription = if (showPreview) "Source" else "Preview",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (!showPreview) {
                        IconButton(
                            onClick = {
                                try {
                                    file.writeText(text)
                                    initialText = text
                                    onSaved()
                                    Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                                }
                            },
                            enabled = isModified
                        ) {
                            Icon(
                                Icons.Filled.Save,
                                contentDescription = "Save",
                                tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                            )
                        }
                    } else {
                        IconButton(onClick = { openWithSystem(context, file) }) {
                            Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (showPreview) {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                allowFileAccess = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                            }
                            loadDataWithBaseURL("file://${file.parent}/", text, "text/html", "UTF-8", null)
                        }
                    },
                    update = { wv ->
                        wv.loadDataWithBaseURL("file://${file.parent}/", text, "text/html", "UTF-8", null)
                    },
                    onRelease = { wv ->
                        try {
                            wv.stopLoading()
                            wv.destroy()
                        } catch (ignored: Exception) {}
                    },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                )
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 7. DOCX VIEWER
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DocxTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val paragraphs = remember(file) { parseDocxText(file) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("Word Document · ${paragraphs.size} sections", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(paragraphs) { p ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.dp, Color(0xFF30363D)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        p,
                        style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp),
                        color = Color(0xFFE6EDF3),
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 8. PPTX VIEWER (Slide-by-slide presentation deck)
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PptxTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val slides = remember(file) { parsePptxSlides(file) }
    var currentSlideIdx by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (slides.isNotEmpty()) "Slide ${currentSlideIdx + 1} of ${slides.size}" else "PowerPoint Presentation",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { if (currentSlideIdx > 0) currentSlideIdx-- },
                        enabled = currentSlideIdx > 0
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous Slide")
                    }
                    IconButton(
                        onClick = { if (currentSlideIdx < slides.size - 1) currentSlideIdx++ },
                        enabled = currentSlideIdx < slides.size - 1
                    ) {
                        Icon(Icons.Filled.ChevronRight, contentDescription = "Next Slide")
                    }
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0D1117))
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (slides.isNotEmpty() && currentSlideIdx in slides.indices) {
                val slide = slides[currentSlideIdx]
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF161B22)),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "SLIDE ${currentSlideIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${currentSlideIdx + 1} / ${slides.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF8B949E)
                            )
                        }

                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 10.dp),
                            color = Color(0xFF30363D)
                        )

                        slide.forEach { line ->
                            Text(
                                line,
                                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 24.sp),
                                color = Color(0xFFF0F6FC),
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }
            } else {
                Text("No slides found in presentation.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 9. SPREADSHEET VIEWER (XLSX, CSV, TSV)
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpreadsheetTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current
    val ext = file.name.substringAfterLast('.', "").lowercase()
    val table = remember(file) {
        if (ext in listOf("csv", "tsv")) {
            parseDelimitedFile(file, if (ext == "tsv") '\t' else ',')
        } else {
            parseXlsxFile(file)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${table.size} rows · ${table.firstOrNull()?.size ?: 0} cols",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(onClick = { openWithSystem(context, file) }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Open Externally")
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115))
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                table.forEachIndexed { rowIdx, row ->
                    Row {
                        // Row index header
                        Surface(
                            color = Color(0xFF161B22),
                            border = BorderStroke(0.5.dp, Color(0xFF30363D)),
                            modifier = Modifier.size(width = 44.dp, height = 36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    "${rowIdx + 1}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF8B949E)
                                )
                            }
                        }

                        // Cells
                        row.forEach { cell ->
                            Surface(
                                color = if (rowIdx == 0) Color(0xFF1C2128) else Color(0xFF0D1117),
                                border = BorderStroke(0.5.dp, Color(0xFF30363D)),
                                modifier = Modifier.size(width = 120.dp, height = 36.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 6.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    Text(
                                        cell,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (rowIdx == 0) Color(0xFF58A6FF) else Color(0xFFE6EDF3),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
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

// -------------------------------------------------------------------------------------------------
// 10. CODE / TEXT VIEWER & EDITOR (With Syntax Highlighting & Coding Accessory Bar)
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CodeTextTabViewer(
    file: File,
    onClose: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val ext = remember(file.name) { file.name.substringAfterLast('.', "").lowercase() }
    val initialText = remember(file) { readFileSafe(file) }
    var textFieldValue by remember(file) { mutableStateOf(TextFieldValue(initialText, TextRange(0))) }
    val text = textFieldValue.text
    val isModified = text != initialText
    var lineWrap by remember { mutableStateOf(false) }

    val undoStack = remember { mutableStateListOf<String>() }
    val redoStack = remember { mutableStateListOf<String>() }

    fun updateText(newVal: TextFieldValue, addToUndo: Boolean = true) {
        if (addToUndo && newVal.text != textFieldValue.text) {
            undoStack.add(textFieldValue.text)
            if (undoStack.size > 50) undoStack.removeAt(0)
            redoStack.clear()
        }
        textFieldValue = newVal
    }

    fun insertSymbol(symbol: String) {
        val current = textFieldValue
        val sel = current.selection
        val before = current.text.substring(0, sel.min)
        val after = current.text.substring(sel.max)
        val newText = before + symbol + after
        val newCursor = sel.min + symbol.length
        updateText(TextFieldValue(newText, TextRange(newCursor)))
    }

    val codingSymbols = remember {
        listOf("Tab", "{", "}", "(", ")", "[", "]", "=>", ";", ":", "\"", "'", "<", ">", "/", "\\", "_", "$", "!", "?", "=", "+", "-", "&", "|")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(file.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isModified) {
                                Spacer(Modifier.width(6.dp))
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary))
                            }
                        }
                        Text(formatSize(file.length()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            try {
                                file.writeText(text)
                                onSaved()
                                Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Save error: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = isModified
                    ) {
                        Icon(
                            Icons.Filled.Save,
                            contentDescription = "Save",
                            tint = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                    IconButton(onClick = { lineWrap = !lineWrap }) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "Wrap",
                            tint = if (lineWrap) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )
        },
        bottomBar = {
            Column(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant)) {
                // Coding Accessory Bar (Undo, Redo, Tab, Symbols)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF14171E))
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Undo
                    IconButton(
                        onClick = {
                            if (undoStack.isNotEmpty()) {
                                val prev = undoStack.removeAt(undoStack.size - 1)
                                redoStack.add(textFieldValue.text)
                                updateText(TextFieldValue(prev, TextRange(prev.length)), addToUndo = false)
                            }
                        },
                        enabled = undoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Undo, contentDescription = "Undo", tint = if (undoStack.isNotEmpty()) Color.White else Color(0xFF555555), modifier = Modifier.size(16.dp))
                    }

                    // Redo
                    IconButton(
                        onClick = {
                            if (redoStack.isNotEmpty()) {
                                val next = redoStack.removeAt(redoStack.size - 1)
                                undoStack.add(textFieldValue.text)
                                updateText(TextFieldValue(next, TextRange(next.length)), addToUndo = false)
                            }
                        },
                        enabled = redoStack.isNotEmpty(),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Redo, contentDescription = "Redo", tint = if (redoStack.isNotEmpty()) Color.White else Color(0xFF555555), modifier = Modifier.size(16.dp))
                    }

                    // Symbols
                    codingSymbols.forEach { sym ->
                        Surface(
                            color = Color(0xFF21262D),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(0.5.dp, Color(0xFF30363D)),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    if (sym == "Tab") insertSymbol("  ") else insertSymbol(sym)
                                }
                        ) {
                            Text(
                                sym,
                                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3)),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFF30363D))

                // Status info
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val lineCount = remember(text) { text.count { it == '\n' } + 1 }
                    Text("$lineCount lines · ${text.length} chars", style = MaterialTheme.typography.bodySmall)
                    Text(if (isModified) "● Modified" else "UTF-8", style = MaterialTheme.typography.bodySmall, color = if (isModified) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    ) { padding ->
        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color(0xFF0F1115))
        ) {
            val lines = remember(text) { text.lines() }
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(vScroll)
            ) {
                // Line numbers gutter
                Column(
                    modifier = Modifier
                        .background(Color(0xFF14171D))
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    lines.indices.forEach { index ->
                        Text(
                            text = "${index + 1}",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = Color(0xFF5A6270),
                            lineHeight = 20.sp
                        )
                    }
                }

                // Text / Code editor input with Syntax Highlighting
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (!lineWrap) Modifier.horizontalScroll(hScroll) else Modifier)
                        .padding(horizontal = 12.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = textFieldValue,
                        onValueChange = { updateText(it) },
                        visualTransformation = remember(ext) { CodeSyntaxTransformation(ext) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            color = Color(0xFFECECEC),
                            lineHeight = 20.sp
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }
    }
}


// -------------------------------------------------------------------------------------------------
// 11. BINARY / GENERIC VIEWER
// -------------------------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BinaryTabViewer(file: File, onClose: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close Tab")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Filled.Description,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))
            Text(file.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(formatSize(file.length()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { openWithSystem(context, file) }) {
                    Text("Open in App")
                }
                Button(onClick = { shareFile(context, file) }) {
                    Text("Share")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// SYNTAX HIGHLIGHTING ENGINE
// -------------------------------------------------------------------------------------------------
class CodeSyntaxTransformation(private val ext: String) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlightSyntax(text.text, ext), OffsetMapping.Identity)
    }
}

fun highlightSyntax(code: String, ext: String): AnnotatedString {
    val builder = AnnotatedString.Builder(code)
    if (code.length > 80_000) return builder.toAnnotatedString()

    val kwColor = Color(0xFFFF7B72)
    val strColor = Color(0xFF7EE787)
    val numColor = Color(0xFF79C0FF)
    val commentColor = Color(0xFF8B949E)
    val typeColor = Color(0xFFFFA657)
    val fnColor = Color(0xFFD2A8FF)

    try {
        // Strings
        val strRegex = Regex("(\"[^\"]*\"|'[^']*'|`[^`]*`)")
        for (m in strRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = strColor), m.range.first, m.range.last + 1)
        }

        // Numbers
        val numRegex = Regex("\\b\\d+(\\.\\d+)?\\b")
        for (m in numRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = numColor), m.range.first, m.range.last + 1)
        }

        // Keywords
        val kwRegex = Regex("\\b(const|let|var|function|def|class|import|export|from|return|if|else|elif|for|while|try|catch|finally|async|await|pub|fn|val|fun|struct|impl|type|interface|enum|public|private|protected|static|void|package|switch|case|break|continue|new|this|super|throw|throws|yield|match|select|where|true|false|null|undefined|None|True|False|nil)\\b")
        for (m in kwRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = kwColor, fontWeight = FontWeight.Bold), m.range.first, m.range.last + 1)
        }

        // Types
        val typeRegex = Regex("\\b(String|Int|Boolean|Float|Double|Array|List|Map|Set|Promise|Record|object|number|string|boolean|any|void|never|unknown)\\b")
        for (m in typeRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = typeColor), m.range.first, m.range.last + 1)
        }

        // Functions
        val fnRegex = Regex("\\b([a-zA-Z_][a-zA-Z0-9_]*)(?=\\s*\\()")
        for (m in fnRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = fnColor), m.range.first, m.range.last + 1)
        }

        // Comments
        val commentRegex = if (ext in listOf("py", "sh", "bash", "zsh", "yaml", "yml")) {
            Regex("#.*")
        } else {
            Regex("(//.*|/\\*[\\s\\S]*?\\*/)")
        }
        for (m in commentRegex.findAll(code)) {
            builder.addStyle(SpanStyle(color = commentColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic), m.range.first, m.range.last + 1)
        }
    } catch (ignored: Exception) {}

    return builder.toAnnotatedString()
}

// -------------------------------------------------------------------------------------------------
// PARSER & UTILITY HELPERS
// -------------------------------------------------------------------------------------------------

private fun readFileSafe(file: File): String = try {
    file.readText()
} catch (e: Exception) {
    ""
}

private fun formatMs(ms: Int): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024))
}

private fun openWithSystem(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun shareFile(context: Context, file: File) {
    try {
        val uri: Uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot share: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun parseDocxText(file: File): List<String> {
    val list = mutableListOf<String>()
    try {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: return listOf("No document.xml in docx.")
            val xml = zip.getInputStream(entry).bufferedReader().readText()
            val paraRegex = Regex("<w:p[ >](.*?)</w:p>", RegexOption.DOT_MATCHES_ALL)
            val textRegex = Regex("<w:t[^>]*>(.*?)</w:t>")
            paraRegex.findAll(xml).forEach { match ->
                val pText = textRegex.findAll(match.value).map { it.groupValues[1] }.joinToString("")
                if (pText.isNotBlank()) list.add(pText.trim())
            }
        }
    } catch (e: Exception) {
        list.add("Error reading docx: ${e.message}")
    }
    return if (list.isEmpty()) listOf("Empty document.") else list
}

private fun parsePptxSlides(file: File): List<List<String>> {
    val slides = mutableListOf<List<String>>()
    try {
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { it.name.startsWith("ppt/slides/slide") && it.name.endsWith(".xml") }
                .sortedBy { entry ->
                    val num = entry.name.removePrefix("ppt/slides/slide").removeSuffix(".xml").toIntOrNull() ?: 0
                    num
                }
                .toList()

            val textRegex = Regex("<a:t>(.*?)</a:t>")
            for (entry in entries) {
                val xml = zip.getInputStream(entry).bufferedReader().readText()
                val lines = textRegex.findAll(xml).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
                if (lines.isNotEmpty()) {
                    slides.add(lines)
                }
            }
        }
    } catch (e: Exception) {
        slides.add(listOf("Error reading presentation: ${e.message}"))
    }
    return slides
}

private fun parseDelimitedFile(file: File, delimiter: Char): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    try {
        file.forEachLine { line ->
            if (line.isNotBlank()) {
                rows.add(line.split(delimiter).map { it.trim() })
            }
        }
    } catch (e: Exception) {
        rows.add(listOf("Error: ${e.message}"))
    }
    return rows
}

private fun parseXlsxFile(file: File): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    try {
        ZipFile(file).use { zip ->
            val strings = mutableListOf<String>()
            val ssEntry = zip.getEntry("xl/sharedStrings.xml")
            if (ssEntry != null) {
                val xml = zip.getInputStream(ssEntry).bufferedReader().readText()
                Regex("<t[^>]*>(.*?)</t>").findAll(xml).forEach { strings.add(it.groupValues[1]) }
            }

            val sheetEntry = zip.getEntry("xl/worksheets/sheet1.xml") ?: return listOf(listOf("No sheet1.xml in workbook."))
            val xml = zip.getInputStream(sheetEntry).bufferedReader().readText()
            val rowRegex = Regex("<row[^>]*>(.*?)</row>", RegexOption.DOT_MATCHES_ALL)
            val cellRegex = Regex("<c[^>]*?(?:t=\"(s)\")?[^>]*?>.*?<v>(.*?)</v>.*?</c>", RegexOption.DOT_MATCHES_ALL)

            rowRegex.findAll(xml).forEach { rMatch ->
                val cells = mutableListOf<String>()
                cellRegex.findAll(rMatch.value).forEach { cMatch ->
                    val isShared = cMatch.groupValues[1] == "s"
                    val v = cMatch.groupValues[2]
                    if (isShared) {
                        val idx = v.toIntOrNull() ?: -1
                        cells.add(if (idx in strings.indices) strings[idx] else v)
                    } else {
                        cells.add(v)
                    }
                }
                if (cells.isNotEmpty()) rows.add(cells)
            }
        }
    } catch (e: Exception) {
        rows.add(listOf("Error parsing xlsx: ${e.message}"))
    }
    return if (rows.isEmpty()) listOf(listOf("Empty sheet.")) else rows
}

private fun buildMarkdownHtml(markdown: String): String {
    val escapedJson = JSONObject.quote(markdown)
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=yes">
        <script src="https://cdn.jsdelivr.net/npm/marked/marked.min.js"></script>
        <script src="https://cdn.jsdelivr.net/npm/mermaid@10/dist/mermaid.min.js"></script>
        <style>
        body {
            background-color: #0d1117;
            color: #c9d1d9;
            font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
            font-size: 15px;
            line-height: 1.6;
            margin: 0;
            padding: 16px;
            word-wrap: break-word;
        }
        h1, h2, h3, h4, h5, h6 {
            color: #58a6ff;
            font-weight: 600;
            margin-top: 24px;
            margin-bottom: 12px;
            border-bottom: 1px solid #21262d;
            padding-bottom: 6px;
        }
        code {
            background-color: #161b22;
            color: #ff7b72;
            padding: 2px 6px;
            border-radius: 4px;
            font-family: monospace;
            font-size: 85%;
        }
        pre {
            background-color: #161b22;
            border: 1px solid #30363d;
            border-radius: 6px;
            padding: 12px;
            overflow-x: auto;
        }
        pre code {
            background-color: transparent;
            color: #c9d1d9;
            padding: 0;
        }
        table {
            border-collapse: collapse;
            width: 100%;
            margin: 16px 0;
        }
        th, td {
            border: 1px solid #30363d;
            padding: 8px 12px;
        }
        th {
            background-color: #161b22;
            color: #f0f6fc;
        }
        blockquote {
            border-left: 4px solid #388bfd;
            color: #8b949e;
            margin: 0;
            padding-left: 12px;
        }
        a {
            color: #58a6ff;
            text-decoration: none;
        }
        .mermaid {
            background: #161b22;
            border: 1px solid #30363d;
            border-radius: 8px;
            padding: 16px;
            margin: 16px 0;
            display: flex;
            justify-content: center;
            overflow-x: auto;
        }
        </style>
        </head>
        <body>
        <div id="content"></div>
        <script>
        const rawMd = $escapedJson;
        try {
            if (typeof marked !== 'undefined') {
                const renderer = new marked.Renderer();
                const origCode = renderer.code.bind(renderer);
                renderer.code = function(code, lang, escaped) {
                    if (lang === 'mermaid') {
                        return '<div class="mermaid">' + code + '</div>';
                    }
                    return origCode(code, lang, escaped);
                };
                document.getElementById('content').innerHTML = marked.parse(rawMd, { renderer: renderer });
                if (typeof mermaid !== 'undefined') {
                    mermaid.initialize({ startOnLoad: false, theme: 'dark' });
                    mermaid.run({ querySelector: '.mermaid' });
                }
            } else {
                document.getElementById('content').innerHTML = '<pre>' + rawMd.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</pre>';
            }
        } catch (e) {
            document.getElementById('content').innerHTML = '<pre>' + rawMd.replace(/&/g, '&amp;').replace(/</g, '&lt;') + '</pre>';
        }
        </script>
        </body>
        </html>
    """.trimIndent()
}
