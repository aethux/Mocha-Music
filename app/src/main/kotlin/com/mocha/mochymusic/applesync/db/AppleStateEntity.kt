// AppleStateEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One small fact about the last sync: when it ran, what it did.
 *
 * A key/value table rather than columns, because these are single values that change shape as the
 * sync learns to report more, and a new key costs nothing while a new column costs a migration.
 */
@Entity(tableName = "sync_state")
data class AppleStateEntity(
    @PrimaryKey val key: String,
    val value: String,
    val updatedAt: Long = System.currentTimeMillis(),
)
