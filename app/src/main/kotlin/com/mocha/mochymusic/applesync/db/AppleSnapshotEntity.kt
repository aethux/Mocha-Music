// AppleSnapshotEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity

/**
 * The playlist as it stood at the end of the last successful sync: the base for the three-way merge.
 *
 * Rows rather than a blob, so a diff is a query and a half-finished sync can rebuild the snapshot
 * from what actually exists.
 */
@Entity(tableName = "snapshot", primaryKeys = ["playlistId", "position"])
data class AppleSnapshotEntity(
    val playlistId: String,
    val position: Int,
    val songId: String,
    val libraryId: String? = null,
)
