// ApplePlaylistGrid.kt
//
// Playlists as a grid, the way Albums already are: same screen, same tiles, same header. The list
// view was the odd one out in a library where every other collection is a grid.

package com.mochyy.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Playlist
import com.mochyy.mochymusic.ui.screens.library.LibraryGridEntry
import com.mochyy.mochymusic.ui.screens.library.LibraryGridScreen

/**
 * The playlists screen as a grid. The tile is the playlist's own artwork, the subtitle its length,
 * and the shared element key is the same one Home uses so opening one grows out of its cover.
 */
@Composable
fun PlaylistGridScreen(
    playlists: List<Playlist>,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    onOpen: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit = {},
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val resources = LocalContext.current.resources
    val entries = remember(playlists) {
        playlists.map { playlist ->
            LibraryGridEntry(
                id = playlist.id,
                title = playlist.playlist.name,
                subtitle = resources.getQuantityString(
                    R.plurals.lib_song_count,
                    playlist.songCount,
                    playlist.songCount,
                ),
                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                sharedElementKey = "playlist-${playlist.id}",
            )
        }
    }
    val byId = remember(playlists) { playlists.associateBy { it.id } }

    LibraryGridScreen(
        title = title,
        subtitle = subtitle,
        searchPlaceholder = searchPlaceholder,
        entries = entries,
        onOpen = { entry -> byId[entry.id]?.let(onOpen) },
        onLongClick = { entry -> byId[entry.id]?.let(onLongClick) },
        // Like Albums, both buttons open a playlist rather than build a queue across every one:
        // this screen loads playlist metadata only, and a real "play everything" would mean reading
        // every playlist's songs first just to make one button work.
        onPlay = { playlists.firstOrNull()?.let(onOpen) },
        onShuffle = { playlists.randomOrNull()?.let(onOpen) },
        trailing = trailing,
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
