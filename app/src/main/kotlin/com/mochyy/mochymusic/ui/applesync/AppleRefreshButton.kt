// AppleRefreshButton.kt

package com.mochyy.mochymusic.ui.applesync

import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleSyncRunner
import com.mochyy.mochymusic.applesync.AppleSyncStatus
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import dagger.hilt.android.EntryPointAccessors

/**
 * The refresh in the Library header: read Apple's library and playlists into the replica.
 *
 * It spins while it works, and the progress itself belongs to [AppleSyncCard], which any screen that
 * can see a library read draws. One source of truth means the button and the list can never disagree
 * about whether something is happening.
 */
@Composable
fun AppleRefreshButton(tint: Color = Color.Unspecified) {
    val context = LocalContext.current
    // the "not on Apple" dots come from memory, which empties on every app start; without this the
    // list would show no dots until a sync ran, which reads as the dots having been removed
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (!AppleSyncStatus.known) {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                ApplePlaylistEntryPoint::class.java,
            )
            runCatching { entry.dao().syncedSongIds().toSet() }.onSuccess(AppleSyncStatus::setSynced)
            // a song the add path matched has a track row and no replica row: still Apple's
            runCatching { entry.dao().trackedSongIds().toSet() }.onSuccess(AppleSyncStatus::setTracked)
            // a manually matched song is on Apple too; without this its dot would come back on every start
            runCatching {
                entry.dao().rematchedSongIds(com.mochyy.mochymusic.applesync.db.AppleRematchEntity.SOURCE_APPLE).toSet()
            }.onSuccess(AppleSyncStatus::setManualApple)
        }
    }

    if (AppleSyncStatus.progress.running || AppleSyncRunner.isRunning) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            strokeWidth = 2.dp,
            // the header's tint is Unspecified, which draws nothing on a dark background
            color = if (tint == Color.Unspecified) LocalContentColor.current else tint,
        )
        return
    }

    IconButton(
        onClick = {
            // the whole refresh lives in AppleSyncFlow; the service keeps it alive once the app is
            // in the background and reports it in the notification shade
            com.mochyy.mochymusic.applesync.AppleSyncService.start(context)
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.refresh),
            contentDescription = stringResource(R.string.apple_refresh_desc),
            tint = tint,
        )
    }
}
