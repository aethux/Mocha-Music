// AppleCovers.kt
//
// Where a song's cover comes from, in one order: Apple's animated artwork first, then Apple's still
// artwork, then whatever YouTube has for it. The answer is cached per song, so a second import or a
// refresh costs nothing - only clearing the cache (Settings → Apple Music → Clear cover cache) makes
// the next one fetch again.

package com.mocha.mochymusic.applesync

import android.content.Context
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import com.mocha.mochymusic.applesync.db.CoverCacheEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

object AppleCovers {

    const val SOURCE_MOTION = "apple_motion"
    const val SOURCE_ART = "apple_art"
    const val SOURCE_YOUTUBE = "youtube"

    /** Apple hands out a template with {w}x{h} in it; the lists draw at 300. */
    fun sized(template: String?, size: Int = 300): String? =
        template?.replace("{w}", size.toString())?.replace("{h}", size.toString())

    private fun dao(context: Context): AppleSyncDao =
        EntryPointAccessors
            .fromApplication(context.applicationContext, com.mocha.mochymusic.applesync.di.AppleSongWritesEntryPoint::class.java)
            .dao()

    /**
     * The still cover for a song, cached.
     *
     * [knownArtwork] is what the caller already has (the import reads it with the song), so a first
     * import writes its cache row without a request. Only a song with no cached answer and nothing
     * known is looked up: Apple's catalogue first - animated and still together - and YouTube's
     * thumbnail as the last resort.
     */
    suspend fun coverFor(
        context: Context,
        songId: String,
        catalogId: String?,
        knownArtwork: String?,
        title: String,
        artist: String,
        /** false during an import: a hundred songs should not mean a hundred catalogue reads */
        allowNetwork: Boolean = true,
    ): String? {
        val dao = dao(context)
        runCatching { dao.cover(songId) }.getOrNull()?.let { cached ->
            // a row written from the import's own artwork can still be upgraded to the animated one
            if (cached.animatedUrl != null || !allowNetwork || cached.source == SOURCE_YOUTUBE) {
                return cached.staticUrl ?: cached.animatedUrl
            }
        }

        // step one and two: Apple, animated then still
        if (catalogId != null && allowNetwork) {
            val found = runCatching { appleCover(context, catalogId) }.getOrNull()
            if (found != null && (found.animatedUrl != null || found.staticUrl != null)) {
                dao.saveCover(
                    CoverCacheEntity(
                        songId = songId,
                        animatedUrl = found.animatedUrl,
                        staticUrl = sized(found.staticUrl) ?: knownArtwork,
                        source = if (found.animatedUrl != null) SOURCE_MOTION else SOURCE_ART,
                        fetchedAt = System.currentTimeMillis(),
                    ),
                )
                return sized(found.staticUrl) ?: knownArtwork
            }
        }

        if (knownArtwork != null) {
            dao.saveCover(
                CoverCacheEntity(
                    songId = songId,
                    staticUrl = sized(knownArtwork),
                    source = SOURCE_ART,
                    fetchedAt = System.currentTimeMillis(),
                ),
            )
            return sized(knownArtwork)
        }

        // No search, ever: a thumbnail found by name is how a song ends up wearing another song's
        // cover. A song with nothing known keeps its own artwork, or nothing at all.
        return null
    }

    /**
     * Writes the cache row an import can answer for free, and reports whether it had to.
     *
     * A song the cache already knows is left alone: that is the whole point of the table.
     */
    suspend fun seed(dao: AppleSyncDao, songId: String, artworkUrl: String?): Boolean {
        if (runCatching { dao.cover(songId) }.getOrNull() != null) return false
        if (artworkUrl == null) return false
        dao.saveCover(
            CoverCacheEntity(
                songId = songId,
                staticUrl = sized(artworkUrl),
                source = SOURCE_ART,
                fetchedAt = System.currentTimeMillis(),
            ),
        )
        return true
    }

    private val coversScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )

    /** [fillAnimated], started and left to finish on its own. */
    fun fillInBackground(tokens: AppleTokens, dao: AppleSyncDao, songs: List<Triple<String, String?, String?>>) {
        coversScope.launch {
            runCatching { fillAnimated(tokens, dao, songs) }
        }
    }

    /**
     * Fills in what the import could not: the animated cover, one song at a time, only for songs
     * whose cache row was just seeded. [onDone] gets the count so a long run can say what it did.
     */
    suspend fun fillAnimated(
        tokens: AppleTokens,
        dao: AppleSyncDao,
        songs: List<Triple<String, String?, String?>>,
        concurrency: Int = 6,
    ): Int {
        var filled = 0
        songs.chunked(concurrency).forEach { chunk ->
            coroutineScope {
                chunk.map { (songId, catalogId, known) ->
                    async {
                        // Apple's catalogue only: a song that isn't on Apple has no business
                        // wearing an Apple cover from a name match
                        if (catalogId == null || !songId.startsWith("AM_")) return@async
                        val pair = tokens.read() ?: return@async
                        val cover = runCatching { AppleClient(pair).catalogCover(catalogId) }.getOrNull()
                        val motion = cover?.animatedUrl ?: return@async
                        dao.saveCover(
                            CoverCacheEntity(
                                songId = songId,
                                animatedUrl = motion,
                                staticUrl = sized(cover.staticUrl) ?: known,
                                source = SOURCE_MOTION,
                                fetchedAt = System.currentTimeMillis(),
                            ),
                        )
                        filled++
                    }
                }.awaitAll()
            }
        }
        return filled
    }

    /** The cached animated cover for a song, if Apple gave us one. */
    suspend fun animatedCoverFor(context: Context, songId: String): String? =
        runCatching { dao(context).cover(songId)?.animatedUrl }.getOrNull()

    /** Which step answered for this song, for a menu that says so. */
    suspend fun cachedSource(context: Context, songId: String): String? =
        runCatching { dao(context).cover(songId)?.source }.getOrNull()

    /** Clears every answered cover: the next import or refresh fetches them again. */
    suspend fun clear(context: Context) {
        runCatching { dao(context).clearCovers() }
    }

    data class AppleCover(val animatedUrl: String?, val staticUrl: String?)

    /** One catalogue read for the song: its artwork, and the motion loop of its album. */
    private suspend fun appleCover(context: Context, catalogId: String): AppleCover? {
        val tokens = AppleTokens(context).read() ?: return null
        return AppleClient(tokens).catalogCover(catalogId)
    }

}
