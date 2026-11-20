package com.mocha.mochymusic.applesync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The matcher makes one decision that matters: is this the same recording. These cases are the ones
 * that go wrong in practice, and none of them need the network because scoring is pure.
 */
class AppleMatcherTest {

    private val matcher = AppleMatcher(AppleClient(AppleTokens.Pair("dev", "user")))

    private fun song(
        id: String,
        title: String,
        artist: String,
        album: String? = null,
        seconds: Int = 240,
    ) = CatalogSong(
        id = id,
        title = title,
        artist = artist,
        album = album,
        durationMs = seconds * 1000L,
        isrc = null,
    )

    @Test
    fun `the studio recording wins over a live version`() {
        val candidates = listOf(
            song("live", "Bohemian Rhapsody (Live at Wembley)", "Queen", "Live at Wembley", 290),
            song("studio", "Bohemian Rhapsody", "Queen", "A Night at the Opera", 355),
            song("cover", "Bohemian Rhapsody", "The Rock Choir", "Choir Hits", 350),
        )

        val ranked = matcher.rank(candidates, "Bohemian Rhapsody", "Queen", "A Night at the Opera", 354_000)

        assertEquals("studio", ranked.first().song.id)
    }

    @Test
    fun `a different artist is not accepted`() {
        val candidates = listOf(
            song("wrong", "Bohemian Rhapsody", "Some Tribute Band", null, 200),
        )

        assertTrue(matcher.rank(candidates, "Bohemian Rhapsody", "Queen", null, 354_000).isEmpty())
    }

    @Test
    fun `a sped up version is rejected even with the right title`() {
        val candidates = listOf(
            song("sped", "Bohemian Rhapsody (Sped Up)", "Queen", null, 200),
        )

        val ranked = matcher.rank(candidates, "Bohemian Rhapsody", "Queen", null, 354_000)

        assertTrue(ranked.isEmpty())
    }

    @Test
    fun `a wrong length is not accepted on its own`() {
        val candidates = listOf(
            song("hour", "Bohemian Rhapsody", "Queen", null, 3_600),
        )

        assertTrue(matcher.rank(candidates, "Bohemian Rhapsody", "Queen", null, 354_000).isEmpty())
    }

    @Test
    fun `featuring credits do not break a match`() {
        val candidates = listOf(
            song("feat", "Die With A Smile", "Lady Gaga & Bruno Mars", "Single", 251),
        )

        val ranked = matcher.rank(candidates, "Die With A Smile (feat. Bruno Mars)", "Lady Gaga", null, 251_000)

        assertEquals("feat", ranked.first().song.id)
    }
}
