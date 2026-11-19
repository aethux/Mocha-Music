// CoverOverrides.kt
//
// The covers a person picked, for a playlist, an artist or a song. Same table for all three so the
// sheet, the crop and the import protection are written once. Set one and every import leaves that
// row's artwork alone; clear it and Apple's own cover comes back on the next import.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.db.CoverOverrideEntity
import com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint
import dagger.hilt.android.EntryPointAccessors

object CoverOverrides {

    enum class Kind(val tag: String) { PLAYLIST("playlist"), ARTIST("artist"), SONG("song"), ALBUM("album") }

    fun key(kind: Kind, id: String): String = "${kind.tag}:$id"

    private fun dao(context: Context): AppleSyncDao =
        EntryPointAccessors
            .fromApplication(context.applicationContext, AppleSongWritesEntryPoint::class.java)
            .dao()

    /** The chosen cover, or null when Apple's own should be used. */
    suspend fun get(context: Context, kind: Kind, id: String): String? =
        runCatching { dao(context).coverOverride(key(kind, id))?.value }.getOrNull()

    /** The same read for a caller that already has the sidecar dao and no context. */
    suspend fun get(dao: AppleSyncDao, kind: Kind, id: String): String? =
        runCatching { dao.coverOverride(key(kind, id))?.value }.getOrNull()

    suspend fun set(context: Context, kind: Kind, id: String, value: String) {
        runCatching {
            dao(context).saveCoverOverride(
                CoverOverrideEntity(
                    key = key(kind, id),
                    value = value,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Back to Apple's cover: the next import fills it in again. */
    suspend fun clear(context: Context, kind: Kind, id: String) {
        runCatching { dao(context).clearCoverOverride(key(kind, id)) }
    }

    /**
     * The artwork a row had before a chosen cover replaced it, kept under its own key so "Use
     * Apple's cover" can put it back. Without this a revert would blank a row whose artwork nothing
     * else ever rewrites - a YouTube song, or a playlist Apple knows nothing about.
     */
    private fun backupKey(kind: Kind, id: String): String = "backup:${key(kind, id)}"

    suspend fun previous(context: Context, kind: Kind, id: String): String? =
        runCatching { dao(context).coverOverride(backupKey(kind, id))?.value }.getOrNull()

    /** Remembers [value] once, so a second choice does not overwrite the original with the first. */
    suspend fun rememberPrevious(context: Context, kind: Kind, id: String, value: String) {
        runCatching {
            val dao = dao(context)
            if (dao.coverOverride(backupKey(kind, id)) == null) {
                dao.saveCoverOverride(
                    CoverOverrideEntity(
                        key = backupKey(kind, id),
                        value = value,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            }
        }
    }

    suspend fun forgetPrevious(context: Context, kind: Kind, id: String) {
        runCatching { dao(context).clearCoverOverride(backupKey(kind, id)) }
    }

    /** Every chosen cover, for the places that draw a list and want them in one read. */
    suspend fun all(context: Context): Map<String, String> =
        runCatching { dao(context).coverOverrides().associate { it.key to it.value } }
            .getOrDefault(emptyMap())

    suspend fun has(context: Context, kind: Kind, id: String): Boolean =
        runCatching { dao(context).coverOverride(key(kind, id)) != null }.getOrDefault(false)
}
