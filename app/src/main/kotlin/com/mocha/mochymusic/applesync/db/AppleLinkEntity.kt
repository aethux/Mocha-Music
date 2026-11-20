// AppleLinkEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A local playlist and the remote playlist it follows, one row per link.
 *
 * [direction] is PUSH, PULL or BOTH. A playlist with no row here is never written to, whatever else
 * the guard decides; unlinking is how a test playlist becomes inert again.
 */
@Entity(tableName = "link")
data class AppleLinkEntity(
    @PrimaryKey val playlistId: String,
    val kind: String = KIND_PLAYLIST,
    val remoteId: String,
    val remoteName: String? = null,
    val direction: String = DIRECTION_BOTH,
    val enabled: Boolean = true,
    val lastSyncAt: Long? = null,
    val lastError: String? = null,
    /**
     * The playlist's local content (its song ids, in order) as it stood when it was last imported or
     * last sent. The watcher compares against this across restarts: without it, every launch looked
     * like a change and rewrote all of Apple's playlists with a replica that might be older.
     */
    val lastSignature: String? = null,
) {
    companion object {
        const val KIND_PLAYLIST = "PLAYLIST"
        const val KIND_LIBRARY = "LIBRARY"
        const val DIRECTION_PUSH = "PUSH"
        const val DIRECTION_PULL = "PULL"
        const val DIRECTION_BOTH = "BOTH"
    }
}
