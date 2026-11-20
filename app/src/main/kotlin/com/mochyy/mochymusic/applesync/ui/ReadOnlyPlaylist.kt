// ReadOnlyPlaylist.kt
//
// The playlists Apple will not let the account change: the ones it makes itself (a mix, a radio
// list) and the ones the person only follows. Apple answers 500 to a rewrite of any of them, and a
// local add or removal would be a change that exists here and nowhere else - so they are marked,
// and the edits are refused with the reason rather than silently kept.

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
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.component.detail.CircleAction
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

/**
 * Whether this playlist can be changed here, read once when its menu or screen opens.
 *
 * Unknown counts as editable: a playlist the import never wrote a flag for is one the guard has
 * never refused, and hiding its actions on a guess would be worse than letting Apple refuse them.
 */
@Composable
fun playlistIsEditable(playlistId: String?): Boolean {
    val database = LocalDatabase.current
    var editable by remember(playlistId) { mutableStateOf(true) }
    LaunchedEffect(playlistId) {
        if (playlistId == null) return@LaunchedEffect
        // the query is Room's plain kind: it refuses the main thread, and a refusal here would read
        // as "editable" and put the action back
        editable = withContext(Dispatchers.IO) {
            runCatching { database.playlistEntity(playlistId)?.isEditable }.getOrNull() ?: true
        }
    }
    return editable
}

/** The same playlists, without the ones nobody can add to. */
@Composable
fun editablePlaylists(playlists: State<List<Playlist>>): State<List<Playlist>> =
    remember(playlists) {
        derivedStateOf { playlists.value.filter { it.playlist.isEditable } }
    }
