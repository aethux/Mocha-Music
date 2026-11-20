// ReadOnlyPlaylist.kt
//
// The playlists Apple keeps to itself: the mixes it builds from a rule (Favourite Songs, a radio
// list) and the editorial ones the account only follows. Apple answers 500 to a rewrite of any of
// them, and a local add would be a change that exists here and nowhere else - so they are marked,
// and the edits are refused with the reason rather than silently kept.
//
// A *collaborative* playlist is a different thing carrying the same `canEdit=false` in its library
// resource: it has people on it, and this account made it - so it is listed and editable here. The
// two are told apart by `hasCollaboration`, which the import already records on `playlist_meta`.

package com.mochyy.mochymusic.applesync.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.component.detail.CircleAction
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The lock beside shuffle, for a playlist Apple keeps for itself.
 *
 * Tapping it says why: the mark alone explains that something is different, not what to do about
 * it, and "the songs I add here never show up on Apple" is the question it has to answer.
 */
@Composable
fun ReadOnlyPlaylistMark(visible: Boolean) {
    if (!visible) return
    val context = androidx.compose.ui.platform.LocalContext.current
    Spacer(androidx.compose.ui.Modifier.width(4.dp))
    CircleAction(
        icon = R.drawable.lock,
        contentDescription = stringResource(R.string.apple_playlist_read_only_mark),
        onClick = {
            TopToaster.show(
                text = context.getString(R.string.apple_playlist_read_only),
                iconRes = R.drawable.lock,
            )
        },
    )
}

/** Which of the account's playlists have other people on them; read once, off the main thread. */
@Composable
fun collaborativePlaylists(): Set<String> {
    val context = androidx.compose.ui.platform.LocalContext.current
    var ids by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(Unit) {
        ids = withContext(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao().playlistMetas().filter { it.collaborative }.map { it.playlistId }.toSet()
            }.getOrDefault(emptySet())
        }
    }
    return ids
}

/**
 * Whether this playlist can be changed here, read once when its menu or screen opens.
 *
 * A shared playlist reports `canEdit=false` in Apple's library resource even though this account
 * made it, so `hasCollaboration` is what keeps it editable. Unknown counts as editable: a playlist
 * the import never wrote a flag for is one the guard has never refused, and hiding its actions on a
 * guess would be worse than letting Apple refuse them.
 */
@Composable
fun playlistIsEditable(playlistId: String?): Boolean {
    if (playlistId == null) return true
    val database = LocalDatabase.current
    val shared = collaborativePlaylists()
    var editable by remember(playlistId) { mutableStateOf(true) }
    LaunchedEffect(playlistId, shared) {
        // the query is Room's plain kind: it refuses the main thread, and a refusal here would read
        // as "editable" and put the action back
        editable = withContext(Dispatchers.IO) {
            runCatching {
                val mine = database.playlistEntity(playlistId)?.isEditable ?: true
                mine || playlistId in shared
            }.getOrDefault(true)
        }
    }
    return editable
}

/** The same playlists, without the ones Apple keeps for itself. Shared ones stay. */
@Composable
fun editablePlaylists(playlists: State<List<Playlist>>): State<List<Playlist>> {
    val shared = collaborativePlaylists()
    return remember(playlists, shared) {
        derivedStateOf { playlists.value.filter { it.playlist.isEditable || it.id in shared } }
    }
}
