// AppleSessionScreen.kt

package com.mocha.mochymusic.ui.applesync

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mocha.mochymusic.LocalHideAppChrome
import com.mocha.mochymusic.applesync.page.ApplePageScripts
import com.mocha.mochymusic.applesync.page.WebViewApplePage

const val AppleSessionRoute = "apple_session"

/**
 * A full-screen music.apple.com session, the way Mocha signs into a service for an import.
 *
 * Two things here are not obvious and were both learned the hard way:
 *
 *  - the WebView owns its own JavascriptInterface, attached before the first load. Added afterwards
 *    the document never sees it, and every answer is silently dropped.
 *  - the controls live in a bottom sheet behind one small button. Anything docked over the page eats
 *    the taps the page needs, and the page is the entire point of this screen.
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleSessionScreen(navController: NavController) {
    val viewModel: AppleSessionViewModel = hiltViewModel()
    val currentOnPage by rememberUpdatedState(viewModel::onPage)
    val holder = remember { mutableStateOf<WebView?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmWrites by remember { mutableStateOf(false) }
    var sheetOpen by remember { mutableStateOf(false) }

    // the page is the whole point of this screen, and the app's own bar and mini player were drawn on top of it
    val chrome = LocalHideAppChrome.current
    DisposableEffect(Unit) {
        chrome.value = true
        onDispose { chrome.value = false }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // the page used to run under the status bar, so Apple's own "Sign In" sat behind the clock;
        // the bar keeps both controls above the page instead of on top of its header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            TextButton(onClick = { navController.navigateUp() }) {
                Text("\u2039 Back", color = Color.White)
            }
            TextButton(onClick = { sheetOpen = true }) {
                Text("Mocha", color = Color.White)
            }
        }

        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { context ->
                WebView(context).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    // a bare WebView user-agent gets Apple's desktop client, which renders as a
                    // squeezed player; a plain Chrome mobile string gets what a phone browser sees
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    // the algorithmic dark mode turned Apple's own controls into white-on-white: the
                    // site brings its own theme, the same fix the import capture WebView carries
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) isForceDarkAllowed = false
                    // the recorder goes in before the page's own scripts: patch fetch afterwards and
                    // Apple's client never routes through it
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        runCatching {
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                ApplePageScripts.captureHook(),
                                setOf("https://music.apple.com"),
                            )
                        }
                    }
                    val page = WebViewApplePage(this)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            currentOnPage(page)
                        }
                    }
                    // no storefront of our own on a first run: the bare domain lets Apple follow the
                    // address it sees, instead of answering with the country picker
                    loadUrl(
                        if (viewModel.hasAccountStorefront) {
                            "https://music.apple.com/${viewModel.storefront}/library/songs"
                        } else {
                            "https://music.apple.com/"
                        },
                    )
                    holder.value = this
                }
            },
        )

    }

    if (sheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { sheetOpen = false },
            sheetState = sheetState,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "Apple Music",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(text = viewModel.status, style = MaterialTheme.typography.bodySmall)
                viewModel.probe?.let { answer ->
                    Text(
                        text = answer,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "Allow changes", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Only to playlists and songs Mocha created itself.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = viewModel.writesEnabled,
                        onCheckedChange = { on -> if (on) confirmWrites = true else viewModel.setWrites(false) },
                    )
                }

                if (viewModel.writesEnabled) {
                    Button(
                        onClick = {
                            if (viewModel.sandboxNext != null) viewModel.advanceSandbox() else viewModel.startSandbox()
                        },
                        enabled = !viewModel.sandboxRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                viewModel.sandboxRunning -> "Working…"
                                viewModel.sandboxNext != null -> "Next: ${viewModel.sandboxNext}"
                                else -> "Start sandbox"
                            },
                        )
                    }
                    for (line in viewModel.sandboxLines.takeLast(6)) {
                        Body(line, alpha = 0.8f)
                    }

                    Spacer(Modifier.height(4.dp))
                    Button(
                        onClick = {
                            if (viewModel.libraryNext != null) viewModel.advanceLibrary() else viewModel.startLibrary()
                        },
                        enabled = !viewModel.libraryRunning,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            when {
                                viewModel.libraryRunning -> "Working…"
                                viewModel.libraryNext != null -> "Library — next: ${viewModel.libraryNext}"
                                else -> "Start library test"
                            },
                        )
                    }
                    for (line in viewModel.libraryLines.takeLast(8)) {
                        Body(line, alpha = 0.8f)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = viewModel::startWatching) {
                            Text("Watch", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = viewModel::readCapture) {
                            Text("Read", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = viewModel::describePage) {
                            Text("Look", style = MaterialTheme.typography.labelMedium)
                        }
                        TextButton(onClick = viewModel::tapLike) {
                            Text("Tap heart", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text(
                        text = "Watch, tap the heart in the page, then read what it sent.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (confirmWrites) {
        AlertDialog(
            onDismissRequest = { confirmWrites = false },
            title = { Text("Allow Mocha to change your Apple Music?") },
            text = {
                Text(
                    "Mocha will only touch playlists and songs it created itself. Anything that " +
                        "was in your library before it ever wrote is refused by a check in the code, " +
                        "not by good intentions.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmWrites = false
                    viewModel.setWrites(true)
                }) { Text("Allow") }
            },
            dismissButton = {
                TextButton(onClick = { confirmWrites = false }) { Text("Not now") }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            currentOnPage(null)
            holder.value?.destroy()
        }
    }
}

/** One log line, small and quiet: these are for reading, not for decoration. */
@Composable
private fun Body(text: String, alpha: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
    )
}
