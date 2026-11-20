// LibraryRows.kt
//
// The two steps between the rows a library list was given and the rows it draws: the search, and
// the index sections. Both had to grow an option for lists that are not in index order (Songs,
// sorted by date or artist), and both are list mechanics rather than Apple's, so they live here
// and the screen calls them.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mocha.mochymusic.applesync.AppleSyncStatus
import com.mocha.mochymusic.ui.screens.library.IndexSection
import com.mocha.mochymusic.ui.screens.library.IndexedRow

/** The list with the search applied: the title, or the line under it. */
internal fun <T> libraryFilter(
    items: List<T>,
    query: String,
    nameOf: (T) -> String,
    subtitleOf: (T) -> String?,
): List<T> {
    val q = query.trim()
    if (q.isBlank()) return items
    return items.filter {
        nameOf(it).contains(q, ignoreCase = true) || subtitleOf(it)?.contains(q, ignoreCase = true) == true
    }
}

/**
 * The rows a list draws: headers and entries when it is sectioned, the caller's own order when it
 * is not. An empty [sections] list means "not sectioned", never "nothing to show".
 */
internal fun <T> indexRows(
    visible: List<T>,
    sections: List<Pair<IndexSection, List<T>>>,
): List<IndexedRow<T>> = buildList<IndexedRow<T>> {
    if (sections.isEmpty()) {
        visible.forEach { add(IndexedRow.Entry(it)) }
    } else {
        sections.forEach { (section, entries) ->
            add(IndexedRow.Header(section))
            entries.forEach { add(IndexedRow.Entry(it)) }
        }
    }
}

/**
 * The mark beside a song that has no Apple Music copy yet.
 *
 * A dot in the error colour read as a notification nobody could name, so it is the app's own
 * "library music" glyph instead, in the row's own content colour: the same vocabulary as the liked
 * heart and the download arrow next to it, and it carries the sentence a screen reader needs.
 */
@Composable
fun AppleMissingMark(songId: String?, modifier: Modifier = Modifier) {
    if (songId == null || !AppleSyncStatus.shouldMark(songId)) return
    Icon(
        painter = painterResource(com.mocha.mochymusic.R.drawable.library_music_outlined),
        contentDescription = stringResource(com.mocha.mochymusic.R.string.apple_mark_missing),
        tint = androidx.compose.material3.LocalContentColor.current.copy(alpha = 0.7f),
        modifier = modifier.size(13.dp),
    )
}
