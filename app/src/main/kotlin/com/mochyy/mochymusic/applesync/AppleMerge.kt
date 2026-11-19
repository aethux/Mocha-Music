// AppleMerge.kt
//
// Apple is the library of record, so when a song turns out to exist on both sides the Apple row is
// the one that stays and the YouTube row gives up everything it was carrying.
//
// What "gives up" means is the same list ImportedVersions.replace moves when a person picks another
// provider's version: membership of every playlist, being liked, being in the library, and the time
// spent playing it. The old row is not deleted - a queue, a history row or a download still holds
// that id - it is emptied instead, and a rematch row makes the id play the Apple recording, so
// nothing that already points at it breaks.

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleRematchEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.flow.first

object AppleMerge {

    /**
     * Folds [fromId] (the YouTube row) into [toId] (the Apple one) and pins the old id to the Apple
     * recording. Returns false when either row is gone, so a concurrent refresh cannot make this
     * write over a row that no longer exists.
     */
    suspend fun absorb(
        database: MusicDatabase,
        dao: AppleSyncDao,
        fromId: String,
        toId: String,
        catalogId: String,
    ): Boolean {
        if (fromId == toId) return false
        // read before the transaction: a room flow waited on inside one never answers
        val playlistMaps = database.playlistIdsContainingSongs(listOf(fromId))
            .map { id -> database.playlistSongs(id).first().map { it.map } }
        var moved = false

        database.withTransaction {
            val old = database.getSongById(fromId)?.song ?: return@withTransaction
            val keep = database.getSongById(toId)?.song ?: return@withTransaction
            database.update(
                keep.copy(
                    inLibrary = keep.inLibrary ?: old.inLibrary,
                    liked = keep.liked || old.liked,
                    likedDate = keep.likedDate ?: old.likedDate,
                    totalPlayTime = keep.totalPlayTime + old.totalPlayTime,
                ),
            )
            // the YouTube row leaves the library and the liked songs it was only in for this track
            database.update(old.copy(inLibrary = null, liked = false, likedDate = null))

            playlistMaps.forEach { maps ->
                val alreadyThere = maps.any { it.songId == toId }
                maps.filter { it.songId == fromId }.forEach { map ->
                    if (alreadyThere) database.delete(map) else database.update(map.copy(songId = toId))
                }
            }
            // the rows keyed to the song id are the app's own tables, and they are what makes a
            // merge lose lyrics, a speed-dial entry and the play history if they are not moved
            val db = database.openHelper.writableDatabase
            for (table in listOf("lyrics", "event", "speed_dial_item", "song_edit")) {
                runCatching {
                    db.execSQL("UPDATE OR IGNORE $table SET songId = ? WHERE songId = ?", arrayOf(toId, fromId))
                    db.execSQL("DELETE FROM $table WHERE songId = ?", arrayOf(fromId))
                }
            }
            moved = true
        }
        if (!moved) return false

        // anything holding the old id - a queue entry, a play history row - now plays Apple's copy
        dao.saveRematch(
            AppleRematchEntity(
                songId = fromId,
                source = AppleRematchEntity.SOURCE_APPLE,
                remoteId = catalogId,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        dao.clearDecision(fromId)
        return true
    }
}
