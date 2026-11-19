// ManualYouTubePick.kt

package com.mochyy.mochymusic.ui.pick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.applesync.AppleSongWrites
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.importer.ImportedVersions
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.PopupSheetHandle
import com.mochyy.mochymusic.ui.component.PopupSheetState
import com.mochyy.mochymusic.ui.component.RemoteFocus
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.player.menu.MenuRowSurface
import com.mochyy.mochymusic.ui.screens.library.LibraryGridArtwork
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.utils.makeTimeString
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * "Search YouTube Music": the lookup above guesses by title and artist, and when it guesses wrong
 * there was no way to say so. This types the query the user actually means and plays the result
 * they pick - as that song, not as a one-off: the row becomes the picked video, and the likes,
 * library flag and playlist places move with it, the same way "choose different version" already
 * works for imported songs.
 *
 * YouTube Music only for now. The other providers need their own catalogue search (Monochrome has
 * none at all; Apple's is the rematch story, not this one), and a picker that half-works is worse
 * than one that is honest about its one provider.
 */
@Composable
fun ManualYouTubePick(
    songId: String,
    title: String,
    artist: String,
    onPicked: () -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()

    var open by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    val defaultTerm = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")

    LaunchedEffect(open, query) {
        if (!open) return@LaunchedEffect
        val term = query.ifBlank { defaultTerm }.trim()
        if (term.isBlank()) return@LaunchedEffect
        results = null
        problem = null
        // typing is the search: a pause is only so five letters don't ask five times
        if (query.isNotBlank()) delay(350)
        YouTube.search(term, YouTube.SearchFilter.FILTER_SONG)
            .onSuccess { found -> results = found.items.filterIsInstance<SongItem>().take(20) }
            .onFailure { problem = it.message ?: context.getString(R.string.pick_failed) }
    }

    Text(
        text = stringResource(R.string.pick_manual_title),
        color = SearchColors.Primary,
        fontSize = 17.sp,
        fontWeight = FontWeight.Bold,
    )
    Text(
        text = stringResource(R.string.pick_manual_desc),
        color = SearchColors.Secondary,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MenuRowSurface)
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = RemoteFocus,
                role = Role.Button,
            ) { open = !open }
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.manage_search),
            contentDescription = null,
            tint = SearchColors.Primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = stringResource(if (open) R.string.pick_manual_hide else R.string.pick_manual_open),
            color = SearchColors.Primary,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }

    if (!open) return

    Spacer(Modifier.heightIn(min = 10.dp))
    SheetSearchField(
        value = query,
        onValueChange = { query = it },
        placeholder = defaultTerm.ifBlank { stringResource(R.string.pick_manual_search) },
    )
    Spacer(Modifier.heightIn(min = 8.dp))

    val list = results
    val message = when {
        problem != null -> problem
        list == null -> stringResource(R.string.pick_looking)
        list.isEmpty() -> stringResource(R.string.pick_none)
        else -> null
    }
    if (message != null) {
        Text(
            text = message,
            color = SearchColors.Secondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(vertical = 14.dp),
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.heightIn(max = 340.dp),
    ) {
        items(list.orEmpty(), key = { it.id }) { item ->
            val same = item.id == songId
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .clickable(enabled = !same, role = Role.Button) {
                        scope.launch {
                            // an Apple song stays an Apple song: its catalogue id moves to the chosen
                            // recording, so a later import finds that one and the heart still lands
                            val appleId = runCatching { AppleSongWrites.appleIdFor(context, songId) }.getOrNull()
                            // the pick becomes the song: likes, library flag and playlist places move
                            // with it, so the next play finds the right recording without asking again
                            runCatching { ImportedVersions.replace(context, database, songId, item) }
                                .onFailure { problem = it.message }
                            if (appleId != null) {
                                runCatching {
                                    AppleSongWrites.keepAppleIdentity(
                                        context = context,
                                        songId = item.id,
                                        catalogId = appleId,
                                        title = item.title,
                                        artist = item.artists.joinToString(", ") { it.name },
                                        album = item.album?.name,
                                    )
                                }
                            }
                            playerConnection.playQueue(
                                ListQueue(
                                    title = item.title,
                                    items = listOf(item.toMediaItem()),
                                ),
                            )
                            TopToaster.show(
                                text = context.getString(R.string.pick_playing, item.title),
                                thumbnail = item.thumbnail,
                                iconRes = R.drawable.sync,
                            )
                            onPicked()
                        }
                    }
                    .padding(horizontal = 6.dp, vertical = 6.dp),
            ) {
                LibraryGridArtwork(url = item.thumbnail, corner = 12.dp, modifier = Modifier.size(48.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.title,
                        color = SearchColors.Primary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            item.artists.joinToString { it.name }.ifBlank { null },
                            item.album?.name,
                        ).joinToString(" · "),
                        color = SearchColors.Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = if (same) {
                        stringResource(R.string.pick_already)
                    } else {
                        item.duration?.let { makeTimeString(it * 1000L) } ?: ""
                    },
                    color = SearchColors.Secondary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
