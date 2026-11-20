// AppleAlbums.kt
//
// Apple's albums, written into Mocha's own album table so the Albums shelf is not empty for music
// that came from Apple.
//
// A song reaches the shelf two ways and both are needed: `song.albumId` is what the shelf's query
// looks at ("albums with a song in the library"), and a `song_album_map` row is what the album's own
// page lists. Writing one without the other is an album that appears and then opens empty.
//
// Everything here is idempotent: a re-import refreshes the count, the year and the artwork it owns,
// and leaves a bookmarked album, a chosen cover and the song rows themselves alone.
//
// Done as one pass rather than one transaction per album. Per album it used to be a relation query
// (`album(id)`, which loads the album's songs), a cover read, an artist read and a row read per
// song; 918 albums made that minutes of work. Now it is three reads for the whole set, and the
// writing happens in batches.

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.applesync.db.AppleStateEntity
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import com.mocha.mochymusic.db.MusicDatabase
import com.mocha.mochymusic.db.entities.AlbumArtistMap
import com.mocha.mochymusic.db.entities.AlbumEntity
import com.mocha.mochymusic.db.entities.ArtistEntity
import com.mocha.mochymusic.db.entities.SongAlbumMap
import java.time.LocalDateTime

object AppleAlbums {

    /** Apple's albums have their own id space: a catalogue album id is not a song's. */
    const val PREFIX = "AM_ALB_"

    fun id(remoteAlbumId: String): String = "$PREFIX$remoteAlbumId"

    data class Result(val albums: Int, val songs: Int)

    /** Albums per transaction: big enough that the fixed cost disappears, small enough to stay bounded. */
    private const val Batch = 200

    /**
     * Apple hands out one artwork template with {w}x{h} in it; 300 is the size the app's lists ask
     * for, and asking for more only to scale it down is the slowest option there is.
     */
    private fun artwork(template: String?): String? =
        template?.replace("{w}", "300")?.replace("{h}", "300")

    /**
     * Writes an album row per album these songs belong to, and links each song to it.
     *
     * Called after the songs are in the database: the link tables carry foreign keys, so an album
     * cannot be pointed at a song row that does not exist yet.
     */
    suspend fun import(
        database: MusicDatabase,
        dao: AppleSyncDao,
        songs: List<RemoteSong>,
    ): Result {
        val groups = songs
            .mapNotNull { song ->
                val album = song.albumId ?: return@mapNotNull null
                val catalogId = song.catalogId ?: return@mapNotNull null
                Triple("AM_$catalogId", id(album), song)
            }
            .groupBy { it.second }

        if (groups.isEmpty()) return Result(0, 0)

        // The pass rewrites every album row and every song→album link. When neither the albums nor
        // which song is on which has changed - the usual case on a refresh - that is a minute of
        // writes for nothing, so a stamp of the resulting shape decides whether to run it at all.
        // It is a digest: the shape itself is 44 KB on a 917-album library, and only equality matters.
        val signature = java.security.MessageDigest.getInstance("SHA-256")
            .digest(
                groups.entries
                    .flatMap { (albumId, entries) -> entries.map { "$albumId:${it.first}" } }
                    .sorted()
                    .joinToString(",")
                    .toByteArray(),
            )
            .joinToString("") { "%02x".format(it) }
            .take(32)
        if (runCatching { dao.state(SIGNATURE_KEY) }.getOrNull() == signature) return Result(0, 0)

        // Three reads for the whole pass, not one per album and one per song. A failed read is a
        // failed read: it is allowed to fail the import rather than pass for "no albums yet".
        val existing = database.appleSongDao.appleAlbums().associateBy { it.id }
        val linked = database.appleSongDao.appleAlbumLinks().associate { it.id to it.albumId }
        val covers = runCatching { dao.coverOverrides() }.getOrNull()
            ?.associate { it.key to it.value }
            ?: emptyMap()

        val artistIds = HashMap<String, String>()
        var albums = 0
        var songsLinked = 0

        groups.entries.chunked(Batch).forEach { batch ->
            database.transaction {
                batch.forEach { (albumId, entries) ->
                    val first = entries.first().third
                    val title = first.album ?: "Unknown album"
                    // a copy the person chose is the cover; Apple's is the one that fills in behind it
                    val chosen = covers[CoverOverrides.key(CoverOverrides.Kind.ALBUM, albumId)]
                    val current = existing[albumId]

                    val row = (current ?: AlbumEntity(id = albumId, title = title, songCount = 0, duration = 0)).copy(
                        title = title.ifBlank { current?.title ?: "Unknown album" },
                        year = first.albumYear ?: current?.year,
                        // Apple's artwork only when nothing was chosen and nothing is set yet
                        thumbnailUrl = chosen ?: current?.thumbnailUrl ?: artwork(first.albumArtworkUrl),
                        // Apple knows the whole album; the library may only hold a few of its songs
                        songCount = maxOf(first.albumTrackCount ?: 0, entries.size),
                        duration = entries.sumOf { (it.third.durationMs ?: 0L) / 1000L }.toInt(),
                        lastUpdateTime = LocalDateTime.now(),
                    )

                    // insert is IGNORE: an album that is already there keeps its bookmarks, and
                    // update then fills in what Apple knows
                    insert(row)
                    update(row)

                    val artistName = first.artist.ifBlank { "Unknown artist" }
                    val artistId = artistIds.getOrPut(artistName) {
                        database.artistByName(artistName)?.id ?: ArtistEntity.generateArtistId()
                    }
                    insert(ArtistEntity(id = artistId, name = artistName))
                    insert(AlbumArtistMap(albumId = albumId, artistId = artistId, order = 0))

                    entries.forEachIndexed { position, entry ->
                        insert(
                            SongAlbumMap(
                                songId = entry.first,
                                albumId = albumId,
                                // Apple's own track number when it has one, so the album page's
                                // order is the album's order and not the library's
                                index = entry.third.trackNumber ?: position + 1,
                            ),
                        )
                        // the shelf looks at the song, not the map; only written when it changed
                        if (linked[entry.first] != albumId) setSongAlbum(entry.first, albumId)
                    }

                    albums++
                    songsLinked += entries.size
                }
            }
        }

        runCatching { dao.saveState(AppleStateEntity(key = SIGNATURE_KEY, value = signature)) }
        return Result(albums = albums, songs = songsLinked)
    }

    /** The albums and their songs, as one string: equal means the pass has nothing to write. */
    private const val SIGNATURE_KEY = "album_signature"
}
