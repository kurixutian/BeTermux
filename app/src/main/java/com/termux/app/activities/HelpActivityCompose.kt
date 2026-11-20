package com.termux.app.activities

import android.content.Context
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import com.betermux.compose.BeTermuxComposeTheme
import com.betermux.theme.LocalWarpPalette

/** Loading state updated by the activity's WebView callbacks. */
class HelpLoadingState {
    var loading by mutableStateOf(true)
        private set

    fun updateLoading(loading: Boolean) {
        this.loading = loading
    }
}

/** Compose shell around the existing WebView browser. */
object HelpActivityCompose {
    @JvmStatic
    fun createView(context: Context, webView: WebView, loadingState: HelpLoadingState): ComposeView =
        ComposeView(context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                BeTermuxComposeTheme(context) {
                    HelpActivityScreen(webView, loadingState)
                }
            }
        }
}

@Composable
internal fun HelpActivityScreen(webView: WebView, loadingState: HelpLoadingState) {
    val palette = LocalWarpPalette.current
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = palette.background,
    ) {
        Box(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
            AndroidView(
                factory = { webView },
                modifier = Modifier.fillMaxSize(),
            )
            if (loadingState.loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(palette.background)
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    awaitPointerEvent().changes.forEach { it.consume() }
                                }
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.semantics {
                            contentDescription = "Loading help"
                        },
                    )
                }
            }
        }
    }
}
