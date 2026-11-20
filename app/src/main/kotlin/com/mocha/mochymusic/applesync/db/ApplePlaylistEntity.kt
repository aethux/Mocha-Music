// ApplePlaylistEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A playlist as Apple has it, so the library can show a count without asking Apple every time and a
 * sync can tell a playlist that exists from one that has to be made.
 */
@Entity(tableName = "library_playlist")
data class ApplePlaylistEntity(
    @PrimaryKey val remoteId: String,
    val name: String,
    val trackCount: Int,
    val canEdit: Boolean,
    val pulledAt: Long,
)
