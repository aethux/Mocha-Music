// AppleRemovedScreen.kt
//
// The songs Apple no longer has, and the three answers to each: keep it here, take it out of this
// library too, or put it back on Apple. Two of those are bulk, because the usual case is a whole
// playlist's worth of songs going away at once.
//
// Nothing here deletes a song row: "remove" clears the library flag, so history, playlists and the
// like stay where they are. "Put it back" is the same verified write as any other library add.

package com.mocha.mochymusic.ui.applesync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mocha.mochymusic.LocalDatabase
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.AppleRemoved
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.ui.component.SubSettingsScaffold
import com.mocha.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val AppleRemovedRoute = "apple_removed"

private data class Row(val catalogId: String, val title: String, val artists: String)

@Composable
fun AppleRemovedScreen(navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Row>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val dao = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        ).dao()
    }

    suspend fun load() {
        val ids = AppleRemoved.list(dao)
        rows = withContext(Dispatchers.IO) {
            ids.map { id ->
                val song = runCatching { database.song("AM_$id").first() }.getOrNull()
                Row(
                    catalogId = id,
                    title = song?.song?.title ?: id,
                    artists = song?.artists?.joinToString(", ") { it.name }.orEmpty(),
                )
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    SubSettingsScaffold(
        title = stringResource(R.string.apple_removed_title),
        onBack = { navController.navigateUp() },
        backLabel = null,
    ) {
        Text(
            text = stringResource(R.string.apple_removed_blurb),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        if (busy) {
            ImportInfoCard(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                    Spacer(Modifier.size(14.dp))
                    Text(stringResource(R.string.apple_removed_working))
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        val list = rows
        if (list != null && list.isEmpty()) {
            Text(
                text = stringResource(R.string.apple_removed_none),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (!list.isNullOrEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) { list.forEach { AppleRemoved.keep(dao, it.catalogId) } }
                            load()
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.apple_removed_keep_all)) }
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            busy = true
                            withContext(Dispatchers.IO) {
                                list.forEach { AppleRemoved.removeHere(dao, database, it.catalogId) }
                            }
                            load()
                            busy = false
                        }
                    },
                ) { Text(stringResource(R.string.apple_removed_remove_all)) }
            }
            Spacer(Modifier.height(12.dp))
        }

        list?.forEach { song ->
            ImportInfoCard(modifier = Modifier.fillMaxWidth()) {
                Text(text = song.title, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = song.artists,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AppleRemoved.keep(dao, song.catalogId) }
                                load()
                            }
                        },
                    ) { Text(stringResource(R.string.apple_removed_keep)) }
                    TextButton(
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.IO) { AppleRemoved.removeHere(dao, database, song.catalogId) }
                                load()
                            }
                        },
                    ) { Text(stringResource(R.string.apple_removed_remove)) }
                    TextButton(
                        onClick = {
                            scope.launch {
                                busy = true
                                withContext(Dispatchers.IO) { AppleRemoved.readd(context, dao, song.catalogId) }
                                load()
                                busy = false
                            }
                        },
                    ) { Text(stringResource(R.string.apple_removed_readd)) }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(120.dp))
    }
}
