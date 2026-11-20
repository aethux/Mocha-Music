package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.applesync.db.AppleRematchEntity
import com.mocha.mochymusic.modules.SourceModules
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pin table as playback sees it, against an in-memory store: no database, no network.
 *
 * The interesting questions are all about identity - which provider a pin names, what a module gets
 * handed, and whether clearing one puts the song back on its own recording.
 */
class AppleRematchTest {

    private class FakeStore : AppleRematchStore {
        val rows = LinkedHashMap<String, AppleRematchEntity>()
        var reads = 0

        override suspend fun rematches(): List<AppleRematchEntity> {
            reads++
            return rows.values.toList()
        }

        override suspend fun rematch(songId: String) = rows[songId]
        override suspend fun rematchedSongIds(source: String) = rows.values.filter { it.source == source }.map { it.songId }
        override suspend fun saveRematch(row: AppleRematchEntity) {
            rows[row.songId] = row
        }

        override suspend fun clearRematch(songId: String) {
            rows.remove(songId)
        }
    }

    private fun rematch(store: FakeStore) = AppleRematch(store)

    @Test
    fun `a song with no pin plays its own recording`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        assertNull(rematch.pin("youtubeId"))
        assertNull(rematch.externalId("youtubeId"))
    }

    @Test
    fun `an Apple pin is handed to a module as provider plus id`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        rematch.save("youtubeId", AppleRematchEntity.SOURCE_APPLE, "1440857781", title = "Alive", artist = "Sia")

        assertEquals("applemusic:1440857781", rematch.externalId("youtubeId"))
        assertEquals("applemusic", rematch.pin("youtubeId")?.source)
        // saved through the store, so a restart reads it back
        assertEquals("1440857781", store.rows.getValue("youtubeId").remoteId)
        assertEquals(AppleRematchEntity.STATE_MANUAL, store.rows.getValue("youtubeId").state)
    }

    @Test
    fun `a YouTube pin needs no external id, the song's own id is the video`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        rematch.save("youtubeId", AppleRematchEntity.SOURCE_YOUTUBE, "youtubeId")

        assertEquals(AppleRematchEntity.SOURCE_YOUTUBE, rematch.pin("youtubeId")?.source)
        assertNull(rematch.externalId("youtubeId"))
    }

    @Test
    fun `clearing a pin puts the song back on its own recording`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        rematch.save("youtubeId", AppleRematchEntity.SOURCE_APPLE, "1")
        rematch.clear("youtubeId")

        assertNull(rematch.pin("youtubeId"))
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `the table is read once, not once per play`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        rematch.save("a", AppleRematchEntity.SOURCE_APPLE, "1")
        rematch.pin("a")
        rematch.pin("b")
        rematch.externalId("a")
        assertEquals(1, store.reads)
    }

    @Test
    fun `only Apple pins take the not-on-Apple dot away`() = runBlocking {
        val store = FakeStore()
        val rematch = rematch(store)
        rematch.save("appleOne", AppleRematchEntity.SOURCE_APPLE, "1", title = "One")
        rematch.save("monoOne", AppleRematchEntity.SOURCE_MONOCHROME, "abc", title = "One")
        rematch.save("youTubeOne", AppleRematchEntity.SOURCE_YOUTUBE, "youTubeOne", title = "One")

        assertTrue("appleOne" in AppleSyncStatus.manualAppleSongIds)
        assertTrue("monoOne" !in AppleSyncStatus.manualAppleSongIds)
        assertTrue("youTubeOne" !in AppleSyncStatus.manualAppleSongIds)
    }

    @Test
    fun `provider ids are the provider order's own`() {
        // a pin is a provider id, so these strings and the order's must be the same strings
        assertEquals(SourceModules.YouTubeId, AppleRematchEntity.SOURCE_YOUTUBE)
        assertEquals("applemusic", AppleRematchEntity.SOURCE_APPLE)
        assertEquals("monochrome", AppleRematchEntity.SOURCE_MONOCHROME)
    }

    @Test
    fun `a pasted Monochrome link yields the track id`() {
        assertEquals("abc123", monochromeTrackId("https://tracks.example.com/track/abc123"))
        assertEquals("abc123", monochromeTrackId("https://relay.example.com/s/abc123?q=flac"))
        assertEquals("abc123", monochromeTrackId("tracks.example.com/track/abc123"))
        assertEquals("abc123", monochromeTrackId("abc123"))
        assertEquals("abc123", monochromeTrackId("  https://tracks.example.com/track/abc123  "))
    }

    @Test
    fun `anything that is not a track link yields nothing`() {
        assertNull(monochromeTrackId(""))
        assertNull(monochromeTrackId("https://tracks.example.com/"))
        assertNull(monochromeTrackId("https://tracks.example.com/some-other-page"))
        assertNull(monochromeTrackId("two words"))
    }
}
