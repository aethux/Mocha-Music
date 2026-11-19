// AppleUnmatchedScreen.kt
//
// "These are the songs Apple doesn't have." One row each, with the two answers a person can give:
// match it on Apple by hand, or keep the YouTube version. Keeping it is remembered, so the song is
// never asked about again; matching goes to the same picker the song menu uses, so there is one
// screen for choosing a recording rather than two.
//
// It asks nothing on its own. The library's card is what brings someone here.

package com.mochyy.mochymusic.ui.applesync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleReconcile
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.ui.component.SettingsGroup
import com.mochyy.mochymusic.ui.component.SettingsGroupStyle
import com.mochyy.mochymusic.ui.component.SettingsItem
import com.mochyy.mochymusic.ui.component.SubSettingsScaffold
import com.mochyy.mochymusic.ui.pick.appleMatchRoute
import com.mochyy.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val AppleUnmatchedRoute = "apple_unmatched"

@Composable
fun AppleUnmatchedScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<AppleReconcile.Unmatched>?>(null) }
    var working by remember { mutableStateOf(true) }

    // the pass runs on entry: it is also what merges anything Apple turns out to have
    LaunchedEffect(Unit) {
        val outcome = withContext(Dispatchers.IO) { runCatching { AppleReconcile.run(context) }.getOrNull() }
        rows = outcome?.unmatched ?: emptyList()
        working = false
    }

    SubSettingsScaffold(
        title = stringResource(R.string.apple_unmatched_title),
        onBack = { navController.navigateUp() },
        backLabel = null,
    ) {
        Text(
            text = stringResource(R.string.apple_unmatched_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (working) {
            ImportInfoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.size(14.dp))
                    Text(
                        text = stringResource(R.string.apple_unmatched_looking),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        } else if (rows.orEmpty().isEmpty()) {
            Text(
                text = stringResource(R.string.apple_unmatched_none),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            // one card per song, the app's own card: the two answers sit under the title rather
            // than beside it, because a settings row squeezes a name into a column too narrow
            rows.orEmpty().forEach { song ->
                ImportInfoCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = song.artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { navController.navigate(appleMatchRoute(song.songId)) }) {
                            Text(stringResource(R.string.apple_unmatched_match))
                        }
                        TextButton(
                            onClick = {
                                rows = rows.orEmpty().filterNot { it.songId == song.songId }
                                scope.launch {
                                    withContext(Dispatchers.IO) {
                                        runCatching {
                                            val dao = EntryPointAccessors.fromApplication(
                                                context.applicationContext,
                                                ApplePlaylistEntryPoint::class.java,
                                            ).dao()
                                            AppleReconcile.keep(dao, song.songId)
                                        }
                                    }
                                }
                            },
                        ) {
                            Text(stringResource(R.string.apple_unmatched_keep))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.apple_unmatched_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(120.dp))
    }
}
