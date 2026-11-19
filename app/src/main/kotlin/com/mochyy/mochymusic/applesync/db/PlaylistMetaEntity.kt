// PlaylistMetaEntity.kt

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Who owns an imported playlist, and whether more than one person can edit it.
 *
 * In the sidecar rather than `mochy.db` because `playlist` has no owner column upstream, and adding
 * one would mean a schema change to the app's own database for a field only Apple knows. Joined by
 * the local playlist id, which is `AM_PL_<remote id>`.
 */
@Entity(tableName = "playlist_meta")
data class PlaylistMetaEntity(
    @PrimaryKey val playlistId: String,
    /** Apple's `curatorName` when it sends one; null when the playlist is the account's own. */
    val ownerName: String? = null,
    /** True when the playlist can be edited by someone other than its owner. */
    val collaborative: Boolean = false,
    /**
     * Apple's `lastModifiedDate` for this playlist. A refresh compares it and skips reading the
     * tracks when it has not moved, which is what keeps a second refresh from re-reading 26
     * playlists' songs out of Apple.
     */
    val lastModified: String? = null,
    /** `canDelete`: false means the playlist is someone else's, in the account's library. */
    val canDelete: Boolean = false,
    /**
     * Whether the owner name has been looked up. The list endpoint does not carry it, so it costs
     * one request per playlist - once, ever, not once per refresh.
     */
    val ownerChecked: Boolean = false,
    /**
     * The playlist's catalogue counterpart, when it has one. A followed or editorial playlist's
     * library resource lists only the tracks that are in the library; the catalogue copy lists all
     * of them, which is what makes an imported playlist the length Apple shows.
     */
    val catalogId: String? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
