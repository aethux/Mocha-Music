// SongInfoFooter.kt
//
// The two lines a menu can end with: what Apple makes of this song, and where its cover came from.
// One composable so the player's menu and the song menu say exactly the same thing.

package com.mocha.mochymusic.ui.menu

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.AppleCovers
import com.mocha.mochymusic.applesync.AppleSongWrites
import com.mocha.mochymusic.applesync.AppleSyncStatus
import kotlinx.coroutines.launch

/**
 * @param inLibrary whether the row is already in the library; null when the caller has not read it
 */
@Composable
fun SongInfoFooter(
    songId: String?,
    inLibrary: Boolean?,
    modifier: Modifier = Modifier,
) {
    if (songId == null || !AppleSyncStatus.known) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val synced = AppleSyncStatus.isSynced(songId)
    var coverSource by remember(songId) { mutableStateOf<String?>(null) }
    var busy by remember(songId) { mutableStateOf(false) }

    LaunchedEffect(songId) {
        coverSource = AppleCovers.cachedSource(context, songId)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (!synced && !busy) {
                    Modifier.clickable {
                        busy = true
                        scope.launch {
                            if (AppleSongWrites.pushLibrary(context.applicationContext, songId, true)) {
                                AppleSyncStatus.noteSynced(songId)
                                coverSource = AppleCovers.cachedSource(context, songId)
                            }
                            busy = false
                        }
                    }
                } else {
                    Modifier
                },
            )
            .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 18.dp),
    ) {
        Text(
            text = if (busy) {
                stringResource(R.string.menu_apple_info_working)
            } else {
                stringResource(
                    when {
                        synced && inLibrary == true -> R.string.menu_apple_info_library
                        synced -> R.string.menu_apple_info_synced
                        else -> R.string.menu_apple_info_missing
                    },
                )
            },
            color = LocalContentColor.current.copy(alpha = if (synced) 0.55f else 0.8f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
        Text(
            text = stringResource(
                when (coverSource) {
                    AppleCovers.SOURCE_MOTION -> R.string.apple_cover_motion
                    AppleCovers.SOURCE_ART -> R.string.apple_cover_art
                    AppleCovers.SOURCE_YOUTUBE -> R.string.apple_cover_youtube
                    else -> R.string.apple_cover_none
                },
            ),
            color = LocalContentColor.current.copy(alpha = 0.55f),
            fontSize = 11.sp,
            lineHeight = 15.sp,
        )
    }
}
