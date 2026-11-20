// AppleAdopt.kt
//
// Taking a song out of Apple's catalogue and into Mocha: the row, its artist, and the library write
// that makes Apple agree. Searching Apple is only useful if the result can be played and kept, and
// a catalogue song has no row here at all until this runs.

package com.mocha.mochymusic.applesync

import android.content.Context
import com.mocha.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.importer.array
import com.mocha.mochymusic.importer.idString
import com.mocha.mochymusic.importer.long
import com.mocha.mochymusic.importer.obj
import com.mocha.mochymusic.importer.string
import com.mocha.mochymusic.db.entities.ArtistEntity
import com.mocha.mochymusic.db.entities.SongArtistMap
import com.mocha.mochymusic.db.entities.SongEntity
import java.time.LocalDateTime

object AppleAdopt {

    /** Apple's artwork template, {w}x{h} and all; 300 is the size the lists already ask for. */
    private fun artwork(template: String?): String? =
        template?.replace("{w}", "300")?.replace("{h}", "300")

    /**
     * Writes the catalogue song as a library row and puts it in the Apple library, so both sides
     * agree from the first tap. Returns the row id, or null if the write failed.
     */
    suspend fun song(
        context: Context,
        database: MusicDatabase,
        catalogId: String,
        title: String,
        artist: String,
        album: String?,
        durationMs: Long?,
        artworkUrl: String?,
        /** false: just a row to play from, the way a search result should behave */
        addToLibrary: Boolean = false,
    ): String? = runCatching {
        val songId = "AM_$catalogId"
        database.transaction {
            insert(
                SongEntity(
                    id = songId,
                    title = title,
                    duration = ((durationMs ?: 0L) / 1000L).toInt(),
                    thumbnailUrl = artwork(artworkUrl),
                    albumName = album,
                    inLibrary = if (addToLibrary) LocalDateTime.now() else null,
                ),
            )
            val artistName = artist.ifBlank { "Unknown artist" }
            val artistId = database.artistByName(artistName)?.id ?: ArtistEntity.generateArtistId()
            insert(ArtistEntity(id = artistId, name = artistName))
            insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
        }
        if (addToLibrary) {
            // the row alone would leave the two libraries disagreeing, so Apple hears about it now
            runCatching { AppleSongWrites.pushLibrary(context = context, songId = songId, inLibrary = true) }
        }
        songId
    }.getOrNull()

    /** What kind of thing a search hit is; each kind is drawn and opened differently. */
    enum class Kind { Song, Artist, Album, Playlist, Video }

    data class Hit(
        val kind: Kind,
        val id: String,
        val title: String,
        val subtitle: String?,
        val artworkUrl: String?,
        val durationMs: Long? = null,
    )

    /**
     * One search, every kind of result, the way Apple's own search answers.
     *
     * The screen filters the list by the same chips the app's search uses, so asking for each kind
     * separately would be five requests for one typed term.
     */
    suspend fun searchEverything(context: Context, term: String): Map<Kind, List<Hit>> {
        val tokens = runCatching { AppleTokens(context).read() }.getOrNull() ?: return emptyMap()
        val json = runCatching { AppleClient(tokens).searchAll(term) }.getOrNull() ?: return emptyMap()
        val results = json["results"].obj() ?: return emptyMap()
        fun rows(key: String, kind: Kind): List<Hit> =
            results[key].obj()?.get("data").array().orEmpty().mapNotNull { item ->
                val obj = item.obj() ?: return@mapNotNull null
                val attributes = obj["attributes"].obj() ?: return@mapNotNull null
                val id = obj.idString("id") ?: return@mapNotNull null
                val name = attributes.string("name") ?: return@mapNotNull null
                Hit(
                    kind = kind,
                    id = id,
                    title = name,
                    subtitle = attributes.string("artistName")
                        ?: attributes.string("curatorName")
                        ?: attributes.string("artistNames"),
                    artworkUrl = attributes["artwork"].obj()?.string("url"),
                    durationMs = attributes.long("durationInMillis"),
                )
            }
        return mapOf(
            Kind.Song to rows("songs", Kind.Song),
            Kind.Artist to rows("artists", Kind.Artist),
            Kind.Album to rows("albums", Kind.Album),
            Kind.Playlist to rows("playlists", Kind.Playlist),
            Kind.Video to rows("music-videos", Kind.Video),
        )
    }

    /** Which of these catalogue ids are already in the Apple library, so the list can say so. */
    suspend fun inLibrary(context: Context, catalogIds: List<String>): Set<String> =
        if (catalogIds.isEmpty()) {
            emptySet()
        } else {
            runCatching {
                dagger.hilt.android.EntryPointAccessors
                    .fromApplication(context.applicationContext, AppleSongWritesEntryPoint::class.java)
                    .dao()
                    .catalogIdsInLibrary(catalogIds)
                    .toSet()
            }.getOrDefault(emptySet())
        }

    /** Catalogue search, best first; empty on any failure rather than throwing into a screen. */
    suspend fun search(context: Context, term: String, limit: Int = 25): List<CatalogSong> {
        val tokens = runCatching { AppleTokens(context).read() }.getOrNull() ?: return emptyList()
        return runCatching { AppleClient(tokens).searchCatalog(term, limit) }.getOrDefault(emptyList())
    }
}
