// ApplePlaylistPageScreen.kt
//
// What the web player's own page lists for one playlist, next to what the REST copy answers.
//
// A library playlist's REST tracks relationship answers with the tracks that are also in the
// library, so a playlist can look one song long through the API and thirty songs long in the player.
// This screen reads the player's page - the same attached-WebView pattern the collaborators reader
// uses, because a headless WebView never reaches onPageFinished and gets an anonymous page - and
// says, in numbers, which side is short.

package com.mochyy.mochymusic.ui.applesync

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.applesync.page.ApplePageScripts
import com.mochyy.mochymusic.applesync.page.WebViewApplePage
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

const val ApplePlaylistPageRoute = "apple_playlist_page/{playlistId}"

fun applePlaylistPageRoute(playlistId: String) = "apple_playlist_page/$playlistId"

private data class PageRead(
    val header: Int,
    val links: Int,
    val sample: List<String>,
    val error: String?,
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun ApplePlaylistPageScreen(playlistId: String, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playlistName by remember { mutableStateOf("") }
    var reading by remember { mutableStateOf(true) }
    var read by remember { mutableStateOf<PageRead?>(null) }
    val holder = remember { mutableStateOf<WebView?>(null) }

    val remoteId = playlistId.removePrefix("AM_PL_")

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao()
                dao.link(playlistId)?.remoteName.orEmpty()
            }.onSuccess { playlistName = it }
        }
        holder.value?.loadUrl("https://music.apple.com/library/playlist/$remoteId")
    }

    val readPage: () -> Unit = {
        scope.launch {
            val view = holder.value ?: return@launch
            // the page paints client-side and the list is drawn as it scrolls, so the page is
            // walked to the end before anything is counted
            delay(4_000)
            repeat(14) {
                WebViewApplePage(view).value("window.scrollBy(0, window.innerHeight)")
                delay(350)
            }
            val raw = runCatching { WebViewApplePage(view).value(ApplePageScripts.readPlaylistTracks()) }
                .getOrNull()
                .orEmpty()
            WebViewApplePage(view).value("window.scrollTo(0, 0)")

            val parsed = runCatching {
                // evaluateJavascript hands back the script's own return value JSON-encoded, so a
                // string comes back quoted and escaped: unwrap it before parsing the page's answer
                val text = raw.trim().let { if (it.startsWith("\"")) JSONObject("{\"v\":$it}").optString("v") else it }
                val json = JSONObject(text)
                PageRead(
                    header = json.optInt("header", 0),
                    links = json.optInt("links", 0),
                    sample = runCatching {
                        val names = json.optJSONArray("names")
                        (0 until minOf(names?.length() ?: 0, 8)).mapNotNull { names?.optString(it) }
                    }.getOrDefault(emptyList()),
                    error = json.optString("error").takeIf { it.isNotBlank() && it != "null" },
                )
            }.getOrNull()

            withContext(Dispatchers.IO) {
                runCatching {
                    val dao = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ApplePlaylistEntryPoint::class.java,
                    ).dao()
                    dao.audit(
                        AppleAuditEntity(
                            at = System.currentTimeMillis(),
                            op = "PLAYLIST_PAGE",
                            target = remoteId,
                            decision = if (parsed == null) "ERROR" else "READ",
                            reason = playlistName + ": page header " + (parsed?.header ?: 0) +
                                " · song links " + (parsed?.links ?: 0) +
                                (parsed?.error?.let { " · " + it } ?: ""),
                        ),
                    )
                }
            }
            read = parsed
            reading = false
        }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.apple_page_title),
        onBack = { navController.navigateUp() },
        backLabel = null,
    ) {
        if (playlistName.isNotBlank()) {
            Text(
                text = playlistName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (reading) {
            ImportInfoCard(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.size(22.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.apple_page_reading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            title = stringResource(R.string.apple_page_found),
            style = SettingsGroupStyle.Grouped,
            items = buildList {
                val page = read
                if (page != null) {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.apple_page_header, page.header)) },
                            icon = painterResource(R.drawable.list),
                            iconShape = CircleShape,
                        ),
                    )
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.apple_page_links, page.links)) },
                            icon = painterResource(R.drawable.list),
                            iconShape = CircleShape,
                        ),
                    )
                    page.sample.forEach { name ->
                        add(
                            SettingsItem(
                                title = { Text(name) },
                                icon = painterResource(R.drawable.queue_music),
                                iconShape = CircleShape,
                            ),
                        )
                    }
                    if (page.error != null) {
                        add(SettingsItem(title = { Text(page.error) }))
                    }
                } else if (!reading) {
                    add(
                        SettingsItem(
                            title = { Text(stringResource(R.string.apple_page_none)) },
                            icon = painterResource(R.drawable.sync),
                            iconShape = CircleShape,
                            onClick = {
                                reading = true
                                read = null
                                holder.value?.reload()
                            },
                        ),
                    )
                }
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.apple_page_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(120.dp))

        // 1 dp, attached, never seen: the page has to lay out for its scripts to answer.
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(1.dp),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.userAgentString =
                        "Mozilla/5.0 (Linux; Android 15; Pixel 7) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/139.0.0.0 Mobile Safari/537.36"
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                        runCatching {
                            WebViewCompat.addDocumentStartJavaScript(
                                this,
                                ApplePageScripts.captureHook(),
                                setOf("https://music.apple.com"),
                            )
                        }
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            readPage()
                        }
                    }
                    holder.value = this
                }
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { holder.value?.destroy() }
    }
}
