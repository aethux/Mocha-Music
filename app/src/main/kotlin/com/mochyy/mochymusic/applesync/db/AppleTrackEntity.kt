// AppleTrackEntity.kt

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * How one Mochy song maps to Apple: the catalog id for identity, the library id for playlist writes.
 *
 * The library id belongs to the account and changes when a song is removed and added again, so it is
 * a cache and not an identity. When a write is refused for an unknown id, re-read the playlist and
 * refresh it rather than trusting this column.
 */
@Entity(tableName = "track")
data class AppleTrackEntity(
    @PrimaryKey val songId: String,
    val catalogId: String? = null,
    val libraryId: String? = null,
    val isrc: String? = null,
    val state: String = STATE_AUTO,
    val updatedAt: Long = 0L,
) {
    companion object {
        const val STATE_AUTO = "AUTO"
        /** a person picked the recording; nothing automatic may overwrite it */
        const val STATE_MANUAL = "MANUAL"
        const val STATE_PICKED = "PICKED"
        const val STATE_NONE = "NONE"
        const val STATE_YOUTUBE_ONLY = "YOUTUBE_ONLY"
    }
}
