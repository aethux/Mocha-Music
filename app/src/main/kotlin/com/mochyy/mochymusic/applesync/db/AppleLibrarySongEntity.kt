// AppleLibrarySongEntity.kt

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One song as Apple has it, kept in the sidecar database so the Library tab can read Apple first
 * without touching the app's own song table.
 *
 * The catalogue id is the key because that is what every write is addressed by — a rating, a library
 * add and a playlist entry all take it. The library id is carried alongside for the calls that want
 * it and for recognising the row if Apple ever changes one of them.
 */
@Entity(
    tableName = "library_song",
    indices = [Index("libraryId"), Index("title"), Index("artist")],
)
data class AppleLibrarySongEntity(
    @PrimaryKey val catalogId: String,
    val libraryId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val isrc: String?,
    val addedAt: Long?,
    val pulledAt: Long,
    /** Apple's favourite: a rating of 1. A dislike is -1 and is not a favourite. */
    val rated: Boolean = false,
)
