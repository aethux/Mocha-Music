// AppleImport.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleRematchEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.db.AppleTrackEntity
import com.mochyy.mochymusic.applesync.db.CoverCacheEntity
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

/**
 * Imports the Apple library into Mochy's own song table, so the songs actually appear in the app.
 *
 * This deliberately does not go through the account importer: that one matches every track against
 * YouTube, which is the right answer for a CSV and the wrong one here — these songs have to keep
 * their Apple identity, because every later write is addressed by it.
 *
 * The id is `AM_<catalogueId>`, which makes the import idempotent: running it twice finds the song
 * already present and skips it rather than creating a second copy with a new id.
 *
 * Written to be cheap on the second run, because that is the run that happens every time. Everything
 * it needs to compare against is read once up front (mappings, cover cache, which songs still lack
 * artwork), so a refresh of a library that has not changed writes nothing at all; the per-song
 * queries it used to make - a cover read, a mapping write and a thumbnail update per song - were
 * most of the wall clock.
 */
class AppleImport(
    private val database: MusicDatabase,
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens,
) {

    data class Result(val added: Int, val skipped: Int, val liked: Int, val albums: Int = 0)

    /** A song that is not in the library yet, resolved as far as the reads allow. */
    private class NewSong(
        val song: SongEntity,
        val artist: ArtistEntity,
        val track: AppleTrackEntity,
        val favourite: Boolean,
    )

    /** Apple's rating is the one that counts: the local heart follows it on every import. */
    private suspend fun applyFavourite(songId: String, favourite: Boolean, currentlyLiked: Boolean) {
        if (currentlyLiked == favourite) return
        runCatching {
            val row = database.getSongById(songId)?.song ?: return@runCatching
            database.query {
                update(
                    row.copy(
                        liked = favourite,
                        likedDate = if (favourite) row.likedDate ?: LocalDateTime.now() else null,
                    ),
                )
            }
        }
    }

    /**
     * Apple hands out one template with {w}x{h} in it rather than a fixed image, so the size is
     * chosen here. 300 is the list thumbnail size the app already uses, and asking for a bigger one
     * only to scale it down is the slowest option available.
     */
    private fun artwork(template: String?): String? =
        template?.replace("{w}", "300")?.replace("{h}", "300")

    /** New songs go in in batches: one transaction per song is a commit per song. */
    private val batchSize = 100

    /** [alreadyRead] is what the pull just fetched; without it this would read the library again. */
    suspend fun run(alreadyRead: List<RemoteSong>? = null): Result {
        val pair = tokens.read() ?: throw AppleSyncException("Sign in to Apple Music first.")
        val client = AppleClient(pair)

        val songs = alreadyRead ?: run {
            AppleSyncStatus.start("Reading your Apple Music library…")
            client.librarySongs { read ->
                AppleSyncStatus.update(phase = "Reading your library…", songs = read)
            }
        }

        AppleSyncStatus.update(phase = "Reading your favourites…", songs = songs.size, totalSongs = songs.size)
        val rated = runCatching { client.ratings(songs.mapNotNull { it.catalogId }) }.getOrDefault(emptyMap())

        // One read each, up front. What this fork already imported is known before the loop, which is
        // also what makes a refresh cheap once the library has been imported once.
        val known = runCatching { database.appleSongIds().toSet() }.getOrDefault(emptySet())
        val tracks = runCatching { dao.tracks() }.getOrNull()
            ?.associateBy { it.songId }
            .orEmpty()
        val cachedCovers: MutableSet<String> = runCatching { dao.covers() }.getOrNull()
            ?.mapTo(HashSet()) { it.songId }
            ?: HashSet()
        val withoutArtwork = runCatching { database.appleSongsWithoutArtwork().toSet() }
            .getOrDefault(emptySet())
        // rows that came in with a playlist and are not in the library yet: if Apple's library has
        // them now, this import is where they get their place in it
        val outsideLibrary = runCatching { database.appleSongsOutsideTheLibrary().toSet() }
            .getOrDefault(emptySet())
        val covers = runCatching { dao.coverOverrides() }.getOrNull()
            ?.associate { it.key to it.value }
            .orEmpty()

        // a song a person rematched to another recording is still this Apple song. The manual
        // mapping is its address, so a re-import must find it instead of making a second row for
        // the same catalogue id - that is what "it stays connected to the recording I picked" means.
        val manual = runCatching { dao.rematchRows(AppleRematchEntity.SOURCE_APPLE) }
            .getOrDefault(emptyList())
            .associateBy { it.remoteId }

        // one read, not one per song: the mirror only needs to know what changed
        val likedNow = runCatching { database.likedSongsByNameAsc().first() }
            .getOrDefault(emptyList())
            .map { it.id }
            .toSet()

        var added = 0
        var skipped = 0
        var liked = 0

        val pendingTracks = ArrayList<AppleTrackEntity>(songs.size)
        val pendingCovers = ArrayList<CoverCacheEntity>()
        val newSongs = ArrayList<NewSong>()
        val artistIds = HashMap<String, String>()

        /** Writes the mapping only when one of its fields actually moved. */
        fun noteTrack(songId: String, catalogId: String, song: RemoteSong, state: String) {
            val row = AppleTrackEntity(
                songId = songId,
                catalogId = catalogId,
                libraryId = song.libraryId,
                isrc = song.isrc,
                state = state,
                updatedAt = System.currentTimeMillis(),
            )
            val was = tracks[songId]
            if (was != null && was.catalogId == row.catalogId && was.libraryId == row.libraryId &&
                was.isrc == row.isrc && was.state == row.state
            ) {
                return
            }
            pendingTracks += row
        }

        /** Every song gets a cache row once, or the next import asks Apple for a cover it has seen. */
        fun noteCover(songId: String, artworkUrl: String?) {
            if (songId in cachedCovers) return
            val url = artwork(artworkUrl) ?: return
            pendingCovers += CoverCacheEntity(
                songId = songId,
                staticUrl = url,
                source = AppleCovers.SOURCE_ART,
                fetchedAt = System.currentTimeMillis(),
            )
            cachedCovers += songId
        }

        songs.forEachIndexed { index, song ->
            val catalogId = song.catalogId ?: return@forEachIndexed
            if (index % 20 == 0) {
                AppleSyncStatus.update(
                    phase = "Importing songs…",
                    songs = index,
                    totalSongs = songs.size,
                )
            }

            val songId = "AM_$catalogId"
            noteCover(songId, song.artworkUrl)
            // Apple is the library of record, so a heart set there comes back here too. This is the
            // half of "like works both ways" the import used to skip: it wrote the flag when a song
            // was first brought in and never looked at it again.
            val favourite = rated[catalogId] == 1L

            // the chosen recording keeps the Apple row's place: its mapping is refreshed at this
            // catalogue id and nothing new is inserted
            manual[catalogId]?.let { mapped ->
                noteTrack(mapped.songId, catalogId, song, AppleTrackEntity.STATE_MANUAL)
                if (mapped.songId in withoutArtwork) {
                    runCatching {
                        artwork(song.artworkUrl)?.let { database.setSongThumbnailIfMissing(mapped.songId, it) }
                    }
                }
                // it came back from Apple: the chosen recording goes back into the library
                runCatching {
                    val row = database.getSongById(mapped.songId)?.song
                    if (row != null && row.inLibrary == null) {
                        database.query { update(row.copy(inLibrary = LocalDateTime.now())) }
                    }
                }
                applyFavourite(mapped.songId, favourite, mapped.songId in likedNow)
                skipped++
                return@forEachIndexed
            }

            if (songId in known) {
                // a song a playlist brought in becomes a library song the moment Apple's library has it
                if (songId in outsideLibrary) {
                    runCatching {
                        database.getSongById(songId)?.song?.let { row ->
                            if (row.inLibrary == null) {
                                database.query { update(row.copy(inLibrary = LocalDateTime.now())) }
                            }
                        }
                    }
                }
                // record the mapping as well: without it the song reads as "not on Apple" and keeps
                // its red dot forever, which is exactly backwards
                noteTrack(songId, catalogId, song, AppleTrackEntity.STATE_AUTO)
                skipped++
                // an import from before the covers existed still gets its artwork, without the row
                // being touched in any other way
                if (songId in withoutArtwork) {
                    runCatching {
                        artwork(song.artworkUrl)?.let { database.setSongThumbnailIfMissing(songId, it) }
                    }
                }
                applyFavourite(songId, favourite, songId in likedNow)
                return@forEachIndexed
            }

            // the import writes Apple's artwork only when the person has not chosen one
            val chosenCover = covers[CoverOverrides.key(CoverOverrides.Kind.SONG, songId)]
            // an Apple artist carries no artwork here, so a picture chosen by hand is all it has
            val artistName = song.artist.ifBlank { "Unknown artist" }
            val artistId = artistIds.getOrPut(artistName) {
                database.artistByName(artistName)?.id ?: ArtistEntity.generateArtistId()
            }
            val artistCover = covers[CoverOverrides.key(CoverOverrides.Kind.ARTIST, artistId)]

            newSongs += NewSong(
                song = SongEntity(
                    id = songId,
                    title = song.title,
                    duration = ((song.durationMs ?: 0L) / 1000L).toInt(),
                    thumbnailUrl = chosenCover ?: artwork(song.artworkUrl),
                    albumName = song.album,
                    // the shelf is a query on this column; the album row itself is written
                    // in one pass after the songs, once every song it points at exists
                    albumId = song.albumId?.let(AppleAlbums::id),
                    liked = favourite,
                    likedDate = if (favourite) LocalDateTime.now() else null,
                    inLibrary = LocalDateTime.now(),
                ),
                artist = ArtistEntity(id = artistId, name = artistName, thumbnailUrl = artistCover),
                track = AppleTrackEntity(
                    songId = songId,
                    catalogId = catalogId,
                    libraryId = song.libraryId,
                    isrc = song.isrc,
                    state = AppleTrackEntity.STATE_AUTO,
                    updatedAt = System.currentTimeMillis(),
                ),
                favourite = favourite,
            )
        }

        // A batch that fails is retried a row at a time, so one bad row costs one song and not a
        // hundred - the same outcome the per-song transaction had, without a commit per song.
        newSongs.chunked(batchSize).forEach { batch ->
            val ok = runCatching {
                database.transaction {
                    batch.forEach { new ->
                        insert(new.song)
                        // insert is IGNORE: an artist that already exists keeps whatever picture it has
                        insert(new.artist)
                        insert(SongArtistMap(songId = new.song.id, artistId = new.artist.id, position = 0))
                    }
                }
            }.isSuccess

            val written = if (ok) {
                batch
            } else {
                batch.filter { new ->
                    runCatching {
                        database.transaction {
                            insert(new.song)
                            insert(new.artist)
                            insert(SongArtistMap(songId = new.song.id, artistId = new.artist.id, position = 0))
                        }
                    }.isSuccess
                }
            }

            written.forEach { new ->
                pendingTracks += new.track
                added++
                if (new.favourite) liked++
            }
            skipped += batch.size - written.size
        }

        if (pendingCovers.isNotEmpty()) runCatching { dao.saveCovers(pendingCovers) }
        if (pendingTracks.isNotEmpty()) runCatching { dao.saveTracks(pendingTracks) }

        // the album rows and their links go in last: every song they point at is in place by now,
        // which is what the foreign keys need, and an album that was already imported is refreshed
        // rather than duplicated
        val albumResult = runCatching {
            AppleSyncStatus.update(phase = "Writing albums…", songs = songs.size, totalSongs = songs.size)
            AppleAlbums.import(database, dao, songs)
        }.getOrDefault(AppleAlbums.Result(0, 0))

        // the animated covers are the slow part (two reads a song) and nothing else waits on them:
        // they run in the background so a refresh finishes and the playlists can follow it
        val withMotion = 0
        AppleCovers.fillInBackground(
            tokens = tokens,
            dao = dao,
            songs = songs.map { Triple("AM_${it.catalogId}", it.catalogId, artwork(it.artworkUrl)) },
        )

        AppleSyncStatus.update(phase = "Done", songs = songs.size, totalSongs = songs.size)
        AppleSyncStatus.finish(
            "$added new songs imported · ${if (skipped > 0) "$skipped already there" else "none were duplicates"}" +
                (if (albumResult.albums > 0) " · ${albumResult.albums} albums" else "") +
                if (withMotion > 0) " · $withMotion animated covers" else "",
        )
        return Result(added = added, skipped = skipped, liked = liked, albums = albumResult.albums)
    }
}
