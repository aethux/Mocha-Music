// AddToPlaylistOpener.kt
//
// "Add to playlist" asked for by a menu, answered by the app instead of by the menu.
//
// A menu is a popup sheet's content, and a sheet draws its last pushed page instead of that content.
// The picker pushes itself as a page, which unmounts the menu that hosts it - and the picker's own
// `onDispose` then dismissed the frame it had just opened, so the sheet closed and no picker was
// ever seen. The ask is kept here, and the picker is drawn at the root, where a tap inside a sheet
// cannot unmount it.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.music.innertube.YouTube
import com.mocha.mochymusic.db.entities.Song
import com.mocha.mochymusic.ui.menu.AddToPlaylistDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** The song a menu asked to put in a playlist, until it is answered or the picker is dismissed. */
object AddToPlaylistAsk {

    var song by mutableStateOf<Song?>(null)
        private set

    fun ask(song: Song) {
        this.song = song
    }

    fun clear() {
        if (song != null) song = null
    }
}

/**
 * The picker for [AddToPlaylistAsk], composed once at the app's root beside the other openers.
 *
 * Own sheet, not a page over the menu that asked: the menu is closing by the time this opens, so
 * there is nothing behind it to return to - which is also why Back and the backdrop are enough.
 */
@Composable
fun AddToPlaylistOpener() {
    val song = AddToPlaylistAsk.song ?: return
    val scope = rememberCoroutineScope()

    AddToPlaylistDialog(
        isVisible = true,
        onGetSong = { playlist ->
            // a playlist that mirrors YouTube keeps its copy there too; an Apple row has no video,
            // and YouTube's own answer to that is what it always was - nothing
            playlist.playlist.browseId?.let { browseId ->
                scope.launch(Dispatchers.IO) { YouTube.addToPlaylist(browseId, song.id) }
            }
            listOf(song.id)
        },
        onDismiss = { AddToPlaylistAsk.clear() },
        topSongId = song.id,
        topSongArtworkUrl = song.song.thumbnailUrl,
    )
}
