package com.mochyy.mochymusic.applesync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The recorded shapes Apple actually sends. These are trimmed captures of real responses: the ids,
 * the nested `playParams`, and the uploads that have no catalogue id at all.
 */
class AppleLibraryTest {

    private fun json(text: String): JsonObject = Json.parseToJsonElement(text) as JsonObject

    @Test
    fun storefrontComesFromTheFirstDataRow() {
        val page = json("""{"data":[{"id":"in","type":"storefronts"}]}""")
        assertEquals("in", AppleLibrary.storefront(page))
        assertNull(AppleLibrary.storefront(json("""{"data":[]}""")))
    }

    @Test
    fun playlistsCarryNameAndCanEdit() {
        val page = json(
            """
            {"data":[
              {"id":"p.ABC123","type":"library-playlists","attributes":{"name":"Road trip","canEdit":true}},
              {"id":"p.DEF456","type":"library-playlists","attributes":{"name":"Shared","canEdit":false}},
              {"type":"library-playlists","attributes":{"name":"no id"}}
            ]}
            """,
        )
        val lists = AppleLibrary.playlists(page)
        assertEquals(2, lists.size)
        assertEquals("Road trip", lists[0].name)
        assertTrue(lists[0].canEdit)
        assertEquals("Shared", lists[1].name)
        assertTrue("canEdit false must survive parsing", !lists[1].canEdit)
    }

    @Test
    fun aPlaylistWithNoNameStillReads() {
        val page = json("""{"data":[{"id":"p.X","attributes":{}}]}""")
        assertEquals("Untitled playlist", AppleLibrary.playlists(page).single().name)
    }

    @Test
    fun aPlaylistsOwnArtworkIsReadAndAStreamsIsNot() {
        // A user's playlist carries its own artwork; a playlist Apple made for a station does not,
        // and the import falls back to the first track rather than replacing a cover with nothing.
        val page = json(
            """
            {"data":[
              {"id":"p.MINE","attributes":{"name":"Mine","artwork":{"url":"https://example.com/{w}x{h}bb.jpg"}}},
              {"id":"p.RADIO","attributes":{"name":"Radio"}}
            ]}
            """,
        )
        val lists = AppleLibrary.playlists(page)
        assertEquals("https://example.com/{w}x{h}bb.jpg", lists[0].artworkUrl)
        assertNull("a playlist without artwork must stay null", lists[1].artworkUrl)
    }

    @Test
    fun aPlaylistOwnerComesFromItsCatalogueCounterpart() {
        // The library resource has no owner; asking for `include=catalog` adds the catalogue copy,
        // and that one carries the name the web player prints.
        val page = json(
            """
            {"data":[{"id":"p.MINE","type":"library-playlists",
              "attributes":{"name":"Mine","canDelete":true},
              "relationships":{"catalog":{"data":[
                {"id":"pl.123","type":"playlists","attributes":{"name":"Mine","curatorName":"A Person"}}
              ]}}}]}
            """,
        )
        val owner = AppleLibrary.playlistOwner(page)
        assertEquals("A Person", owner.ownerName)
        assertEquals("pl.123", owner.catalogId)
        // a playlist with no catalogue counterpart says nothing rather than guessing
        val empty = AppleLibrary.playlistOwner(json("""{"data":[{"id":"p.X","attributes":{}}]}"""))
        assertNull(empty.ownerName)
        assertNull(empty.catalogId)
    }

    @Test
    fun songsReadTheCatalogueIdOutOfPlayParams() {
        val page = json(
            """
            {"data":[
              {"id":"i.ONE","type":"library-songs","attributes":{
                "name":"FATHER","artistName":"Kanye West","albumName":"Yandhi",
                "durationInMillis":198000,"isrc":"USUM71900001",
                "playParams":{"id":"1440857781","kind":"song","isLibrary":true,"catalogId":"1440857781"}}},
              {"id":"i.TWO","type":"library-songs","attributes":{
                "name":"My own upload","artistName":"Me","playParams":{"kind":"song","isLibrary":true}}}
            ]}
            """,
        )
        val songs = AppleLibrary.songs(page)
        assertEquals(2, songs.size)
        assertEquals("1440857781", songs[0].catalogId)
        assertEquals("USUM71900001", songs[0].isrc)
        assertEquals(198000L, songs[0].durationMs)
        assertNull("an upload has no catalogue id", songs[1].catalogId)
    }

    @Test
    fun aSongsAlbumIsReadFromItsRelationships() {
        val page = json(
            """
            {"data":[
              {"id":"i.ONE","type":"library-songs","attributes":{
                "name":"FATHER","artistName":"Kanye West","albumName":"Yandhi","trackNumber":3,
                "playParams":{"kind":"song","isLibrary":true,"catalogId":"1440857781"}},
               "relationships":{"albums":{"data":[
                 {"id":"l.ALBUM1","type":"library-albums","attributes":{
                   "name":"Yandhi","trackCount":16,"releaseDate":"2018-09-29",
                   "playParams":{"id":"1440857700","kind":"album","catalogId":"1440857700"},
                   "artwork":{"url":"https://is1.mzstatic.com/x/{w}x{h}bb.jpg"}}}]}}},
              {"id":"i.TWO","type":"library-songs","attributes":{
                "name":"An upload","artistName":"Me","playParams":{"kind":"song","isLibrary":true}},
               "relationships":{"albums":{"data":[
                 {"id":"l.ALBUM2","type":"library-albums","attributes":{"name":"Mine"}}]}}},
              {"id":"i.THREE","type":"library-songs","attributes":{"name":"No album here"}}
            ]}
            """,
        )
        val songs = AppleLibrary.songs(page)
        assertEquals(3, songs.size)
        // the catalogue id when Apple gives one: the same album is one row everywhere
        assertEquals("1440857700", songs[0].albumId)
        assertEquals("https://is1.mzstatic.com/x/{w}x{h}bb.jpg", songs[0].albumArtworkUrl)
        assertEquals(16, songs[0].albumTrackCount)
        assertEquals(2018, songs[0].albumYear)
        assertEquals(3, songs[0].trackNumber)
        // a library-only album has no catalogue id, so its own id is the stable one
        assertEquals("l.ALBUM2", songs[1].albumId)
        assertNull("no relationships means no album, not a crash", songs[2].albumId)
    }

    @Test
    fun aRowWithNoIdIsSkippedRatherThanCrashing() {
        val page = json("""{"data":[{"attributes":{"name":"nameless"}}]}""")
        assertTrue(AppleLibrary.songs(page).isEmpty())
    }

    @Test
    fun nextPageOnlyTrustsARelativePath() {
        assertEquals(
            "/v1/me/library/songs?offset=100&limit=100",
            AppleLibrary.nextPage(json("""{"next":"/v1/me/library/songs?offset=100&limit=100"}""")),
        )
        assertNull(AppleLibrary.nextPage(json("""{"data":[]}""")))
        assertNull(AppleLibrary.nextPage(json("""{"next":"https://evil.example/v1/x"}""")))
    }
}
