// ApplePendingOpEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A write waiting to happen, in order. Survives process death and a reboot, which is the point: an
 * edit made offline is a promise to Apple, not a hope.
 */
@Entity(tableName = "pending_op", indices = [Index("playlistId")])
data class ApplePendingOpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val playlistId: String? = null,
    val kind: String,
    val payload: String,
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
)
