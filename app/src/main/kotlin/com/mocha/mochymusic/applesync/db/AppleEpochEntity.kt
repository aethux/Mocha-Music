// AppleEpochEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity

/**
 * Everything that was in the account before this app wrote to it.
 *
 * The guard refuses any write whose target appears here, so this table is the promise that the
 * user's existing library is never touched. Filled once on the first sync, and cleared only by an
 * explicit "forget this account" that the UI has to confirm.
 */
@Entity(tableName = "epoch", primaryKeys = ["kind", "remoteId"])
data class AppleEpochEntity(
    val kind: String,
    val remoteId: String,
    val firstSeenAt: Long,
)
