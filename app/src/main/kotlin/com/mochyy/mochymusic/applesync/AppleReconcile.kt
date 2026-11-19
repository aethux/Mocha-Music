// AppleReconcile.kt
//
// Apple is the library of record, so a song that exists on both sides is one song: the Apple row
// keeps it and the YouTube row is folded into it. Only what Apple does not have stays on YouTube,
// and only once the person says so.
//
// The pass is deliberately small and capped. Matching costs one catalogue search per song, so it
// walks at most [LIMIT] songs each time it runs and the rest wait for the next run; a library with
// four hundred YouTube songs is a few passes, not one four-hundred-request stall. Every answer is
// kept in the sidecar, so a song that has been asked about is never asked again.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.db.AppleStateEntity
import com.mochyy.mochymusic.applesync.db.UnmatchedDecisionEntity
import com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.Song
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.flow.first

object AppleReconcile {

    /** One song Apple has no copy of, as the screen lists it. */
    data class Unmatched(
        val songId: String,
        val title: String,
        val artists: String,
        val thumbnailUrl: String?,
        /** The catalogue id of the closest thing Apple has, when there is one to offer. */
        val suggestionId: String? = null,
    )

    data class Outcome(
        /** Ids folded into their Apple copy this run. */
        val merged: List<String>,
        /** What is left: songs Apple does not have, or has only as a near miss. */
        val unmatched: List<Unmatched>,
    ) {
        val nothingToAsk: Boolean get() = unmatched.isEmpty()
    }

    private const val LIMIT = 40

    /**
     * Whether a row is still part of the question. One rule, used by the pass and by the library's
     * card: Apple's own rows and local files are not in question, and neither is a song someone has
     * already answered - matched to an Apple recording, or kept on YouTube.
     */
    fun stillInQuestion(
        songId: String,
        decided: Collection<String>,
        pinned: Collection<String>,
    ): Boolean = !songId.startsWith("AM_") &&
        !songId.startsWith("LOCAL_") &&
        songId !in decided &&
        songId !in pinned

    /** The songs that could still be unmatched: not Apple's, not local, not already answered. */
    suspend fun candidates(database: MusicDatabase, dao: AppleSyncDao): List<Song> {
        val answered = runCatching { dao.decidedSongIds() }.getOrDefault(emptyList()).toSet()
        val pinned = runCatching { dao.rematches().map { it.songId } }.getOrDefault(emptyList()).toSet()
        val songs = runCatching { database.songsByNameAsc().first() }.getOrDefault(emptyList())
        return songs.filter { song -> stillInQuestion(song.id, answered, pinned) }
    }

    /** How many songs are waiting for an answer. Reads the database only: no network. */
    suspend fun pending(database: MusicDatabase, dao: AppleSyncDao): Int =
        runCatching { candidates(database, dao).size }.getOrDefault(0)

    /**
     * Matches, merges, and reports what is left. Runs with the Apple client built from the captured
     * token pair; without one it answers with the candidates untouched, so an offline launch asks
     * nobody anything.
     */
    suspend fun run(context: Context, limit: Int = LIMIT): Outcome {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppleSongWritesEntryPoint::class.java,
        )
        val database = entry.database()
        val dao = entry.dao()
        val library = candidates(database, dao)
        if (library.isEmpty()) {
            rememberCount(dao, 0)
            return Outcome(emptyList(), emptyList())
        }

        val pair = runCatching { entry.tokens().read() }.getOrNull()
            ?: return Outcome(emptyList(), library.take(limit).map(::asUnmatched))
        val matcher = AppleMatcher(AppleClient(pair))

        val known = runCatching { database.appleSongIds() }.getOrDefault(emptyList()).toSet()
        val merged = mutableListOf<String>()
        val unmatched = mutableListOf<Unmatched>()

        for (song in library.take(limit)) {
            val artists = song.artists.joinToString(", ") { it.name }
            val match = runCatching {
                matcher.best(
                    title = song.song.title,
                    artist = artists,
                    durationMs = song.song.duration * 1000L,
                )
            }.getOrNull()

            val target = match?.song?.id?.let { "AM_$it" }
            if (match != null && target != null && target in known &&
                AppleMerge.absorb(database, dao, song.id, target, match.song.id)
            ) {
                merged += song.id
            } else {
                // Apple has it only in the catalogue, or not at all: a person decides, and a near
                // miss is offered as the search the picker should open with
                unmatched += asUnmatched(song, match?.song?.id)
            }
        }

        // the remaining candidates are not this run's answer, but they are still waiting
        rememberCount(dao, unmatched.size + (library.size - limit).coerceAtLeast(0))
        return Outcome(merged, unmatched)
    }

    /** The person chose the YouTube version: stop asking about this song. */
    suspend fun keep(dao: AppleSyncDao, songId: String) {
        dao.saveDecision(
            UnmatchedDecisionEntity(
                songId = songId,
                decision = UnmatchedDecisionEntity.KEEP_YOUTUBE,
                decidedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Undo a "keep on YouTube", so the song is asked about again. */
    suspend fun askAgain(dao: AppleSyncDao, songId: String) = dao.clearDecision(songId)

    private fun asUnmatched(song: Song, suggestionId: String? = null) = Unmatched(
        songId = song.id,
        title = song.song.title,
        artists = song.artists.joinToString(", ") { it.name },
        thumbnailUrl = song.song.thumbnailUrl,
        suggestionId = suggestionId,
    )

    private suspend fun rememberCount(dao: AppleSyncDao, count: Int) {
        runCatching {
            dao.saveState(
                AppleStateEntity(key = AppleSyncFlow.KEY_UNMATCHED, value = count.toString()),
            )
        }
    }
}
