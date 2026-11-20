package com.termux.shared.activities

import android.annotation.SuppressLint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.termux.shared.R
import com.termux.shared.compose.SharedComposeTheme
import com.termux.shared.markdown.MarkdownUtils
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.SimpleEntry
import org.commonmark.node.FencedCodeBlock

/** Mutable Compose UI state observed by [ReportComposeInstaller] and updated by [ReportActivity]. */
class ReportComposeState {
    var title by mutableStateOf("")
        private set
    var markdownText by mutableStateOf("")
        private set
    var canSave by mutableStateOf(false)
        private set
    var reportId by mutableIntStateOf(0)
        private set

    fun update(title: String, markdownText: String, canSave: Boolean) {
        this.title = title
        this.markdownText = markdownText
        this.canSave = canSave
        this.reportId++
    }
}

/** Java bridge installing the Compose UI shell for [ReportActivity]. */
object ReportComposeInstaller {
    @SuppressLint("NotifyDataSetChanged")
    @JvmStatic
    fun setContent(
        activity: ComponentActivity,
        state: ReportComposeState,
        onShare: Runnable,
        onCopy: Runnable,
        onSave: Runnable,
    ) {
        activity.setContent {
            val context = LocalContext.current
            val markwon = remember(context) { MarkdownUtils.getRecyclerMarkwonBuilder(context) }
            val adapter = remember {
                MarkwonAdapter.builderTextViewIsRoot(R.layout.markdown_adapter_node_default)
                    .include(
                        FencedCodeBlock::class.java,
                        SimpleEntry.create(R.layout.markdown_adapter_node_code_block, R.id.code_text_view),
                    )
                    .build()
            }
            val lastRenderedReportId = remember { intArrayOf(-1) }

            SharedComposeTheme {
                ReportComposeScreen(
                    title = state.title,
                    canSave = state.canSave,
                    onShare = { onShare.run() },
                    onCopy = { onCopy.run() },
                    onSave = { onSave.run() },
                ) { modifier ->
                    AndroidView(
                        modifier = modifier.fillMaxSize(),
                        factory = { ctx ->
                            RecyclerView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                )
                                clipChildren = false
                                clipToPadding = false
                                overScrollMode = View.OVER_SCROLL_NEVER
                                val topPadding = ctx.resources.getDimensionPixelSize(R.dimen.content_padding)
                                val bottomPadding = TypedValue.applyDimension(
                                    TypedValue.COMPLEX_UNIT_DIP,
                                    36f,
                                    ctx.resources.displayMetrics,
                                ).toInt()
                                setPadding(0, topPadding, 0, bottomPadding)
                                layoutManager = LinearLayoutManager(ctx)
                                this.adapter = adapter
                            }
                        },
                        update = { recyclerView ->
                            val currentId = state.reportId
                            val markdown = state.markdownText
                            if (currentId > 0 && currentId != lastRenderedReportId[0]) {
                                lastRenderedReportId[0] = currentId
                                adapter.setMarkdown(markwon, markdown)
                                adapter.notifyDataSetChanged()
                                recyclerView.scrollToPosition(0)
                            }
                        },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReportComposeScreen(
    title: String,
    canSave: Boolean,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    contentView: @Composable (Modifier) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = stringResource(R.string.action_more_options),
                            )
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_share)) },
                                onClick = {
                                    menuExpanded = false
                                    onShare()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_copy)) },
                                onClick = {
                                    menuExpanded = false
                                    onCopy()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_save_to_file)) },
                                enabled = canSave,
                                onClick = {
                                    menuExpanded = false
                                    onSave()
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        contentView(Modifier.padding(innerPadding))
    }
}
