// SongActionTiles.kt

package com.mochyy.mochymusic.ui.menu

import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.core.net.toUri
import com.music.innertube.YouTube
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalSyncUtils
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleSongWrites
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.models.MediaMetadata
import com.mochyy.mochymusic.models.toMediaMetadata
import com.mochyy.mochymusic.playback.ExoDownloadService
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.component.SheetActionCard
import com.mochyy.mochymusic.ui.component.SheetActionCardRow
import com.mochyy.mochymusic.ui.component.SheetDownloadCard
import com.mochyy.mochymusic.ui.player.menu.MenuRow
import com.mochyy.mochymusic.ui.player.share.song.SongShare
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The four cards, and only the cards: put it in the library, put it in a playlist, keep a copy,
 * love it - in that order, in two rows.
 *
 * The actions come from the caller because the same four cards mean different songs depending on
 * where the menu was opened: what is playing in the player's menu, the row that was long-pressed in
 * a list. The on/off state is read from the database here, so a tap repaints the label without the
 * caller having to thread state through.
 */
@Composable
fun SongActionTileGrid(
    metadata: MediaMetadata?,
    downloadState: Int?,
    onLibrary: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onRemoveDownload: () -> Unit,
    onLike: () -> Unit,
) {
    val database = LocalDatabase.current
    // the row, not the queue's copy: metadata is built when a song is queued, so it says "not in
    // the library" long after the button next to it put the song there - and toggleLibrary reads
    // the row, so the label and the action disagreed
    val row by remember(metadata?.id) { database.song(metadata?.id) }.collectAsState(initial = null)
    val inLibrary = (row?.song?.inLibrary ?: metadata?.inLibrary) != null
    val liked = row?.song?.liked ?: (metadata?.liked == true)

    SheetActionCardRow {
        SheetActionCard(
            icon = if (inLibrary) R.drawable.library_add_check else R.drawable.library_add,
            title = stringResource(if (inLibrary) R.string.remove_from_library else R.string.add_to_library),
            enabled = metadata != null,
            onClick = onLibrary,
            modifier = Modifier.weight(1f),
        )
        SheetActionCard(
            icon = R.drawable.playlist_add,
            title = stringResource(R.string.add_to_playlist),
            enabled = metadata != null,
            onClick = onAddToPlaylist,
            modifier = Modifier.weight(1f),
        )
    }

    Spacer(Modifier.height(8.dp))

    SheetActionCardRow {
        SheetDownloadCard(
            downloadState = downloadState,
            onDownload = onDownload,
            onRemove = onRemoveDownload,
            modifier = Modifier.weight(1f),
        )
        SheetActionCard(
            icon = if (liked) R.drawable.favorite else R.drawable.favorite_border,
            title = stringResource(if (liked) R.string.menu_tile_unlike else R.string.menu_tile_like),
            enabled = metadata != null,
            onClick = onLike,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The four things a song's menu is opened for, in one grid and one order, and then the switches
 * that belong to what is playing.
 *
 * The grid itself is [SongActionTileGrid]: the player's menu and a library row's menu open with the
 * same four cards in the same places, and only the actions behind them differ.
 */
@Composable
fun SongActionTiles(
    metadata: MediaMetadata?,
    downloadState: Int?,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
    /** the caller owns the picker, since it also owns the dialog it opens */
    onAddToPlaylist: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val repeatMode by playerConnection.repeatMode.collectAsState()

    SongActionTileGrid(
        metadata = metadata,
        downloadState = downloadState,
        // the same calls the player's round buttons make, so Apple hears about it once
        onLibrary = {
            playerConnection.toggleLibrary()
            onDismiss()
        },
        onAddToPlaylist = onAddToPlaylist,
        onDownload = { metadata?.let { downloadSong(context, database, it) } },
        onRemoveDownload = { metadata?.let { removeDownload(context, it) } },
        onLike = {
            playerConnection.toggleLike()
            onDismiss()
        },
    )

    Spacer(Modifier.height(6.dp))

    val shuffleOn by playerConnection.shuffleModeEnabled.collectAsState()
    MenuRow(
        icon = R.drawable.shuffle,
        title = stringResource(
            if (shuffleOn) R.string.ps_action_shuffle_off else R.string.ps_action_shuffle,
        ),
        enabled = metadata != null,
        onClick = {
            playerConnection.player.shuffleModeEnabled = !shuffleOn
            onDismiss()
        },
    )

    // the chevron's small menu used to hold these; it opens this sheet now, so they live here
    Spacer(Modifier.height(4.dp))

    MenuRow(
        icon = R.drawable.repeat,
        title = stringResource(
            when (repeatMode) {
                Player.REPEAT_MODE_ONE -> R.string.ps_action_repeat_off
                Player.REPEAT_MODE_ALL -> R.string.ps_action_repeat_one
                else -> R.string.ps_action_repeat_all
            },
        ),
        enabled = metadata != null,
        onClick = {
            playerConnection.player.repeatMode = when (repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
            onDismiss()
        },
    )

    Spacer(Modifier.height(8.dp))

    // a file on the phone has no station to start
    MenuRow(
        icon = R.drawable.radio,
        title = stringResource(R.string.start_radio),
        enabled = metadata != null && metadata.localUri == null,
        onClick = {
            playerConnection.startRadioSeamlessly()
            onDismiss()
        },
    )

    Spacer(Modifier.height(4.dp))

    MenuRow(
        icon = R.drawable.share,
        title = stringResource(R.string.share),
        enabled = metadata != null,
        onClick = {
            metadata?.let { song ->
                onDismiss()
                SongShare.open(song)
            }
        },
    )
}

/**
 * The same four cards, wired for a song that is a row rather than what is playing.
 *
 * A library row's menu has to act on *that* song, so it cannot call the player's toggle buttons:
 * these are the row-level versions, with the Apple write first and the YouTube path only when Apple
 * does not own the song.
 */
@Composable
fun SongRowActionGrid(
    song: Song,
    downloadState: Int?,
    coroutineScope: CoroutineScope,
    onAddToPlaylist: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val syncUtils = LocalSyncUtils.current
    // a removal the guard refuses is asked here, in a dialog: the toast's action vanished before
    // it could be tapped, and the song stayed in the library with no way to say yes
    var askToRemove by remember(song.id) { mutableStateOf(false) }

    SongActionTileGrid(
        metadata = song.toMediaMetadata(),
        downloadState = downloadState,
        onLibrary = {
            val isInLibrary = song.song.inLibrary != null
            coroutineScope.launch {
                if (isInLibrary && AppleSongWrites.removalNeedsConfirmation(context, song)) {
                    askToRemove = true
                    return@launch
                }
                // an Apple song's flag is Apple's; its row follows Apple's answer
                if (AppleSongWrites.pushLibrary(context, song, !isInLibrary)) return@launch
                val token = if (isInLibrary) song.song.libraryRemoveToken else song.song.libraryAddToken
                token?.let { feedback -> YouTube.feedback(listOf(feedback)) }
                database.query { update(song.song.toggleLibrary()) }
            }
        },
        onAddToPlaylist = onAddToPlaylist,
        onDownload = {
            val request = DownloadRequest
                .Builder(song.id, song.id.toUri())
                .setCustomCacheKey(song.id)
                .setData(song.song.title.toByteArray())
                .build()
            DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
        },
        onRemoveDownload = {
            DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, song.id, false)
        },
        onLike = {
            // the sync operation is the path that reaches Apple, so a row's heart is pushed too
            val toggled = song.song.toggleLike()
            database.query { update(toggled) }
            syncUtils.likeSong(toggled)
        },
    )

    if (askToRemove) {
        com.mochyy.mochymusic.ui.component.DefaultDialog(
            onDismiss = { askToRemove = false },
            title = { Text(stringResource(R.string.apple_write_remove_anyway)) },
            buttons = {
                TextButton(onClick = { askToRemove = false }) {
                    Text(stringResource(com.mochyy.mochymusic.R.string.yt_sync_cancel))
                }
                TextButton(
                    onClick = {
                        askToRemove = false
                        coroutineScope.launch {
                            AppleSongWrites.pushLibrary(context, song, false, confirmed = true)
                        }
                    },
                ) {
                    Text(stringResource(R.string.apple_write_remove_anyway))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.apple_write_remove_confirm, song.song.title),
                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/** the song has to be a row before its download can be listed or played back from there */
private fun downloadSong(context: Context, database: MusicDatabase, metadata: MediaMetadata) {
    database.transaction { insert(metadata) }
    val request = DownloadRequest
        .Builder(metadata.id, metadata.id.toUri())
        .setCustomCacheKey(metadata.id)
        .setData(metadata.title.toByteArray())
        .build()
    DownloadService.sendAddDownload(context, ExoDownloadService::class.java, request, false)
}

private fun removeDownload(context: Context, metadata: MediaMetadata) {
    DownloadService.sendRemoveDownload(context, ExoDownloadService::class.java, metadata.id, false)
}
