// SongsOrdering.kt
//
// The Songs list's order and its sort control, kept out of the screen itself: the screen is
// upstream code, and this is a fork feature with a budget of its own.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.res.stringResource
import com.mocha.mochymusic.R
import com.mocha.mochymusic.constants.LibrarySongsSortDescendingKey
import com.mocha.mochymusic.constants.LibrarySongsSortTypeKey
import com.mocha.mochymusic.constants.SongSortType
import com.mocha.mochymusic.db.entities.Song
import com.mocha.mochymusic.extensions.toMediaItem
import com.mocha.mochymusic.playback.PlayerConnection
import com.mocha.mochymusic.playback.queues.ListQueue
import com.mocha.mochymusic.ui.component.detail.PlaylistSortControl
import com.mocha.mochymusic.ui.screens.library.LibraryPlayBar
import com.mocha.mochymusic.utils.rememberEnumPreference
import com.mocha.mochymusic.utils.rememberPreference

class SongsOrder(
    val songs: List<Song>,
    val byName: Boolean,
    private val content: @Composable () -> Unit,
) {
    /** The control itself, for the play bar's trailing slot. */
    @Composable
    fun control() = content()
}

/** The list in the chosen order, and the control that chooses it. */
@Composable
fun rememberSongsOrder(songs: List<Song>): SongsOrder {
    val (sortType, onSortTypeChange) = rememberEnumPreference(LibrarySongsSortTypeKey, SongSortType.NAME)
    val (sortDescending, onSortDescendingChange) = rememberPreference(LibrarySongsSortDescendingKey, false)

    val ordered = remember(songs, sortType, sortDescending) {
        val sorted = when (sortType) {
            SongSortType.NAME -> songs.sortedWith(compareBy { it.song.title.lowercase() })
            SongSortType.ARTIST -> songs.sortedWith(
                compareBy({ it.artists.joinToString { a -> a.name }.lowercase() }, { it.song.title.lowercase() }),
            )
            SongSortType.CREATE_DATE -> songs.sortedWith(
                compareBy({ it.song.inLibrary ?: it.song.date }, { it.song.title.lowercase() }),
            )
            SongSortType.PLAY_TIME -> songs.sortedBy { it.song.totalPlayTime }
        }
        if (sortDescending) sorted.reversed() else sorted
    }

    return remember(ordered, sortType, sortDescending) {
        SongsOrder(
            songs = ordered,
            byName = sortType == SongSortType.NAME && !sortDescending,
            content = {
                PlaylistSortControl(
                    current = sortType,
                    descending = sortDescending,
                    options = SongSortType.entries,
                    label = { type ->
                        stringResource(
                            when (type) {
                                SongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                SongSortType.NAME -> R.string.sort_by_name
                                SongSortType.ARTIST -> R.string.sort_by_artist
                                SongSortType.PLAY_TIME -> R.string.sort_by_play_time
                            },
                        )
                    },
                    onSelect = { type ->
                        // a date or a play count is read newest-first; a name and an artist A to Z
                        onSortTypeChange(type)
                        onSortDescendingChange(type == SongSortType.CREATE_DATE || type == SongSortType.PLAY_TIME)
                    },
                    onToggleDirection = { onSortDescendingChange(!sortDescending) },
                )
            },
        )
    }
}

/**
 * The play-and-shuffle bar above the list: play the rows in the order they are in, or the same rows
 * shuffled. The sort control rides at the end of it, as it does on the liked playlist.
 */
@Composable
fun SongsOrder.playBar(connection: PlayerConnection, title: String) {
    LibraryPlayBar(
        onPlay = {
            if (songs.isNotEmpty()) {
                connection.playQueue(ListQueue(title = title, items = songs.map { it.toMediaItem() }, startIndex = 0))
            }
        },
        onMore = {
            val shuffled = songs.shuffled()
            if (shuffled.isNotEmpty()) {
                connection.playQueue(ListQueue(title = title, items = shuffled.map { it.toMediaItem() }, startIndex = 0))
            }
        },
        trailing = { control() },
    )
}
