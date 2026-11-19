package com.betermux.features

import android.content.Context
import android.widget.Toast
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.theme.LocalWarpPalette
import com.betermux.theme.WarpBackground
import com.betermux.theme.WarpCard
import com.betermux.theme.WarpTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Supplier

object LocalhostServersBridge {
    @JvmStatic
    fun attachTo(
        composeView: ComposeView,
        context: Context,
        onOpenUrl: Consumer<String>,
        onServersChanged: Consumer<List<LocalhostServerManager.LocalhostServer>>? = null,
        onClose: Runnable? = null,
        onStartServer: BiConsumer<String, Int>? = null,
        defaultProjectPath: Supplier<String>? = null,
    ) {
        composeView.setContent {
            BeTermuxComposeTheme(context) {
                LocalhostServersTab(
                    onOpenUrl = { onOpenUrl.accept(it) },
                    onServersChanged = onServersChanged?.let { cb -> { list -> cb.accept(list) } },
                    onClose = onClose?.let { cb -> { cb.run() } },
                    onStartServer = onStartServer?.let { cb -> { path, port -> cb.accept(path, port) } },
                    defaultProjectPath = defaultProjectPath?.let { sup -> { sup.get() } },
                )
            }
        }
    }
}

/**
 * In-tab Localhost manager screen embedded inside TermuxActivity.
 * Shows live listening development ports with process/PID details,
 * one-tap browser launch, server stop actions, and starting a static
 * file server (python -m http.server) for a project folder on a chosen port.
 */
@Composable
fun LocalhostServersTab(
    onOpenUrl: (String) -> Unit,
    onServersChanged: ((List<LocalhostServerManager.LocalhostServer>) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onStartServer: ((String, Int) -> Unit)? = null,
    defaultProjectPath: (() -> String)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<LocalhostServerManager.LocalhostServer>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var stoppingPort by remember { mutableStateOf<Int?>(null) }
    var scanError by remember { mutableStateOf<String?>(null) }
    val palette = LocalWarpPalette.current
    var showStartDialog by remember { mutableStateOf(false) }
    var startPath by remember { mutableStateOf("") }
    var startPort by remember { mutableStateOf("") }

    suspend fun reload() {
        refreshing = true
        scanError = null
        try {
            val latest = withContext(Dispatchers.IO) { LocalhostServerManager.scanServers() }
            servers = latest
            onServersChanged?.invoke(latest)
        } catch (e: Exception) {
            scanError = e.message ?: "Failed to scan servers"
        } finally {
            refreshing = false
        }
    }

    DisposableEffect(Unit) {
        val job = scope.launch {
            while (true) {
                reload()
                delay(2500L)
            }
        }
        onDispose { job.cancel() }
    }

    WarpBackground(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            WarpTopBar(
                title = "Localhost",
                subtitle = if (servers.isEmpty()) "No active servers" else "${servers.size} live",
                actions = {
                    if (onStartServer != null) {
                        IconButton(
                            onClick = {
                                val base = defaultProjectPath?.invoke() ?: ""
                                startPath = base
                                val remembered = BeTermuxSettings.getLocalhostPort(context, base)
                                startPort = (if (remembered > 0) remembered else 8000).toString()
                                showStartDialog = true
                            },
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Start localhost server")
                        }
                    }
                    IconButton(
                        onClick = { scope.launch { reload() } },
                        enabled = !refreshing,
                    ) {
                        if (refreshing && servers.isEmpty()) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Refresh servers")
                        }
                    }
                    if (onClose != null) {
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close tab")
                        }
                    }
                },
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                    val error = scanError
                    if (error != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.Error,
                                contentDescription = "Server scan error",
                                tint = palette.error,
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Server scan failed",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { scope.launch { reload() } },
                                enabled = !refreshing,
                            ) {
                                Text("Retry")
                            }
                        }
                    } else if (servers.isEmpty() && !refreshing) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 28.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Icon(
                                Icons.Filled.Dns,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.size(44.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "No localhost servers running",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Tap + to start a file server for a project folder, or start one in any terminal tab (e.g. npm run dev, vite, python -m http.server) to open or stop it here.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(servers, key = { "${it.port}:${it.pid ?: -1}" }) { server ->
                                val isStopping = stoppingPort == server.port
                                WarpCard(modifier = Modifier.fillMaxWidth()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 14.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(9.dp)
                                                .clip(CircleShape)
                                                .background(LocalWarpPalette.current.success),
                                        )
                                        Spacer(Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            ) {
                                                Text(
                                                    ":${server.port}",
                                                    style = MaterialTheme.typography.titleSmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold,
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary,
                                                )
                                                Text(
                                                    server.processName,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                if (server.pid != null) {
                                                    Text(
                                                        "PID ${server.pid}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontFamily = FontFamily.Monospace,
                                                            fontSize = 10.sp,
                                                        ),
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    )
                                                }
                                            }
                                            if (server.commandLine.isNotBlank()) {
                                                Spacer(Modifier.height(3.dp))
                                                Text(
                                                    server.commandLine,
                                                    style = MaterialTheme.typography.bodySmall.copy(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 11.sp,
                                                    ),
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        FilledTonalButton(
                                            onClick = { onOpenUrl("http://127.0.0.1:${server.port}") },
                                            shape = RoundedCornerShape(10.dp),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.OpenInNew,
                                                contentDescription = "Open in browser",
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text("Open", fontSize = 12.sp)
                                        }
                                        Spacer(Modifier.width(6.dp))
                                        Button(
                                            enabled = !isStopping,
                                            onClick = {
                                                stoppingPort = server.port
                                                scope.launch {
                                                    val stopped = withContext(Dispatchers.IO) {
                                                        LocalhostServerManager.stopServer(server)
                                                    }
                                                    stoppingPort = null
                                                    if (stopped) {
                                                        Toast.makeText(
                                                            context,
                                                            "Stopped :${server.port} (${server.processName})",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                        delay(180L)
                                                        reload()
                                                    } else {
                                                        Toast.makeText(
                                                            context,
                                                            "Could not find PID for :${server.port}",
                                                            Toast.LENGTH_SHORT,
                                                        ).show()
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(10.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = palette.error.copy(alpha = 0.22f),
                                                contentColor = palette.error,
                                            ),
                                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                                            modifier = Modifier.height(34.dp),
                                        ) {
                                            Icon(
                                                Icons.Filled.Stop,
                                                contentDescription = "Stop server",
                                                modifier = Modifier.size(14.dp),
                                            )
                                            Spacer(Modifier.width(4.dp))
                                            Text(if (isStopping) "Stopping" else "Stop", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (showStartDialog && onStartServer != null) {
                val portNum = startPort.toIntOrNull()
                val valid = startPath.isNotBlank() && portNum != null && portNum in 1024..65535
                AlertDialog(
                    onDismissRequest = { showStartDialog = false },
                    title = { Text("Start local server") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = startPath,
                                onValueChange = {
                                    startPath = it
                                    val remembered = BeTermuxSettings.getLocalhostPort(context, it.trim())
                                    if (remembered > 0) startPort = remembered.toString()
                                },
                                label = { Text("Project folder") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = startPort,
                                onValueChange = { startPort = it.filter(Char::isDigit) },
                                label = { Text("Port") },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                supportingText = { Text("1024–65535 · serves the folder via python http.server") },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            enabled = valid,
                            onClick = {
                                onStartServer(startPath.trim(), portNum!!)
                                showStartDialog = false
                            },
                        ) {
                            Text("Start")
                        }
                    },
                    dismissButton = {
                        FilledTonalButton(onClick = { showStartDialog = false }) {
                            Text("Cancel")
                        }
                    },
                )
            }
        }
}
