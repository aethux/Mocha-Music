// AppleLibrarySummary.kt
//
// The line at the end of the library: what it holds, how much of it is on Apple, and when it was
// last synced. Every count that has a screen behind it opens that screen; the ones that do not are
// plain text, because a chip that does nothing is worse than no chip.
//
// Deliberately small: it is a footer, not a dashboard. Same chip as the search categories (34 dp,
// fully rounded, 14 sp medium) so it reads as part of Mochy rather than something bolted on.

package com.mochyy.mochymusic.applesync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleSyncFlow
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.ui.screens.search.SearchHorizontalPadding
import dagger.hilt.android.EntryPointAccessors

import java.text.NumberFormat
import java.util.Locale

/** Apple's rows are `AM_`, a local file is `LOCAL_`, everything else came from YouTube. */
private const val APPLE_PREFIX = "AM_"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppleLibrarySummary(
    songs: List<Song>,
    albumCount: Int,
    playlistCount: Int,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val onApple = songs.count { it.id.startsWith(APPLE_PREFIX) }
    val numbers = NumberFormat.getIntegerInstance(Locale.getDefault())
    val lastSync = lastSyncLine()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchHorizontalPadding)
            .padding(top = 24.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // One sentence, not a row of buttons: every count is a word you can tap.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            SentenceLink(stringResource(R.string.apple_summary_songs, numbers.format(songs.size))) {
                navController.navigate("library/songs")
            }
            if (albumCount > 0) {
                SentenceDot()
                SentenceLink(stringResource(R.string.apple_summary_albums, numbers.format(albumCount))) {
                    navController.navigate("library/albums")
                }
            }
            if (playlistCount > 0) {
                SentenceDot()
                SentenceLink(stringResource(R.string.apple_summary_playlists, numbers.format(playlistCount))) {
                    navController.navigate("library/playlists")
                }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            SentenceLink(stringResource(R.string.apple_summary_on_apple, numbers.format(onApple))) {
                navController.navigate(appleSongsRoute("apple"))
            }
            val youtube = songs.size - onApple
            if (youtube > 0) {
                SentenceDot()
                SentenceLink(stringResource(R.string.apple_summary_on_youtube, numbers.format(youtube))) {
                    navController.navigate(appleSongsRoute("youtube"))
                }
            }
            SentenceDot()
            Text(
                text = lastSync ?: stringResource(R.string.apple_summary_not_synced),
                style = MaterialTheme.typography.bodySmall,
                color = SearchColors.Secondary,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

/** A tappable word in the sentence: body size, in the colour the app uses for links. */
@Composable
private fun SentenceLink(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodySmall,
        color = SearchColors.Primary,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
    )
}

@Composable
private fun SentenceDot() {
    Text(
        text = " · ",
        style = MaterialTheme.typography.bodySmall,
        color = SearchColors.Secondary,
        modifier = Modifier.padding(vertical = 4.dp),
    )
}

/**
 * "synced 3 min ago", observed from the sidecar. The sync writes the timestamp when it finishes, and
 * a Flow means the footer moves the moment it does instead of when the screen is next opened.
 */
@Composable
private fun lastSyncLine(): String? {
    val context = LocalContext.current
    val dao = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        ).dao()
    }
    val states by remember(dao) { dao.stateFlow() }.collectAsState(initial = emptyList())
    return states.firstOrNull { it.key == AppleSyncFlow.KEY_LAST_SYNC_AT }
        ?.value?.toLongOrNull()?.let { at -> ago(at) }
}

@Composable
private fun ago(at: Long): String {
    val minutes = ((System.currentTimeMillis() - at) / 60_000L).coerceAtLeast(0).toInt()
    return when {
        minutes < 1 -> stringResource(R.string.apple_summary_just_now)
        minutes < 60 -> stringResource(R.string.apple_summary_minutes, minutes)
        minutes < 60 * 24 -> stringResource(R.string.apple_summary_hours, minutes / 60)
        minutes < 60 * 24 * 2 -> stringResource(R.string.apple_summary_yesterday)
        else -> stringResource(R.string.apple_summary_days, minutes / (60 * 24))
    }
}

/**
 * The call site is one line: this is the footer at the end of the library's All tab. Lives here so
 * the library screen does not carry the layout.
 */
fun LazyListScope.appleLibrarySummary(
    songs: List<Song>,
    albumCount: Int,
    playlistCount: Int,
    navController: NavController,
) = item(key = "apple_summary") {
    AppleLibrarySummary(
        songs = songs,
        albumCount = albumCount,
        playlistCount = playlistCount,
        navController = navController,
    )
}
