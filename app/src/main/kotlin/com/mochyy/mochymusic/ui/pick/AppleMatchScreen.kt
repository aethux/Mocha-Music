// AppleMatchScreen.kt
//
// The other half of "pick a recording": this one searches Apple's catalogue, so a song can be tied
// to the Apple recording it is - which is what the heart and the library button then act on, and
// what a later import reconnects to.

package com.mochyy.mochymusic.ui.pick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.mochyy.mochymusic.LocalDatabase
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.AppleAdopt
import com.mochyy.mochymusic.applesync.AppleSongWrites
import com.mochyy.mochymusic.applesync.CatalogSong
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import com.mochyy.mochymusic.ui.screens.library.LibraryGridArtwork
import com.mochyy.mochymusic.ui.screens.search.SearchColors
import com.mochyy.mochymusic.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

const val AppleMatchRoute = "match_apple/{songId}"

fun appleMatchRoute(songId: String): String = "match_apple/$songId"

val AppleMatchArguments = listOf(navArgument("songId") { type = NavType.StringType })

/**
 * Apple's catalogue for a term, as the page its row opens. Picking one ties this song to that
 * recording: the rematch row is written, so the heart and the library button land on this Apple
 * song from then on and a re-import comes back to it.
 */
@Composable
fun AppleMatchScreen(songId: String, navController: NavController) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()
    val song by remember(songId) { database.song(songId) }.collectAsState(initial = null)

    val title = song?.song?.title.orEmpty()
    val artist = song?.artists?.joinToString(", ") { it.name }.orEmpty()

    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<CatalogSong>?>(null) }
    var owned by remember { mutableStateOf<Set<String>>(emptySet()) }

    LaunchedEffect(title, artist) {
        if (query.isBlank() && title.isNotBlank()) {
            query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        }
    }

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.isBlank()) return@LaunchedEffect
        results = null
        delay(350)
        // an album, its single and its deluxe all answer with the same recording
        val found = AppleAdopt.search(context, term).distinctBy { song ->
            song.isrc?.takeIf { it.isNotBlank() }
                ?: "${'$'}{song.title.lowercase()}/${'$'}{song.artist.lowercase()}"
        }
        results = found
        owned = AppleAdopt.inLibrary(context, found.map { it.id })
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_source_apple),
                    color = SearchColors.Primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = title,
                    color = SearchColors.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        SheetSearchField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.pick_manual_search),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Text(
            text = stringResource(R.string.match_apple_hint, title),
            color = SearchColors.Secondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )

        val list = results
        if (list == null || list.isEmpty()) {
            Text(
                text = if (list == null) stringResource(R.string.pick_looking) else stringResource(R.string.search_apple_none),
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
            items(list, key = { it.id }) { found ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(role = Role.Button) {
                            scope.launch {
                                AppleSongWrites.keepAppleIdentity(
                                    context = context,
                                    songId = songId,
                                    catalogId = found.id,
                                    title = found.title,
                                    artist = found.artist,
                                    album = found.album,
                                )
                                TopToaster.show(
                                    text = context.getString(R.string.pick_matched, found.title),
                                    thumbnail = found.artworkUrl?.replace("{w}", "300")?.replace("{h}", "300"),
                                    iconRes = R.drawable.sync,
                                )
                                navController.navigateUp()
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                ) {
                    LibraryGridArtwork(
                        url = found.artworkUrl?.replace("{w}", "300")?.replace("{h}", "300"),
                        corner = 10.dp,
                        modifier = Modifier.size(48.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = found.title,
                            color = SearchColors.Primary,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = listOfNotNull(found.artist.ifBlank { null }, found.album).joinToString(" · "),
                            color = SearchColors.Secondary,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (found.id in owned) {
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            painter = painterResource(R.drawable.library_add_check),
                            contentDescription = stringResource(R.string.search_apple_in_library),
                            tint = SearchColors.Secondary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = found.durationMs?.let { makeTimeString(it) } ?: "",
                        color = SearchColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
