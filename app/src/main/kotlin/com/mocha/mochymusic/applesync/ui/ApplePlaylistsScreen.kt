// ApplePlaylistsScreen.kt
//
// Playlists, with the presentation the person last chose: the list they had before, or the grid that
// albums already use. One switch in the header toggles between them, and the choice is remembered -
// a library is looked at the same way twice.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.db.entities.Playlist
import com.mocha.mochymusic.ui.screens.library.LibraryEntityListScreen
import com.mocha.mochymusic.ui.screens.search.SearchColors
import com.mocha.mochymusic.utils.rememberPreference

/** Remembered between visits: the same library, looked at the same way. */
private val PlaylistsGridViewKey = booleanPreferencesKey("playlistsGridView")

@Composable
fun PlaylistsCollectionScreen(
    playlists: List<Playlist>,
    title: String,
    subtitle: String,
    searchPlaceholder: String,
    onOpen: (Playlist) -> Unit,
    onLongClick: (Playlist) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val (grid, setGrid) = rememberPreference(PlaylistsGridViewKey, false)
    val resources = LocalContext.current.resources

    val toggle: @Composable () -> Unit = {
        Icon(
            painter = painterResource(if (grid) R.drawable.list else R.drawable.grid_view),
            contentDescription = stringResource(
                if (grid) R.string.lib_playlists_show_list else R.string.lib_playlists_show_grid,
            ),
            tint = SearchColors.Primary,
            modifier = Modifier
                .size(22.dp)
                .clickable { setGrid(!grid) }
                .padding(1.dp),
        )
    }

    if (grid) {
        PlaylistGridScreen(
            playlists = playlists,
            title = title,
            subtitle = subtitle,
            searchPlaceholder = searchPlaceholder,
            onOpen = onOpen,
            onLongClick = onLongClick,
            trailing = toggle,
            modifier = modifier,
            pureBlack = pureBlack,
        )
    } else {
        LibraryEntityListScreen(
            title = title,
            subtitle = subtitle,
            searchPlaceholder = searchPlaceholder,
            items = playlists,
            idOf = { it.id },
            nameOf = { it.playlist.name },
            subtitleOf = { resources.getQuantityString(R.plurals.lib_song_count, it.songCount, it.songCount) },
            thumbnailOf = { it.thumbnails.firstOrNull() },
            // the same key Home's cards use, so the playlist opens growing out of its cover
            sharedElementKeyOf = { "playlist-${it.id}" },
            onClick = onOpen,
            onLongClick = onLongClick,
            // the grid already shows this bar; a collection that grows one control in one mode is
            // the same screen looking like two
            playBar = {
                com.mocha.mochymusic.ui.screens.library.LibraryPlayBar(
                    onPlay = { playlists.firstOrNull()?.let(onOpen) },
                    onMore = { playlists.randomOrNull()?.let(onOpen) },
                )
            },
            trailing = toggle,
            modifier = modifier,
            pureBlack = pureBlack,
        )
    }
}
