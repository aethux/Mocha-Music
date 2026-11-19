// AppleRematchStore.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleRematchEntity

/**
 * The five things the rematch table is asked for.
 *
 * Narrower than the sidecar's whole DAO on purpose: playback reads pins on a hot path, and a piece of
 * code that can only read and write this one table cannot quietly change the epoch or the replica.
 * The Room DAO is adapted to it in the DI module; tests supply their own.
 */
interface AppleRematchStore {
    suspend fun rematches(): List<AppleRematchEntity>
    suspend fun rematch(songId: String): AppleRematchEntity?
    suspend fun rematchedSongIds(source: String): List<String>
    suspend fun saveRematch(row: AppleRematchEntity)
    suspend fun clearRematch(songId: String)
}
