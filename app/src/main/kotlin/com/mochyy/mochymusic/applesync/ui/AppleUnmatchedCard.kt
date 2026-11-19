// AppleUnmatchedCard.kt
//
// The library's one question: these songs are not on Apple Music, what do you want done with them?
//
// Apple is the library of record, so a song that exists on both sides is already one song by the
// time this draws - the reconcile pass folds duplicates into their Apple copy. What is left is
// either genuinely absent from Apple or waiting for a person, and it is this card that says how
// many, and this card that goes away when the answer is zero.

package com.mochyy.mochymusic.applesync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.applesync.AppleSyncCard
import com.mochyy.mochymusic.ui.applesync.AppleUnmatchedRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors

/**
 * The sync's progress and, under it, the songs Apple does not have. One entry point for both so the
 * library's header carries one line of fork code and not two.
 */
@Composable
fun appleStatusCards(songs: List<Song>, navController: NavController) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        AppleSyncCard()
        AppleUnmatchedCard(songs = songs, navController = navController)
    }
}

@Composable
fun AppleUnmatchedCard(songs: List<Song>, navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        ).dao()
    }
    // observed, so "keep on YouTube" - or a match picked on the review screen - makes the row and
    // the card disappear where it was answered
    val decided by remember(dao) { dao.decidedSongIdsFlow() }.collectAsState(initial = emptyList())
    val pinned by remember(dao) {
        dao.rematchSongIdsFlow(com.mochyy.mochymusic.applesync.db.AppleRematchEntity.SOURCE_APPLE)
    }.collectAsState(initial = emptyList())
    // a song the add path matched on Apple has a track row: it is answered, not pending
    val tracked by remember(dao) { dao.trackedSongIdsFlow() }.collectAsState(initial = emptyList())
    val count = remember(songs, decided, pinned, tracked) {
        songs.count { song ->
            com.mochyy.mochymusic.applesync.AppleReconcile.stillInQuestion(
                song.id,
                decided + tracked,
                pinned,
            )
        }
    }

    // A row exists for a song the person already answered, so the count is the question itself.
    if (count == 0) return

    ImportInfoCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.apple_unmatched_card, count),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.apple_unmatched_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { navController.navigate(AppleUnmatchedRoute) }) {
                Text(stringResource(R.string.apple_unmatched_review))
            }
        }
    }
}
