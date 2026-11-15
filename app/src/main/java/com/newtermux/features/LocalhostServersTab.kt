package com.newtermux.features

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.newtermux.compose.NewTermuxComposeTheme
import com.newtermux.glass.GlassBackground
import com.newtermux.glass.GlassCard
import com.newtermux.glass.GlassChip
import com.newtermux.glass.GlassPageBackdropProvider
import com.newtermux.glass.GlassTokens
import com.newtermux.glass.GlassTopBar
import com.newtermux.glass.glassPageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.function.Consumer

object LocalhostServersBridge {
    @JvmStatic
    fun attachTo(
        composeView: ComposeView,
        context: Context,
        onOpenUrl: Consumer<String>,
        onServersChanged: Consumer<List<LocalhostServerManager.LocalhostServer>>? = null,
        onClose: Runnable? = null,
    ) {
        composeView.setContent {
            NewTermuxComposeTheme(context) {
                LocalhostServersTab(
                    onOpenUrl = { onOpenUrl.accept(it) },
                    onServersChanged = onServersChanged?.let { cb -> { list -> cb.accept(list) } },
                    onClose = onClose?.let { cb -> { cb.run() } },
                )
            }
        }
    }
}

/**
 * In-tab Localhost manager screen embedded inside TermuxActivity.
 * Shows live listening development ports with process/PID details,
 * one-tap browser launch, and server stop actions.
 */
@Composable
fun LocalhostServersTab(
    onOpenUrl: (String) -> Unit,
    onServersChanged: ((List<LocalhostServerManager.LocalhostServer>) -> Unit)? = null,
    onClose: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf<List<LocalhostServerManager.LocalhostServer>>(emptyList()) }
    var refreshing by remember { mutableStateOf(true) }
    var stoppingPort by remember { mutableStateOf<Int?>(null) }

    suspend fun reload() {
        refreshing = true
        val latest = withContext(Dispatchers.IO) { LocalhostServerManager.scanServers() }
        servers = latest
        refreshing = false
        onServersChanged?.invoke(latest)
    }

    LaunchedEffect(Unit) {
        while (true) {
            reload()
            delay(2500L)
        }
    }

    GlassPageBackdropProvider {
        GlassBackground(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                GlassTopBar(
                    title = "Localhost",
                    subtitle = if (servers.isEmpty()) "No active servers" else "${servers.size} live",
                    actions = {
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
                        .fillMaxWidth()
                        .glassPageSource(),
                ) {
                    if (servers.isEmpty() && !refreshing) {
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
                                "Start a server in any terminal tab (e.g. npm run dev, vite, python -m http.server) to open or stop it here.",
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
                                GlassCard(modifier = Modifier.fillMaxWidth()) {
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
                                                .background(Color(0xFF3FB950)),
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
                                                containerColor = Color(0xFFF85149).copy(alpha = 0.22f),
                                                contentColor = Color(0xFFFF7B72),
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
        }
    }
}
