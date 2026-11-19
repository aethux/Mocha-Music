// SongMenuRows.kt
//
// Rows a song's menu needs that are not Apple's: kept out of the upstream file so its seam stays a
// line per row.

package com.mochyy.mochymusic.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.PlayerConnection
import com.mochyy.mochymusic.ui.component.Material3MenuItemData

/**
 * Play next, as a row.
 *
 * It was one of the two big cards; the four tiles took that row, and play-next is a queue action
 * like the one it now sits beside.
 */
fun playNextMenuItem(
    song: Song,
    playerConnection: PlayerConnection,
    onDismiss: () -> Unit,
): Material3MenuItemData = Material3MenuItemData(
    title = { Text(text = stringResource(R.string.play_next)) },
    icon = {
        Icon(
            painter = painterResource(R.drawable.playlist_play),
            contentDescription = null,
        )
    },
    onClick = {
        onDismiss()
        playerConnection.playNext(song.toMediaItem())
    },
)
