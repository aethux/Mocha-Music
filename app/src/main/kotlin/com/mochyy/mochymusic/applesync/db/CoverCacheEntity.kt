// CoverCacheEntity.kt
//
// One cover decision per song, remembered. The point of the table is the second import: artwork
// does not change, and re-asking Apple for every song on every refresh is what made a refresh feel
// like a cold start. Clearing this table is the only thing that brings the fetching back.

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cover_cache")
data class CoverCacheEntity(
    @PrimaryKey val songId: String,
    /** Apple's animated artwork (an mp4 loop), when the album has one. */
    val animatedUrl: String? = null,
    /** What the lists draw. */
    val staticUrl: String? = null,
    /** which step of the chain answered: apple_motion, apple_art, youtube */
    val source: String,
    val fetchedAt: Long,
)
