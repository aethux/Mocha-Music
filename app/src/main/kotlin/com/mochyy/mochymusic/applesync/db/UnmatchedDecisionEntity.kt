// UnmatchedDecisionEntity.kt
//
// A song Apple does not have, and the person's answer to being asked about it.
//
// Only one answer exists: keep the YouTube version. A song Apple *does* have is merged into its
// Apple copy and never needs a row here, and nothing else may be stored, so a row is exactly
// "stop asking me about this one".

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "unmatched_decision")
data class UnmatchedDecisionEntity(
    @PrimaryKey val songId: String,
    /** [KEEP_YOUTUBE]: the person chose the YouTube version over matching it on Apple. */
    val decision: String,
    val decidedAt: Long,
) {
    companion object {
        const val KEEP_YOUTUBE = "KEEP_YOUTUBE"
    }
}
