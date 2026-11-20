// AppleJournalEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * What changed in a playlist, and who changed it. LOCAL is the user in Mocha, REMOTE is Apple.
 *
 * Without an origin the sync cannot tell its own push from a fresh edit, and every poll fights the
 * last write. [applied] says whether the remote call for this entry already happened.
 */
@Entity(tableName = "journal", indices = [Index("playlistId")])
data class AppleJournalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: String? = null,
    val songId: String? = null,
    val op: String,
    val origin: String,
    val at: Long,
    val applied: Boolean = false,
)
