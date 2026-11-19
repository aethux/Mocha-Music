// LibraryRows.kt
//
// The two steps between the rows a library list was given and the rows it draws: the search, and
// the index sections. Both had to grow an option for lists that are not in index order (Songs,
// sorted by date or artist), and both are list mechanics rather than Apple's, so they live here
// and the screen calls them.

package com.mochyy.mochymusic.applesync.ui

import com.mochyy.mochymusic.ui.screens.library.IndexSection
import com.mochyy.mochymusic.ui.screens.library.IndexedRow

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
