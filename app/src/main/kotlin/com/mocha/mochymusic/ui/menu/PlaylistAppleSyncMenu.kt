// PlaylistAppleSyncMenu.kt
//
// "Sync with Apple Music" in a playlist's menus, shaped like the Re-sync row next to it: one entry,
// a picker when the playlist has not been linked yet, and a count once it has.

package com.mocha.mochymusic.ui.menu

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
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.ApplePlaylistCreate
import com.mocha.mochymusic.applesync.ApplePlaylistDelete
import com.mocha.mochymusic.applesync.ApplePlaylistLinker
import com.mocha.mochymusic.applesync.ApplePlaylistWrites
import com.mocha.mochymusic.applesync.CoverOverrides
import com.mocha.mochymusic.applesync.RemotePlaylist
import com.mocha.mochymusic.applesync.db.AppleLinkEntity
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.db.entities.Playlist
import com.mocha.mochymusic.ui.component.DefaultDialog
import com.mocha.mochymusic.ui.component.Material3MenuItemData
import com.mocha.mochymusic.ui.component.TopToaster
import com.mocha.mochymusic.ui.component.PopupSheetState
import com.mocha.mochymusic.ui.player.menu.MenuRow
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
                    message = error.message ?: context.getString(R.string.apple_sync_no_answer)
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
            message = context.getString(R.string.menu_apple_unlinked)
            TopToaster.show(
                text = context.getString(R.string.menu_apple_unlinked),
                iconRes = R.drawable.sync,
            )
        }
    }

    /**
     * A playlist with no Apple counterpart: make one there and take these songs with it.
     *
     * The picker only ever offered Apple's own playlists, and linking to one means every push
     * replaces *that* list — so a playlist made here had nowhere to go. This is that place.
     */
    fun createOnApple() {
        scope.launch {
            busy = true
            message = null
            val result = runCatching { ApplePlaylistCreate.create(context, playlistId, quiet = true) }
            linked = runCatching { linker.linked(playlistId) }.getOrNull()
            pickerOpen = false
            message = result.exceptionOrNull()?.message
                ?: result.getOrNull()?.refusal
                ?: context.getString(R.string.apple_playlist_created, linked?.remoteName.orEmpty())
            busy = false
        }
    }

    fun dismissPicker() {
        pickerOpen = false
    }

    var askDelete by mutableStateOf(false)
        private set

    /** A delete is destructive and irreversible on Apple's side, so it is asked, never assumed. */
    fun askToDelete() {
        askDelete = true
    }

    fun dismissDelete() {
        askDelete = false
    }

    fun deleteOnApple() {
        askDelete = false
        scope.launch {
            busy = true
            message = null
            val result = runCatching { ApplePlaylistDelete.delete(context, playlistId, quiet = true) }
            linked = runCatching { linker.linked(playlistId) }.getOrNull()
            message = result.exceptionOrNull()?.message
                ?: result.getOrNull()?.refusal
                ?: result.getOrNull()?.name?.let { context.getString(R.string.apple_playlist_deleted, it) }
            busy = false
        }
    }

    /**
     * The row is always there: the menu it lives in is a snapshot of the state at the moment it
     * opened, so a row that only exists once the link has been read never appears at all. The tap
     * reads the link instead, and says so when there is none.
     */
    fun deleteMenuItem(): Material3MenuItemData = Material3MenuItemData(
        title = { Text(stringResource(R.string.menu_apple_delete_title)) },
        description = { Text(stringResource(R.string.menu_apple_delete_desc)) },
        icon = { Icon(painter = painterResource(R.drawable.delete), contentDescription = null) },
        onClick = {
            val link = linked
            if (link == null) {
                message = context.getString(R.string.apple_playlist_unlinked)
            } else {
                askDelete = true
            }
        },
    )

    /**
     * The one read that has to be certain.
     *
     * The menu it describes is a snapshot taken when the sheet opened, and the async prefetch can
     * still be in flight at that moment - which is how a linked playlist came to be labelled "not
     * synced". A single row lookup is cheap enough to take synchronously, once, and then cache.
     */
    private fun linkNow(): AppleLinkEntity? {
        linked?.let { return it }
        if (readOnce) return null
        readOnce = true
        val read = runCatching {
            kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { linker.linked(playlistId) }
        }.getOrNull()
        if (read != null) linked = read
        return read
    }

    private var readOnce = false

    fun menuItem(): Material3MenuItemData? = Material3MenuItemData(
        // linked, the row says so and sends by hand; unlinked, it is the way to choose a playlist
        title = {
            Text(
                stringResource(
                    if (linkNow() != null) R.string.menu_apple_connected else R.string.menu_apple_not_connected,
                ),
            )
        },
        description = {
            val link = linkNow()
            Text(
                if (link != null && link.lastSyncAt != null) {
                    stringResource(R.string.menu_apple_connected_desc, ago(link.lastSyncAt))
                } else {
                    stringResource(R.string.menu_apple_not_connected_desc)
                },
            )
        },
        icon = { Icon(painter = painterResource(R.drawable.sync), contentDescription = null) },
        onClick = ::open,
    )

    /** "just now", "4 minutes ago", "2 days ago": the cue that a send landed and when. */
    private fun ago(at: Long): String {
        val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0)
        return when {
            minutes < 1 -> context.getString(R.string.menu_apple_ago_now)
            minutes < 60 -> context.getString(R.string.menu_apple_ago_minutes, minutes)
            minutes < 60 * 24 -> context.getString(R.string.menu_apple_ago_hours, minutes / 60)
            else -> context.getString(R.string.menu_apple_ago_days, minutes / (60 * 24))
        }
    }
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
fun playlistPageReadItem(
    playlist: Playlist,
    navController: NavController?,
    onDismiss: () -> Unit,
): Material3MenuItemData = Material3MenuItemData(
    title = { Text(stringResource(R.string.apple_page_menu)) },
    description = { Text(stringResource(R.string.apple_page_menu_desc)) },
    icon = { Icon(painterResource(R.drawable.sync), contentDescription = null) },
    onClick = {
        onDismiss()
        navController?.navigate(com.mocha.mochymusic.ui.applesync.applePlaylistPageRoute(playlist.id))
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
                // first, because a playlist made here usually has nowhere else to go
                MenuRow(
                    icon = R.drawable.playlist_add,
                    title = stringResource(R.string.menu_apple_create_title),
                    enabled = !state.busy,
                    onClick = { state.createOnApple() },
                )
                Text(
                    text = stringResource(R.string.menu_apple_create_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                )
                for (playlist in state.candidates) {
                    // the same row shape as every other picker in the app
                    MenuRow(
                        icon = R.drawable.playlist_add,
                        title = playlist.name,
                        enabled = !state.busy,
                        onClick = { state.choose(playlist) },
                    )
                }
            }
        }
    }

    if (state.askDelete) {
        DefaultDialog(
            onDismiss = state::dismissDelete,
            title = { Text(stringResource(R.string.menu_apple_delete_title)) },
            buttons = {
                TextButton(onClick = state::dismissDelete) {
                    Text(stringResource(R.string.yt_sync_cancel))
                }
                TextButton(onClick = state::deleteOnApple) {
                    Text(stringResource(R.string.menu_apple_delete_title))
                }
            },
        ) {
            Text(
                text = stringResource(
                    R.string.apple_playlist_delete_confirm,
                    state.linked?.remoteName.orEmpty(),
                ),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    return state
}
