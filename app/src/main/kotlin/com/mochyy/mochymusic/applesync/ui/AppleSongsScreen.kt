// AppleSongsScreen.kt
//
// "1,407 on Apple Music" and "6 on YouTube" in the library footer are counts of the same list, split
// by where each song plays from. Tapping one opens that half of the library, drawn with the same
// row, the same sort control and the same play bar as the full Songs list - it is a filter, not a
// second design.

package com.mochyy.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.LocalMenuState
import com.mochyy.mochymusic.ui.component.SongSwipeBox
import com.mochyy.mochymusic.ui.component.librarySwipeAction
import com.mochyy.mochymusic.ui.component.queueSwipeAction
import com.mochyy.mochymusic.ui.menu.SongMenu
import com.mochyy.mochymusic.ui.screens.library.LibraryEntityListScreen
import com.mochyy.mochymusic.viewmodels.LibraryHomeViewModel

/** Apple's rows are `AM_`; everything without that prefix plays from YouTube. */
const val APPLE_SONGS_ROUTE = "apple/songs/{source}"

fun appleSongsRoute(source: String) = "apple/songs/$source"

@Composable
fun AppleSongsScreen(
    source: String,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val all by viewModel.songs.collectAsState()
    // the footer counts a song matched by hand as Apple's, so this list must too, or the number
    // and the list it opens disagree (one on YouTube, four rows)
    val dao = remember {
        dagger.hilt.android.EntryPointAccessors.fromApplication(
            context.applicationContext,
            com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint::class.java,
        ).dao()
    }
    val pinned by remember(dao) {
        dao.rematchSongIdsFlow(com.mochyy.mochymusic.applesync.db.AppleRematchEntity.SOURCE_APPLE)
    }.collectAsState(initial = emptyList())
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current

    val onApple = source != "youtube"
    val title = if (onApple) "On Apple Music" else "On YouTube"
    val songs = remember(all, onApple, pinned) {
        val apple = pinned.toSet()
        all.filter {
            val isApple = it.id.startsWith("AM_") || it.id in apple
            isApple == onApple
        }
    }
    val order = rememberSongsOrder(songs)

    LibraryEntityListScreen(
        title = title,
        subtitle = if (onApple) "Everything in your library that plays from Apple" else "The songs Apple does not have",
        searchPlaceholder = "Search these songs",
        items = order.songs,
        idOf = { it.id },
        nameOf = { it.song.title },
        subtitleOf = { it.artists.joinToString { a -> a.name }.ifBlank { null } },
        thumbnailOf = { it.song.thumbnailUrl },
        playingIdOf = { it.id },
        onClick = { song ->
            playerConnection.playQueue(
                ListQueue(
                    title = title,
                    items = order.songs.map { it.toMediaItem() },
                    startIndex = order.songs.indexOf(song).coerceAtLeast(0),
                ),
            )
        },
        playBar = { order.playBar(playerConnection, title) },
        sectioned = order.byName,
        onLongClick = { song ->
            menuState.show {
                SongMenu(originalSong = song, navController = navController, onDismiss = menuState::dismiss)
            }
        },
        modifier = modifier,
        pureBlack = pureBlack,
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
