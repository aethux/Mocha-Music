// AppleMatcher.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.importer.TrackMatcher
import kotlin.math.abs
import kotlin.math.max

/**
 * Finds the Apple catalogue song that corresponds to a track Mochy already has.
 *
 * The scoring is [TrackMatcher]'s, pointed at a different catalogue: title dominates, artist and
 * length decide the rest, a same-album result gets a small lift, and a version word the source did
 * not ask for (remix, live, karaoke, sped up) costs more than any of those can recover. That maths
 * was written for YouTube and it works here because the problem is the same one: deciding whether
 * two listings are the same recording.
 *
 * A miss is a normal answer. [best] returns null rather than a bad guess, because the caller can ask
 * the user to pick or keep the YouTube version, and a wrong match is far more expensive than none.
 */
class AppleMatcher(private val client: AppleClient) {

    data class Match(val song: CatalogSong, val score: Double)

    suspend fun best(
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
    ): Match? {
        if (title.isBlank()) return null
        val query = listOf(title, artist).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = client.searchCatalog(query)
        return rank(candidates, title, artist, album, durationMs).firstOrNull()
    }

    /** Score every candidate and keep the ones worth acting on, best first. */
    fun rank(
        candidates: List<CatalogSong>,
        title: String,
        artist: String,
        album: String? = null,
        durationMs: Long? = null,
    ): List<Match> = candidates
        .map { Match(it, score(title, artist, album, durationMs, it)) }
        .filter { it.score >= Accept }
        .sortedByDescending { it.score }

    /** Exposed for tests: the number that decides a match, with no network in the way. */
    fun score(
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        candidate: CatalogSong,
    ): Double {
        val targetTitle = TrackMatcher.normalizeTitle(title)
        val candidateTitle = TrackMatcher.normalizeTitle(candidate.title)
        val titleScore = TrackMatcher.similarity(targetTitle, candidateTitle)
        if (titleScore < MinTitle) return 0.0

        val targetArtists = TrackMatcher.splitArtists(artist).map(TrackMatcher::normalizeText).filter { it.isNotEmpty() }
        val candidateArtists = TrackMatcher.splitArtists(candidate.artist).map(TrackMatcher::normalizeText).filter { it.isNotEmpty() }
        val artistScore = if (targetArtists.isEmpty()) {
            0.5
        } else {
            targetArtists.maxOf { target -> candidateArtists.maxOfOrNull { TrackMatcher.similarity(target, it) } ?: 0.0 }
        }

        val durationScore = durationScore(durationMs, candidate.durationMs)

        // whole words on the raw titles: cleaning drops version markers, and "live" hides inside "alive"
        val rawTarget = " ${TrackMatcher.normalizeText(title)} "
        val rawCandidate = " ${TrackMatcher.normalizeText(candidate.title)} "
        val versionPenalty = VersionWords.count { word ->
            rawCandidate.contains(" $word ") != rawTarget.contains(" $word ")
        } * VersionPenalty

        return titleScore * 0.5 + artistScore * 0.25 + durationScore * 0.25 +
            albumScore(album, candidate.album) - versionPenalty
    }

    private fun albumScore(sourceAlbum: String?, candidateAlbum: String?): Double {
        if (candidateAlbum.isNullOrBlank()) return 0.0
        val candidate = " ${TrackMatcher.normalizeText(candidateAlbum)} "
        val source = " ${TrackMatcher.normalizeText(sourceAlbum.orEmpty())} "
        val liveAlbum = LiveAlbumWords.any { candidate.contains(" $it ") && !source.contains(" $it ") }
        if (liveAlbum) return -VersionPenalty
        if (sourceAlbum.isNullOrBlank()) return 0.0
        return if (TrackMatcher.similarity(source.trim(), candidate.trim()) >= 0.8) 0.05 else 0.0
    }

    /** Apple gives milliseconds; the bands are the ones the YouTube matcher uses. */
    private fun durationScore(sourceMs: Long?, candidateMs: Long?): Double {
        if (sourceMs == null || sourceMs <= 0 || candidateMs == null || candidateMs <= 0) return 0.5
        val diff = abs(sourceMs / 1000.0 - candidateMs / 1000.0)
        return when {
            diff <= TrackMatcher.CLOSE_SECONDS -> 1.0
            diff <= 8 -> 0.6
            diff <= 20 -> 0.1
            diff <= 60 -> -0.3
            else -> -1.0
        }
    }

    companion object {
        /** the same bar the YouTube matcher accepts a song on */
        const val Accept = 0.62
        private const val MinTitle = 0.45
        private const val VersionPenalty = 0.3

        private val VersionWords = listOf(
            "remix", "live", "instrumental", "inst", "acoustic", "karaoke", "cover", "sped up", "slowed",
            "nightcore", "reverb", "8d", "stage", "dance practice", "practice", "performance", "fancam",
            "concert", "rehearsal", "reaction", "1 hour", "hour", "loop", "extended",
            "japanese", "korean", "english", "chinese",
        )
        private val LiveAlbumWords = listOf("live", "tour", "concert", "unplugged", "session", "sessions")
    }
}
