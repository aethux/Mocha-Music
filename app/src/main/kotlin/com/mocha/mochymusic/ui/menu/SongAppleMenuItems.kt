// SongAppleMenuItems.kt
//
// The two rows that say what a song is on Apple Music: the recording it plays, and the Apple
// recording its heart and its library flag belong to. Handed back as an array so a menu can spread
// them into its own list, where they belong - between "add to playlist" and "share".

package com.mocha.mochymusic.ui.menu

import android.content.Context
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.ui.component.Material3MenuItemData
import com.mocha.mochymusic.ui.component.TopToaster
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.mocha.mochymusic.ui.pick.appleMatchRoute
import com.mocha.mochymusic.ui.pick.pickRecordingRoute

/**
 * Says a song left a playlist, and the playlist it left.
 *
 * A removal used to happen silently - and on an Apple playlist the difference between "gone here
 * and there" and "did the tap work?" is exactly what the person needs to know.
 */
internal fun announceRemovedFromPlaylist(
    context: Context,
    database: MusicDatabase,
    playlistId: String,
    title: String,
    thumbnail: String?,
) {
    CoroutineScope(Dispatchers.IO).launch {
        val name = runCatching { database.playlist(playlistId).first()?.playlist?.name }.getOrNull()
            ?: context.getString(R.string.playlist_fallback_name)
        TopToaster.show(
            text = context.getString(R.string.playlist_removed_song_from, title, name),
            thumbnail = thumbnail,
            iconRes = R.drawable.remove,
        )
    }
}

@Composable
fun appleSongMenuItems(
    songId: String,
    navController: NavController,
    onDismiss: () -> Unit,
): Array<Material3MenuItemData> {
    val context = LocalContext.current
    var kept by remember(songId) { mutableStateOf(false) }
    LaunchedEffect(songId) {
        kept = runCatching {
            val dao = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ApplePlaylistEntryPoint::class.java,
            ).dao()
            songId in dao.decidedSongIds()
        }.getOrDefault(false)
    }
    return arrayOf(
    Material3MenuItemData(
        title = { Text(text = stringResource(R.string.menu_pick_recording)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.manage_search),
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            navController.navigate(pickRecordingRoute(songId))
        },
    ),
    Material3MenuItemData(
        title = { Text(text = stringResource(R.string.menu_match_apple)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            navController.navigate(appleMatchRoute(songId))
        },
    ),
    if (kept) {
        Material3MenuItemData(
            title = { Text(text = stringResource(R.string.menu_apple_ask_again)) },
            description = { Text(text = stringResource(R.string.menu_apple_ask_again_desc)) },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.group_add),
                    contentDescription = null,
                )
            },
            onClick = {
                onDismiss()
                kotlinx.coroutines.MainScope().launch {
                    runCatching {
                        val dao = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            ApplePlaylistEntryPoint::class.java,
                        ).dao()
                        com.mocha.mochymusic.applesync.AppleReconcile.askAgain(dao, songId)
                    }
                }
            },
        )
    } else {
        null
    },
).filterNotNull().toTypedArray()
}
