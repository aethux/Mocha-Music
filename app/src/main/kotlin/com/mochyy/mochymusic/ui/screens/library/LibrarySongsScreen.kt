// LibrarySongsScreen.kt

package com.mochyy.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.applesync.ui.playBar
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val songs by viewModel.songs.collectAsState()
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val songsTitle = stringResource(R.string.songs)

    // MOCHY-APPLE: order and sort control live in applesync/ui/SongsOrdering.kt
    val order = com.mochyy.mochymusic.applesync.ui.rememberSongsOrder(songs)
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
        // MOCHY-APPLE: the whole list plays or shuffles, with the sort control at the bar's end
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
