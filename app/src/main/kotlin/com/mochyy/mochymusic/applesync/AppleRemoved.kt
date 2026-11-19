// AppleRemoved.kt
//
// Songs Apple no longer has. The refresh writes Apple's library into the replica, but nothing used
// to notice what had left: a song deleted on Apple stayed in this library forever, still counted as
// "on Apple Music" and still playing from a recording that is no longer there.
//
// The list is the difference between what this library holds with an Apple flag and what the last
// pull saw. It lives in the sidecar's sync_state as catalogue ids, because it is a fact about the
// last refresh rather than a table to migrate. Every answer (keep, remove here, put it back) takes
// the id off the list, so the card only ever shows what is still waiting.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleStateEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.flow.first

object AppleRemoved {

    const val KEY = "removed_from_apple"

    /** Catalogue ids Apple no longer has, newest check first. */
    suspend fun list(dao: AppleSyncDao): List<String> =
        runCatching { dao.state(KEY) }.getOrNull()
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.distinct()
            .orEmpty()

    /**
     * Recomputes the difference after a complete pull. Apple's library is read whole or the run
     * fails, so a song's absence here means it is genuinely gone rather than not read yet.
     */
    suspend fun record(dao: AppleSyncDao, database: MusicDatabase, remoteCatalogIds: Collection<String>) {
        val remote = remoteCatalogIds.toSet()
        // the ids the fork imported, minus the ones it knows are outside the library
        val everyApple = runCatching { database.appleSongIds() }.getOrDefault(emptyList())
        val outside = runCatching { database.appleSongsOutsideTheLibrary() }.getOrDefault(emptyList()).toSet()
        val local = everyApple.filterNot { it in outside }.map { it.removePrefix("AM_") }
        val gone = local.filter { it !in remote }.sorted()
        dao.saveState(AppleStateEntity(key = KEY, value = gone.joinToString(",")))
    }

    /** "Keep it here": it stays in Mochy, and the card stops asking. */
    suspend fun keep(dao: AppleSyncDao, catalogId: String) = save(dao, list(dao) - catalogId)

    /** "Remove it here too": the row leaves the library but is not deleted, so history survives. */
    suspend fun removeHere(dao: AppleSyncDao, database: MusicDatabase, catalogId: String) {
        runCatching {
            val row = database.getSongById("AM_$catalogId")?.song ?: return@runCatching
            if (row.inLibrary != null) database.query { update(row.toggleLibrary()) }
        }
        keep(dao, catalogId)
    }

    /** "Put it back": one POST, verified by the write path's own read-back. */
    suspend fun readd(context: Context, dao: AppleSyncDao, catalogId: String): Boolean {
        val ok = runCatching {
            AppleSongWrites.pushLibrary(context.applicationContext, "AM_$catalogId", true)
        }.getOrDefault(false)
        if (ok) keep(dao, catalogId)
        return ok
    }

    private suspend fun save(dao: AppleSyncDao, ids: List<String>) {
        dao.saveState(AppleStateEntity(key = KEY, value = ids.joinToString(",")))
    }

    /** The sidecar's state flow, for a card that should change the moment an answer is given. */
    suspend fun count(database: MusicDatabase, dao: AppleSyncDao): Int = list(dao).size
}
