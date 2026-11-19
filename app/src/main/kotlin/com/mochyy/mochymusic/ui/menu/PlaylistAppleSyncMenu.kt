// PlaylistAppleSyncMenu.kt
//
// "Sync with Apple Music" in a playlist's menus, shaped like the Re-sync row next to it: one entry,
// a picker when the playlist has not been linked yet, and a count once it has.

package com.mochyy.mochymusic.ui.menu

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.ApplePlaylistLinker
import com.mochyy.mochymusic.applesync.ApplePlaylistWrites
import com.mochyy.mochymusic.applesync.CoverOverrides
import com.mochyy.mochymusic.applesync.RemotePlaylist
import com.mochyy.mochymusic.applesync.db.AppleLinkEntity
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.ui.component.DefaultDialog
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.PopupSheetState
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * One playlist's Apple Music connection, for its menu.
 *
 * Nothing here writes to Apple. Linking records which remote playlist this one is, and the counts
 * come from reading both sides, so the menu can say how much of a sync there is to do before any
 * sync exists.
 */
class PlaylistAppleSyncState internal constructor(
    private val playlistId: String,
    private val scope: CoroutineScope,
    private val context: Context,
    private val linker: ApplePlaylistLinker,
    private val onDismiss: () -> Unit,
) {
    var linked by mutableStateOf<AppleLinkEntity?>(null)
        private set

    var candidates by mutableStateOf<List<RemotePlaylist>>(emptyList())
        private set

    var pickerOpen by mutableStateOf(false)
        private set

    var busy by mutableStateOf(false)
        private set

    var message by mutableStateOf<String?>(null)
        private set

    /** Reads the link without opening anything, so the row can say what tapping it does. */
    fun prefetch() {
        if (linked != null) return
        scope.launch {
            linked = runCatching { linker.linked(playlistId) }.getOrNull()
        }
    }

    fun open() {
        scope.launch {
            busy = true
            linked = runCatching { linker.linked(playlistId) }.getOrNull()
            if (linked == null) {
                candidates = runCatching { linker.candidates() }.getOrElse { error ->
                    message = error.message ?: "Apple didn't answer."
                    emptyList()
                }
                pickerOpen = candidates.isNotEmpty()
            } else {
                // linked already: the row's job is to send this playlist to Apple now, and to say
                // what came of it - a playlist Apple won't take says so instead of doing nothing
                message = ApplePlaylistWrites.push(context, playlistId, quiet = false).refusal
            }
            busy = false
        }
    }

    fun choose(remote: RemotePlaylist) {
        scope.launch {
            busy = true
            runCatching { linker.link(playlistId, remote) }
                .onSuccess {
                    linked = linker.linked(playlistId)
                    pickerOpen = false
                    message = context.getString(R.string.menu_apple_linked, remote.name)
                }
                .onFailure { message = it.message }
            busy = false
        }
    }

    fun unlink() {
        scope.launch {
            linker.unlink(playlistId)
            linked = null
            message = null
        }
    }

    fun dismissPicker() {
        pickerOpen = false
    }

    fun menuItem(): Material3MenuItemData? = Material3MenuItemData(
        // linked, the row is the way to push by hand; unlinked, it is the way to choose the playlist
        title = {
            Text(
                stringResource(
                    if (linked != null) R.string.menu_apple_push_title else R.string.menu_apple_sync_title,
                ),
            )
        },
        description = {
            Text(
                stringResource(
                    if (linked != null) R.string.menu_apple_push_desc else R.string.menu_apple_sync_desc,
                ),
            )
        },
        icon = { Icon(painter = painterResource(R.drawable.sync), contentDescription = null) },
        onClick = ::open,
    )
}

/**
 * A playlist's cover, as a row in its own menu: hand it a link, a picture from the phone, or hand
 * it back to Apple.
 *
 * Apple has no call that sets playlist artwork, so a chosen cover is always local - see
 * [CoverOverrides]. The row itself is written at once, so the change shows without a sync.
 */
@Composable
fun playlistCoverMenuItem(
    playlist: Playlist,
    database: MusicDatabase,
    menuState: PopupSheetState,
): Material3MenuItemData = coverRow(
    kind = CoverOverrides.Kind.PLAYLIST,
    id = playlist.id,
    menuState = menuState,
    onApplied = coverApplier(CoverOverrides.Kind.PLAYLIST, playlist.id, playlist.playlist.thumbnailUrl) { chosen ->
        database.query { update(playlist.playlist.copy(thumbnailUrl = chosen)) }
    },
)

@Composable
fun rememberPlaylistAppleSync(playlistId: String, onDismiss: () -> Unit): PlaylistAppleSyncState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val linker = remember {
        val entry = EntryPointAccessors.fromApplication(context.applicationContext, ApplePlaylistEntryPoint::class.java)
        ApplePlaylistLinker(entry.dao(), entry.tokens())
    }
    val state = remember(playlistId) { PlaylistAppleSyncState(playlistId, scope, context, linker, onDismiss) }

    // the row's words depend on whether this playlist is linked, so the link is read as the menu opens
    LaunchedEffect(playlistId) { state.prefetch() }

    if (state.pickerOpen) {
        DefaultDialog(
            onDismiss = state::dismissPicker,
            title = { Text(stringResource(R.string.menu_apple_pick_title)) },
            buttons = {
                TextButton(onClick = state::dismissPicker) {
                    Text(stringResource(R.string.yt_sync_cancel))
                }
            },
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // 26 playlists past the first screenful must still be reachable
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = stringResource(R.string.menu_apple_pick_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                for (playlist in state.candidates) {
                    Text(
                        text = playlist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { state.choose(playlist) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }

    return state
}
