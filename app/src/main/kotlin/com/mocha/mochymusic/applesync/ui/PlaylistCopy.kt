// PlaylistCopy.kt
//
// The songs a picker was holding, put into the playlist it just made.
//
// Kept out of the picker's own file on purpose: that file is upstream's, and the fork's seam budget
// is twenty added lines. One call here instead of a loop there.

package com.mocha.mochymusic.applesync.ui

import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.db.entities.PlaylistSongMap

/** Inserts [songIds] into [playlistId] in order. A no-op when there is nothing to add. */
fun copySongsToPlaylist(database: MusicDatabase, playlistId: String, songIds: List<String>) {
    if (songIds.isEmpty()) return
    database.query {
        songIds.forEachIndexed { index, songId ->
            insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = index))
        }
    }
}
