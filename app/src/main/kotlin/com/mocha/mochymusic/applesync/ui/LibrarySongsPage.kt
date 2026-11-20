// LibrarySongsPage.kt
//
// The All songs page is the fork's own: Apple's order, a sort control at the bar's end, and the
// Apple missing mark on a row. It used to sit inside upstream's LibrarySongsScreen.kt, where the
// seam budget counted every line of it; upstream then rewrote that page, so the page moved here
// and the upstream file calls it.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mocha.mochymusic.LocalPlayerConnection
import com.mocha.mochymusic.R
import com.mocha.mochymusic.extensions.toMediaItem
import com.mocha.mochymusic.playback.queues.ListQueue
import com.mocha.mochymusic.ui.component.LocalMenuState
import com.mocha.mochymusic.ui.component.SongSwipeBox
import com.mocha.mochymusic.ui.component.librarySwipeAction
import com.mocha.mochymusic.ui.component.queueSwipeAction
import com.mocha.mochymusic.ui.menu.SongMenu
import com.mocha.mochymusic.ui.screens.library.LibraryEntityListScreen
import com.mocha.mochymusic.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsPage(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val songsTitle = stringResource(R.string.songs)

    // order and sort control live in applesync/ui/SongsOrdering.kt
    val order = rememberSongsOrder(songs)
    val ordered = order.songs

    LibraryEntityListScreen(
        title = songsTitle,
        subtitle = stringResource(R.string.lib_songs_subtitle),
        searchPlaceholder = stringResource(R.string.lib_songs_search),
        items = ordered,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            // Queue the order the user is looking at, not the underlying create-date order
            playerConnection.playQueue(
                ListQueue(
                    title = songsTitle,
                    items = ordered.map { it.toMediaItem() },
                    startIndex = ordered.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        // the whole list plays or shuffles, with the sort control at the bar's end
        playBar = { order.playBar(playerConnection, songsTitle) },
        sectioned = order.byName,
        onLongClick = { song ->
            menuState.show {
                SongMenu(
                    originalSong = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
        // left queues the song, right takes it out of the library (it's already in it)
        rowWrapper = { song, row ->
            SongSwipeBox(
                slab = false,
                library = { librarySwipeAction(song.song) },
                queue = { queueSwipeAction { song.toMediaItem() } },
            ) {
                row()
            }
        },
    )
}
