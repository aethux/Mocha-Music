// LibraryPlaylistsScreen.kt

package com.mocha.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.importer.ImportSource
import com.mocha.mochymusic.ui.component.LocalMenuState
import com.mocha.mochymusic.ui.menu.PlaylistMenu
import com.mocha.mochymusic.viewmodels.LibraryHomeViewModel
import com.mocha.mochymusic.viewmodels.LibraryShelfKind

/**
 * The library's playlists, or with a [shelf] the Local or Cached tab's: only the playlists that
 * have one of that tab's songs in them. With [source], only the playlists imported from that service.
 */
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
    shelf: LibraryShelfKind? = null,
    source: ImportSource? = null,
) {
    if (shelf == null && source == null) {
        LibraryPlaylistsPage(navController)
        return
    }
    val viewModel: LibraryHomeViewModel = hiltViewModel()
    val all by (shelf?.let(viewModel::shelf)?.playlists ?: viewModel.playlists).collectAsStateWithLifecycle()
    val playlistSources by viewModel.playlistSources.collectAsState()
    val playlists = remember(all, playlistSources, source) {
        if (source == null) all else all.filter { playlistSources[it.id] == source }
    }
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val resources = LocalContext.current.resources

    // MOCHY-APPLE: list or grid, whichever was last chosen; both live in applesync/ui
    com.mocha.mochymusic.applesync.ui.PlaylistsCollectionScreen(
        playlists = playlists,
        title = stringResource(R.string.lib_playlist),
        subtitle = when {
            source != null -> stringResource(R.string.lib_playlists_imported_subtitle, source.label)
            shelf == LibraryShelfKind.Local -> stringResource(R.string.lib_playlists_local_subtitle)
            shelf == LibraryShelfKind.Cached -> stringResource(R.string.lib_playlists_cached_subtitle)
            else -> stringResource(R.string.lib_playlists_all_subtitle)
        },
        searchPlaceholder = stringResource(R.string.lib_playlists_search),
        onOpen = { playlist -> navController.navigate("local_playlist/${playlist.id}") },
        onLongClick = { playlist ->
            menuState.show {
                PlaylistMenu(
                    playlist = playlist,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                    navController = navController,
                )
            }
        },
        navController = navController,
        modifier = modifier,
        pureBlack = pureBlack,
    )
}
