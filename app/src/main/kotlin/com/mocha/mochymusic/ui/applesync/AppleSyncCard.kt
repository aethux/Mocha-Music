// AppleSyncCard.kt

package com.mocha.mochymusic.ui.applesync

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mocha.mochymusic.R
import androidx.compose.ui.unit.dp
import com.mocha.mochymusic.applesync.AppleSyncStatus
import com.mocha.mochymusic.ui.screens.settings.importsync.ImportInfoCard

/**
 * The sync's progress, shown wherever a library read can be seen.
 *
 * The same card the import screens use, for the same reason: a long read that says nothing looks
 * broken. Counts are cumulative, so "12 of 1490" means the songs already written, not the current
 * page — the number only goes up.
 */
@Composable
fun AppleSyncCard(modifier: Modifier = Modifier) {
    val progress = AppleSyncStatus.progress
    if (!progress.running && progress.finishedMessage == null) return

    ImportInfoCard(modifier = modifier.fillMaxWidth()) {
        if (progress.running) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                Spacer(Modifier.width(14.dp))
                Text(progress.phase, style = MaterialTheme.typography.bodyLarge)
            }
            Spacer(Modifier.height(10.dp))
            val fraction = progress.fraction
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = buildString {
                    append(stringResource(R.string.apple_card_songs, progress.songs))
                    if (progress.playlists > 0) {
                        append(" · ")
                        append(stringResource(R.string.apple_card_playlists, progress.playlists))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = progress.finishedMessage ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (progress.failed) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                }
                TextButton(onClick = AppleSyncStatus::dismiss) {
                    Text(stringResource(R.string.apple_sync_ok))
                }
            }
        }
    }
}
