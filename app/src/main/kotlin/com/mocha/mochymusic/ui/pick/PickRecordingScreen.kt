// PickRecordingScreen.kt
//
// The picker as the app's own search looks: a field at the top of the screen and a full list of
// results under it, rather than a box inside a sheet. Same job either way - the song becomes the
// recording that was chosen - so both entry points share [pickRecording].

package com.mocha.mochymusic.ui.pick

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.mocha.mochymusic.LocalDatabase
import com.mocha.mochymusic.LocalPlayerConnection
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.AppleSongWrites
import com.mocha.mochymusic.constants.HideExplicitKey
import com.mocha.mochymusic.constants.HideVideoSongsKey
import com.mocha.mochymusic.constants.HideYoutubeShortsKey
import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.extensions.toMediaItem
import com.mocha.mochymusic.importer.ImportedVersions
import com.mocha.mochymusic.playback.PlayerConnection
import com.mocha.mochymusic.playback.queues.ListQueue
import com.music.innertube.models.filterExplicit
import com.music.innertube.models.filterVideoSongs
import com.music.innertube.models.filterYoutubeShorts
import com.mocha.mochymusic.ui.component.SheetSearchField
import com.mocha.mochymusic.ui.component.TopToaster
import com.mocha.mochymusic.ui.screens.library.LibraryGridArtwork
import com.mocha.mochymusic.ui.screens.search.SearchColors
import com.mocha.mochymusic.utils.dataStore
import com.mocha.mochymusic.utils.get
import com.mocha.mochymusic.utils.makeTimeString
import com.music.innertube.YouTube
import com.music.innertube.models.SongItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val PickRecordingRoute = "pick_recording/{songId}"

fun pickRecordingRoute(songId: String): String = "pick_recording/$songId"

/** The route's argument: a song id, however odd its characters. */
val PickRecordingArguments = listOf(navArgument("songId") { type = NavType.StringType })

/**
 * The song becomes [item]: its likes, playlist places, library flag and import records move onto
 * the chosen recording, an Apple song keeps its catalogue id, and it starts playing.
 */
internal suspend fun pickRecording(
    context: Context,
    database: MusicDatabase,
    playerConnection: PlayerConnection,
    songId: String,
    item: SongItem,
) {
    // an Apple song stays an Apple song: its catalogue id moves to the chosen recording, so a later
    // import finds that one and the heart still lands
    val appleId = runCatching { AppleSongWrites.appleIdFor(context, songId) }.getOrNull()
    runCatching { ImportedVersions.replace(context, database, songId, item) }
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
        ListQueue(title = item.title, items = listOf(item.toMediaItem())),
    )
    TopToaster.show(
        text = context.getString(R.string.pick_playing, item.title),
        thumbnail = item.thumbnail,
        iconRes = R.drawable.sync,
    )
}

/** The picker as a page of its own: the field at the top, the results filling the screen. */
@Composable
fun PickRecordingScreen(songId: String, navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val scope = rememberCoroutineScope()
    val song by remember(songId) { database.song(songId) }.collectAsState(initial = null)

    val title = song?.song?.title.orEmpty()
    val artist = song?.artists?.joinToString(", ") { it.name }.orEmpty()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SongItem>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    // the field starts on the song it is about, once the row has arrived
    LaunchedEffect(title, artist) {
        if (query.isBlank() && title.isNotBlank()) {
            query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.isBlank()) return@LaunchedEffect
        results = null
        problem = null
        delay(350)
        // the app's own search hides explicit songs, video songs and Shorts before it draws them;
        // without the same three, the picker listed rows the search page never shows
        val hideExplicit = context.dataStore.get(HideExplicitKey, false)
        val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)
        val hideYoutubeShorts = context.dataStore.get(HideYoutubeShortsKey, true)
        YouTube.search(term, YouTube.SearchFilter.FILTER_SONG)
            .onSuccess { found ->
                results = found.items
                    .distinctBy { it.id }
                    .filterExplicit(hideExplicit)
                    .filterVideoSongs(hideVideoSongs)
                    .filterYoutubeShorts(hideYoutubeShorts)
                    .filterIsInstance<SongItem>()
                    .take(30)
            }
            .onFailure { problem = it.message ?: context.getString(R.string.pick_failed) }
    }

    val insets = WindowInsets.systemBars.asPaddingValues()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchColors.page(pureBlack = false))
            .padding(top = insets.calculateTopPadding(), bottom = insets.calculateBottomPadding()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 16.dp, top = 6.dp, bottom = 10.dp),
        ) {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                    tint = SearchColors.Primary,
                )
            }
            Box(modifier = Modifier.weight(1f)) {
                SheetSearchField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = stringResource(R.string.pick_manual_search),
                )
            }
        }

        Text(
            text = stringResource(R.string.pick_page_hint, title),
            color = SearchColors.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        val list = results
        val message = when {
            problem != null -> problem
            query.isBlank() -> stringResource(R.string.pick_looking)
            list == null -> stringResource(R.string.pick_looking)
            list.isEmpty() -> stringResource(R.string.pick_none)
            else -> null
        }
        if (message != null) {
            Text(
                text = message,
                color = SearchColors.Secondary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(list.orEmpty(), key = { it.id }) { item ->
                val same = item.id == songId
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = !same, role = Role.Button) {
                            scope.launch {
                                pickRecording(context, database, playerConnection, songId, item)
                                navController.navigateUp()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    LibraryGridArtwork(url = item.thumbnail, corner = 10.dp, modifier = Modifier.size(48.dp))
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
            item { Spacer(Modifier.heightIn(min = 24.dp)) }
        }
    }
}
