// AppleCollaboratorsScreen.kt
//
// The other people on a shared playlist, read from the web player's own page.
//
// MusicKit has nothing to give here (the collaborators relationship 404s and `include=collaborators`
// adds nothing), and a signed-in page is client-rendered, so the page's own header is the source: it
// reads "Owner & N other" and the avatars beside it carry the names in their alt text.
//
// The page still has to be a real, attached WebView (a headless one never reaches onPageFinished and
// gets an anonymous page), so it sits 1 dp tall behind the screen and the screen itself is built like
// the rest of Mocha's settings: the same scaffold, the same grouped rows, an import card while it
// works. Nothing of the page is shown - the read is the only thing this screen is for.

package com.mocha.mochymusic.ui.applesync

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
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.PlaylistCollaborators
import com.mocha.mochymusic.applesync.db.PlaylistCollaboratorEntity
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.applesync.page.ApplePageScripts
import com.mocha.mochymusic.applesync.page.WebViewApplePage
import com.mocha.mochymusic.ui.component.SettingsGroup
import com.mocha.mochymusic.ui.component.SettingsGroupStyle
import com.mocha.mochymusic.ui.component.SettingsItem
import com.mocha.mochymusic.ui.component.SubSettingsScaffold
import com.mocha.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val AppleCollaboratorsRoute = "apple_collaborators/{playlistId}"

fun appleCollaboratorsRoute(playlistId: String) = "apple_collaborators/$playlistId"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AppleCollaboratorsScreen(playlistId: String, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var playlistName by remember { mutableStateOf("") }
    var names by remember { mutableStateOf<List<String>>(emptyList()) }
    var reading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    val holder = remember { mutableStateOf<WebView?>(null) }

    // the remote id is the local one without the prefix, and the name is the one the import stored
    val remoteId = playlistId.removePrefix("AM_PL_")

    LaunchedEffect(playlistId) {
        withContext(Dispatchers.IO) {
            runCatching {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao()
                Triple(
                    dao.link(playlistId)?.remoteName.orEmpty(),
                    dao.collaborators(playlistId).map { it.name },
                    dao.playlistMeta(playlistId)?.ownerName,
                )
            }.onSuccess { (name, stored, _) ->
                playlistName = name
                names = stored
            }
        }
        holder.value?.loadUrl("https://music.apple.com/library/playlist/$remoteId")
    }

    val read: () -> Unit = {
        scope.launch {
            val view = holder.value ?: return@launch
            // the page renders client-side; the credit line arrives after the first paint
            delay(4_500)
            val raw = runCatching { WebViewApplePage(view).value(ApplePageScripts.readCollaborators()) }.getOrNull()
            val parsed = PlaylistCollaborators.parse(raw)

            val known = withContext(Dispatchers.IO) {
                runCatching {
                    val dao = EntryPointAccessors.fromApplication(
                        context.applicationContext,
                        ApplePlaylistEntryPoint::class.java,
                    ).dao()
                    Triple(
                        dao.playlistMeta(playlistId)?.ownerName,
                        dao.link(playlistId)?.remoteName,
                        PlaylistCollaborators.selfName(dao.playlistMetas()),
                    )
                }.getOrNull()
            }
            val owner = known?.first
            val name = known?.second ?: playlistName
            val self = known?.third
            // the playlist's own title carries an alt too, and neither the owner nor this account
            // belongs on a list of *other* people
            val people = parsed.names.filterNot {
                it.equals(owner, ignoreCase = true) ||
                    it.equals(name, ignoreCase = true) ||
                    it.equals(self, ignoreCase = true)
            }

            // A read that saw the credit line is authoritative even when it found nobody: a playlist
            // whose only "collaborator" was this account must lose that row, not keep it forever.
            if (parsed.names.isNotEmpty() || parsed.evidence.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        val dao = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            ApplePlaylistEntryPoint::class.java,
                        ).dao()
                        dao.clearCollaborators(playlistId)
                        if (people.isNotEmpty()) {
                            dao.saveCollaborators(
                                people.mapIndexed { at, person ->
                                    PlaylistCollaboratorEntity(playlistId = playlistId, name = person, position = at)
                                },
                            )
                        }
                    }
                }
                names = people
            } else if (parsed.names.isEmpty() && names.isEmpty()) {
                failed = true
            }
            reading = false
        }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.apple_collaborators_title),
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
                    text = stringResource(R.string.apple_collaborators_reading),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        SettingsGroup(
            title = stringResource(R.string.apple_collaborators_people),
            style = SettingsGroupStyle.Grouped,
            items = when {
                names.isNotEmpty() -> names.map { person ->
                    SettingsItem(
                        title = { Text(person) },
                        icon = painterResource(R.drawable.group_filled),
                        iconShape = CircleShape,
                    )
                }

                failed -> listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.apple_collaborators_none)) },
                        descriptionText = stringResource(R.string.apple_collaborators_none_desc),
                        icon = painterResource(R.drawable.group_outlined),
                        iconShape = CircleShape,
                        onClick = {
                            failed = false
                            reading = true
                            holder.value?.reload()
                        },
                    ),
                )

                reading -> emptyList()

                else -> listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.apple_collaborators_none)) },
                        descriptionText = stringResource(R.string.apple_collaborators_none_desc),
                        icon = painterResource(R.drawable.group_outlined),
                        iconShape = CircleShape,
                    ),
                )
            },
        )

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.apple_collaborators_source),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(120.dp))

        // 1 dp, attached, never seen: the page has to lay out for its scripts to answer, and a
        // detached WebView never reaches onPageFinished.
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
                            read()
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
