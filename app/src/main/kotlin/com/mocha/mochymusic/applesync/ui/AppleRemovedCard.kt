// AppleRemovedCard.kt
//
// "These were in your library, and Apple no longer has them." The other half of the matching card:
// that one asks about songs Apple never had, this one about songs it has taken away. Both are one
// sentence and a way in, and both disappear when there is nothing left to say.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.layout.Spacer
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.AppleRemoved
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.ui.applesync.AppleRemovedRoute
import com.mocha.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import dagger.hilt.android.EntryPointAccessors

@Composable
fun AppleRemovedCard(navController: NavController, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        ).dao()
    }
    val states by remember(dao) { dao.stateFlow() }.collectAsState(initial = emptyList())
    val removed = states.firstOrNull { it.key == AppleRemoved.KEY }?.value
        ?.split(",")?.filter { it.isNotBlank() }.orEmpty()
    if (removed.isEmpty()) return

    ImportInfoCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.apple_removed_card, removed.size),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = stringResource(R.string.apple_removed_card_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = { navController.navigate(AppleRemovedRoute) }) {
                Text(stringResource(R.string.apple_unmatched_review))
            }
        }
    }
}
