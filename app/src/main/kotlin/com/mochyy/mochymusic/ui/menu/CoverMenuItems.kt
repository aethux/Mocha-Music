// CoverMenuItems.kt
//
// "Cover" as a row in a song's, an artist's or an album's menu. The sheet is the one a playlist
// already uses, so choosing an image, cropping it, pasting a link and handing it back to Apple are
// written once; these rows only say which row of the database the answer belongs to.

package com.mochyy.mochymusic.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.CoverOverrides
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Album
import com.mochyy.mochymusic.db.entities.Artist
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.component.PopupSheetState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Writes that outlive the sheet that asked for them.
 *
 * A cover is applied as the sheet closes: a coroutine on the *menu's* scope is cancelled with that
 * composition, which left the override saved and the row untouched - the cover only appeared after
 * the next import.
 */
private val coverWrites = CoroutineScope(SupervisorJob() + Dispatchers.IO)

internal fun coverRow(
    kind: CoverOverrides.Kind,
    id: String,
    menuState: PopupSheetState,
    onApplied: (String?) -> Unit,
): Material3MenuItemData = Material3MenuItemData(
    title = { Text(text = stringResource(R.string.cover_menu_title)) },
    description = { Text(text = stringResource(R.string.cover_menu_desc)) },
    icon = { Icon(painter = painterResource(R.drawable.image), contentDescription = null) },
    onClick = {
        menuState.show {
            CoverSheet(
                kind = kind,
                id = id,
                // the sheet is a page of this menu, so its own back is a step back, not a dismissal
                onDismiss = menuState::dismiss,
                onApplied = onApplied,
            )
        }
    },
)

/**
 * What the sheet's answer does to the row: a new cover is written and the artwork it replaced is
 * remembered, so handing the cover back to Apple puts *that* back rather than leaving a blank one.
 */
@Composable
internal fun coverApplier(
    kind: CoverOverrides.Kind,
    id: String,
    current: String?,
    write: (String?) -> Unit,
): (String?) -> Unit {
    val context = LocalContext.current
    return { chosen ->
        coverWrites.launch {
            if (chosen != null) {
                current?.let { CoverOverrides.rememberPrevious(context, kind, id, it) }
                write(chosen)
            } else {
                val previous = CoverOverrides.previous(context, kind, id)
                write(previous)
                CoverOverrides.forgetPrevious(context, kind, id)
            }
        }
    }
}

@Composable
fun songCoverMenuItem(
    song: Song,
    menuState: PopupSheetState,
    database: MusicDatabase,
): Material3MenuItemData = coverRow(
    kind = CoverOverrides.Kind.SONG,
    id = song.id,
    menuState = menuState,
    // the override is what an import reads; the row is what the lists draw now
    onApplied = coverApplier(CoverOverrides.Kind.SONG, song.id, song.song.thumbnailUrl) { chosen ->
        database.query { update(song.song.copy(thumbnailUrl = chosen)) }
    },
)

@Composable
fun artistCoverMenuItem(
    artist: Artist,
    menuState: PopupSheetState,
    database: MusicDatabase,
): Material3MenuItemData = coverRow(
    kind = CoverOverrides.Kind.ARTIST,
    id = artist.id,
    menuState = menuState,
    onApplied = coverApplier(CoverOverrides.Kind.ARTIST, artist.id, artist.artist.thumbnailUrl) { chosen ->
        database.query { update(artist.artist.copy(thumbnailUrl = chosen)) }
    },
)

@Composable
fun albumCoverMenuItem(
    album: Album,
    menuState: PopupSheetState,
    database: MusicDatabase,
): Material3MenuItemData = coverRow(
    kind = CoverOverrides.Kind.ALBUM,
    id = album.id,
    menuState = menuState,
    onApplied = coverApplier(CoverOverrides.Kind.ALBUM, album.id, album.album.thumbnailUrl) { chosen ->
        database.query { update(album.album.copy(thumbnailUrl = chosen)) }
    },
)
