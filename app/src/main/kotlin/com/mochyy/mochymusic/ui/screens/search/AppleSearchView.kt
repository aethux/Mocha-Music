// AppleSearchView.kt
//
// The search's source chips and the Apple side of them. Both chip rows are the app's own
// [SearchCategoryRow], so the spacing is the spacing of the filters above them rather than a copy
// of it that drifts.

package com.mochyy.mochymusic.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.LocalPlayerConnection
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleAdopt
import com.mochyy.mochymusic.extensions.toMediaItem
import com.mochyy.mochymusic.playback.queues.ListQueue
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.screens.library.LibraryGridArtwork
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Where a search looks. Apple is the extra one; the other two are the app's own results. */
enum class SearchSource(val labelRes: Int) {
    All(R.string.search_source_all),
    YouTubeMusic(R.string.search_source_yt),
    AppleMusic(R.string.search_source_apple),
}

/** The app's music/artist/album/video chips, applied to Apple's results. */
private enum class AppleKindChip(val labelRes: Int, val kind: AppleAdopt.Kind?) {
    All(R.string.search_category_all, null),
    Songs(R.string.search_category_music, AppleAdopt.Kind.Song),
    Artists(R.string.search_category_artist, AppleAdopt.Kind.Artist),
    Albums(R.string.search_category_album, AppleAdopt.Kind.Album),
    Playlists(R.string.search_category_playlist, AppleAdopt.Kind.Playlist),
    Videos(R.string.search_category_video, AppleAdopt.Kind.Video),
}

// the Library home's chips: pills sitting in a dark track, the picked one a step lighter
private val ChipTrack = Color(0xFF161618)
private val Chip = Color(0xFF232326)
private val ChipPicked = Color(0xFF3D3D41)

@Composable
private fun TrackChips(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .padding(horizontal = SearchHorizontalPadding, vertical = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(ChipTrack)
            .padding(3.dp)
            .horizontalScroll(rememberScrollState()),
    ) {
        labels.forEachIndexed { index, label ->
            val picked = index == selectedIndex
            Text(
                text = label,
                color = if (picked) Color.White else Color.White.copy(alpha = 0.72f),
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (picked) ChipPicked else Chip)
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
fun SearchSourceChips(
    current: SearchSource,
    onSelect: (SearchSource) -> Unit,
    modifier: Modifier = Modifier,
) {
    // the app's own filter style: sources sit under the media filters and should read as one row
    SearchCategoryRow(
        categories = SearchSource.entries.map { stringResource(it.labelRes) },
        selectedIndex = current.ordinal,
        onSelect = { onSelect(SearchSource.entries[it]) },
        modifier = modifier,
    )
}

/**
 * Apple's catalogue for a term, as the page its chip opens.
 *
 * Every kind of result comes back in one request and the chips filter that list, so switching
 * between songs and albums costs nothing. An artist or an album opens as a search for its songs:
 * Apple's artist and album pages have no screen here, and their songs are what can be played.
 */
@Composable
fun AppleMusicSearchPage(
    query: String,
    pureBlack: Boolean,
    source: SearchSource,
    onSourceChange: (SearchSource) -> Unit,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()

    var term by remember(query) { mutableStateOf(query) }
    var chip by remember { mutableStateOf(AppleKindChip.All) }
    var hits by remember { mutableStateOf<Map<AppleAdopt.Kind, List<AppleAdopt.Hit>>?>(null) }
    var owned by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(term) {
        hits = null
        val found = AppleAdopt.searchEverything(context, term)
        hits = found
        owned = AppleAdopt.inLibrary(context, found.values.flatten().map { it.id })
    }

    val visible = remember(hits, chip) {
        val all = hits ?: return@remember emptyList()
        if (chip.kind == null) {
            // Apple's own order: songs, then artists, albums, playlists, videos
            AppleAdopt.Kind.entries.flatMap { all[it].orEmpty() }
        } else {
            all[chip.kind].orEmpty()
        }
    }

    val insets = WindowInsets.statusBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack))
            .padding(top = insets.calculateTopPadding()),
    ) {
        Text(
            text = term,
            color = SearchColors.Primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp),
        )
        SearchSourceChips(current = source, onSelect = onSourceChange)
        TrackChips(
            labels = AppleKindChip.entries.map { stringResource(it.labelRes) },
            selectedIndex = chip.ordinal,
            onSelect = { chip = AppleKindChip.entries[it] },
        )

        if (hits == null) {
            Text(
                text = stringResource(R.string.search_apple_looking),
                color = SearchColors.Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
            return@Column
        }
        if (visible.isEmpty()) {
            Text(
                text = stringResource(R.string.search_apple_none),
                color = SearchColors.Secondary,
                fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )
            return@Column
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = 140.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(visible, key = { "${it.kind}/${it.id}" }) { hit ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) {
                            when (hit.kind) {
                                // an artist or an album is a way into their songs, not a page
                                AppleAdopt.Kind.Artist, AppleAdopt.Kind.Album -> {
                                    term = listOfNotNull(hit.title, hit.subtitle).joinToString(" ")
                                    chip = AppleKindChip.Songs
                                }

                                AppleAdopt.Kind.Playlist, AppleAdopt.Kind.Video -> TopToaster.show(
                                    text = context.getString(R.string.search_apple_not_playable),
                                )

                                AppleAdopt.Kind.Song -> scope.launch {
                                    val songId = AppleAdopt.song(
                                        context = context,
                                        database = database,
                                        catalogId = hit.id,
                                        title = hit.title,
                                        artist = hit.subtitle.orEmpty(),
                                        album = null,
                                        durationMs = hit.durationMs,
                                        artworkUrl = hit.artworkUrl,
                                        addToLibrary = false,
                                    )
                                    if (songId != null) {
                                        val row = database.song(songId).firstOrNull() ?: return@launch
                                        playerConnection?.playQueue(
                                            ListQueue(title = hit.title, items = listOf(row.toMediaItem())),
                                        )
                                    }
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    LibraryGridArtwork(
                        url = hit.artworkUrl?.replace("{w}", "300")?.replace("{h}", "300"),
                        corner = if (hit.kind == AppleAdopt.Kind.Artist) 48.dp else 10.dp,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = hit.title,
                            color = SearchColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(hit.subtitle, kindLabel(hit.kind))
                                .filter { it.isNotBlank() }
                                .joinToString(" · "),
                            color = SearchColors.Secondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    if (hit.id in owned) {
                        // already in the Apple library: the same mark the catalogue rows carry
                        androidx.compose.material3.Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.library_add_check),
                            contentDescription = stringResource(R.string.search_apple_in_library),
                            tint = SearchColors.Secondary,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(
                        text = hit.durationMs?.takeIf { hit.kind == AppleAdopt.Kind.Song }
                            ?.let { makeTimeString(it) } ?: "",
                        color = SearchColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun kindLabel(kind: AppleAdopt.Kind): String = when (kind) {
    AppleAdopt.Kind.Song -> ""
    AppleAdopt.Kind.Artist -> stringResource(R.string.search_category_artist)
    AppleAdopt.Kind.Album -> stringResource(R.string.search_category_album)
    AppleAdopt.Kind.Playlist -> stringResource(R.string.search_category_playlist)
    AppleAdopt.Kind.Video -> stringResource(R.string.search_category_video)
}
