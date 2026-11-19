// AppleCollaboratorsScreen.kt
//
// The other people on a shared playlist, read from the web player's own page.
//
// Built the way the rest of this fork's page work is built (AppleSessionScreen): a real, attached
// WebView on music.apple.com with the app's session, the capture hook installed at document start,
// and `WebViewApplePage` answering through a JavascriptInterface. MusicKit has nothing to give here
// (the collaborators relationship 404s and `include=collaborators` adds nothing), and a signed-in
// page is client-rendered, so the page's own header is the source: it reads "Owner & N other" and
// the avatars beside it carry the names in their alt text.
//
// Opened for one playlist from the playlist page, or for every shared playlist at once - the same
// screen with a queue of one or of all, so the read costs one page load per playlist and nothing else.

package com.mochyy.mochymusic.ui.applesync

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.mochyy.mochymusic.applesync.PlaylistCollaborators
import com.mochyy.mochymusic.applesync.db.PlaylistCollaboratorEntity
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.applesync.page.ApplePageScripts
import com.mochyy.mochymusic.applesync.page.WebViewApplePage
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val AppleCollaboratorsRoute = "apple_collaborators/{playlistId}"

fun appleCollaboratorsRoute(playlistId: String) = "apple_collaborators/$playlistId"

/** Every shared playlist that has no names yet. */
const val AppleCollaboratorsAll = "all"

/** One playlist to read: its local id (for the sidecar) and its remote id (for the URL). */
private data class Target(val localId: String, val remoteId: String, val name: String)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppleCollaboratorsScreen(playlistId: String, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var forAll by remember { mutableStateOf(playlistId == AppleCollaboratorsAll) }
    var generation by remember { mutableStateOf(0) }

    var queue by remember { mutableStateOf<List<Target>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var sheetOpen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Opening the playlist…") }
    var current by remember { mutableStateOf<List<String>>(emptyList()) }
    var evidence by remember { mutableStateOf<List<String>>(emptyList()) }
    var answered by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(false) }
    val holder = remember { mutableStateOf<WebView?>(null) }

    // the queue: this one playlist, or every shared playlist still missing names
    LaunchedEffect(playlistId, generation) {
        done = false
        index = 0
        answered = 0
        queue = withContext(Dispatchers.IO) {
            runCatching {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao()
                val metas = dao.playlistMetas()
                val targets = if (forAll) {
                    // a row that is really this account is not a collaborator, so a playlist that
                    // only has those still counts as unread and is read again
                    val self = PlaylistCollaborators.selfName(metas)
                    val named = dao.allCollaborators()
                        .filterNot { it.name.equals(self, ignoreCase = true) }
                        .map { it.playlistId }
                        .toSet()
                    metas.filter { it.collaborative && it.playlistId !in named }
                } else {
                    metas.filter { it.playlistId == playlistId }
                }
                targets.map { meta ->
                    val remote = meta.playlistId.removePrefix("AM_PL_")
                    Target(meta.playlistId, remote, dao.link(meta.playlistId)?.remoteName.orEmpty())
                }
            }.getOrDefault(emptyList())
        }
        status = if (queue.isEmpty()) "Nothing to read" else "Reading 1 of ${queue.size}"
        if (queue.isEmpty()) {
            done = true
            sheetOpen = true
        }
    }

    // each target is one page load; the read happens when that load finishes
    LaunchedEffect(queue, index) {
        val target = queue.getOrNull(index) ?: return@LaunchedEffect
        status = "Reading ${index + 1} of ${queue.size}${if (target.name.isNotBlank()) " · ${target.name}" else ""}"
        holder.value?.loadUrl("https://music.apple.com/library/playlist/${target.remoteId}")
    }

    val readCurrent: () -> Unit = {
        scope.launch {
            val view = holder.value ?: return@launch
            val target = queue.getOrNull(index) ?: return@launch
            val page = WebViewApplePage(view)
            delay(4_500)
            val raw = runCatching { page.value(ApplePageScripts.readCollaborators()) }.getOrNull()
            val parsed = PlaylistCollaborators.parse(raw)
            evidence = parsed.evidence

            val known = withContext(Dispatchers.IO) {
                runCatching {
                    val dao = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ApplePlaylistEntryPoint::class.java,
                    ).dao()
                    Triple(
                        dao.playlistMeta(target.localId)?.ownerName,
                        dao.link(target.localId)?.remoteName,
                        PlaylistCollaborators.selfName(dao.playlistMetas()),
                    )
                }.getOrNull()
            }
            val owner = known?.first
            val playlistName = known?.second ?: target.name
            val self = known?.third
            // the playlist's own title carries an alt too, and neither the owner nor this account
            // belongs on a list of *other* people
            val people = parsed.names.filterNot {
                it.equals(owner, ignoreCase = true) ||
                    it.equals(playlistName, ignoreCase = true) ||
                    it.equals(self, ignoreCase = true)
            }
            current = people
            if (people.isNotEmpty()) answered++

            // A read that saw something is authoritative even when it found nobody: a playlist
            // whose only "collaborator" was this account must lose that row, not keep it forever.
            if (parsed.names.isNotEmpty() || parsed.evidence.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val dao = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            ApplePlaylistEntryPoint::class.java,
                        ).dao()
                        dao.clearCollaborators(target.localId)
                        if (people.isNotEmpty()) {
                            dao.saveCollaborators(
                                people.mapIndexed { at, name ->
                                    PlaylistCollaboratorEntity(playlistId = target.localId, name = name, position = at)
                                },
                            )
                        }
                    }
                }
            }

            if (index + 1 < queue.size) {
                index++
            } else {
                done = true
                status = if (forAll) "Read ${queue.size} playlists · $answered had names" else "Done"
                sheetOpen = true
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    // the phone page: the same string the session screen uses, since Apple's desktop
                    // client renders as a squeezed player here
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
                            readCurrent()
                        }
                    }
                    holder.value = this
                }
            },
        )

        Button(
            onClick = { sheetOpen = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Text(if (forAll && !done) "${(index + 1).coerceAtMost(queue.size)}/${queue.size}" else "Mochy")
        }

        TextButton(
            onClick = { navController.navigateUp() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(4.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp)),
        ) {
            Text("‹ Back", color = Color.White)
        }
    }

    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = sheetState) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Collaborators", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall)
                val target = queue.getOrNull(index)
                if (target != null && target.name.isNotBlank() && !done) {
                    Text(target.name, style = MaterialTheme.typography.bodyMedium)
                }
                current.forEach { name -> Text("· $name", style = MaterialTheme.typography.bodyMedium) }
                if (evidence.isNotEmpty()) {
                    Text(
                        "What the page said",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    evidence.take(6).forEach { line ->
                        Text(
                            line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (done) Button(onClick = { navController.navigateUp() }) { Text("Done") }
                    if (!done) TextButton(onClick = { sheetOpen = false }) { Text("Keep reading") }
                    if (!forAll) {
                        // one playlist led here; the rest of them are the same read
                        TextButton(onClick = {
                            forAll = true
                            generation++
                            sheetOpen = false
                        }) { Text("Read the other playlists") }
                    }
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose { holder.value?.destroy() }
    }
}
