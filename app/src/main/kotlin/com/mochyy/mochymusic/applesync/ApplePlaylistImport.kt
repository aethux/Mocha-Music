// ApplePlaylistImport.kt
//
// Apple's playlists as real Mochy playlists: a row you can open, play and add to, with its songs,
// and a link row so the guard knows which Apple playlist it belongs to. Re-running is safe - the
// membership is rewritten from Apple's own order each time.
//
// Two things keep a refresh from being expensive. The tracks are read a few playlists at a time
// (twenty-six serial round trips is a minute of latency, not work), and a playlist whose membership
// has not changed is not rewritten: no clear, no re-insert of every row, no link write.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleLinkEntity
import com.mochyy.mochymusic.applesync.db.AppleStateEntity
import com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.db.entities.ArtistEntity
import com.mochyy.mochymusic.db.entities.PlaylistEntity
import com.mochyy.mochymusic.db.entities.PlaylistSongMap
import com.mochyy.mochymusic.db.entities.SongArtistMap
import com.mochyy.mochymusic.db.entities.SongEntity
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import java.time.LocalDateTime

object ApplePlaylistImport {

    data class Result(
        val playlists: Int,
        val songs: Int,
        val skipped: Int,
        /** Playlists whose membership had not changed: read from Apple, not written. */
        val unchanged: Int = 0,
        /** Songs that came with a playlist but are not in the library. */
        val outsideTheLibrary: Int = 0,
    )

    /** The local playlist id an Apple playlist is imported as. */
    fun localId(remoteId: String): String = "AM_PL_$remoteId"

    /** Bumped when an import starts meaning something new; the stamp only skips work for a version. */
    private const val IMPORT_VERSION_KEY = "playlist_import_version"
    private const val IMPORT_VERSION = "10"

    /** How many playlists are read at once. Apple's API is latency-bound, not throughput-bound. */
    private const val Reads = 4

    /** Imports every playlist on the account, in Apple's own order. */
    suspend fun run(context: Context, onProgress: (Int, Int) -> Unit = { _, _ -> }): Result {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppleSongWritesEntryPoint::class.java,
        )
        val database = entry.database()
        val dao = entry.dao()
        val tokens = entry.tokens()
        val pair = runCatching { tokens.read() }.getOrNull() ?: return Result(0, 0, 0)
        val client = AppleClient(pair)

        val remotes = runCatching { client.playlists() }.getOrDefault(emptyList())
        var songs = 0
        var skipped = 0
        var unchanged = 0
        var extra = 0

        // one read for the whole pass: asking per track which songs exist was 742 queries on this
        // account, and every one of them was a round trip in the middle of the import
        val known = runCatching { database.appleSongIds() }.getOrDefault(emptyList()).toMutableSet()
        // songs a playlist brought in but the library does not hold: they live outside the library
        val outside = runCatching { database.appleSongsOutsideTheLibrary() }.getOrDefault(emptyList()).toSet()
        // who the account is, for the playlists that are the account's own
        val accountLabel = runCatching { dao.account().first()?.label }.getOrNull()

        // Apple stamps every playlist with a lastModifiedDate. A playlist whose stamp has not moved
        // since the last import has the same songs in the same order, so its tracks are not read at
        // all: a refresh of an unchanged account now asks Apple for nothing per playlist.
        // The owner and the catalogue id come from one read per playlist, and the id decides where
        // the tracks are read from - so it is learned before anything is planned, not during.
        val before = runCatching { dao.playlistMetas() }.getOrDefault(emptyList()).associateBy { it.playlistId }
        runCatching {
            val todo = remotes.filter {
                val meta = before[localId(it.id)]
                meta?.ownerChecked != true || meta.catalogId == null
            }
            todo.chunked(Reads).forEach { chunk ->
                coroutineScope {
                    chunk.map { remote ->
                        async { remote.id to client.playlistCurator(remote.id) }
                    }.awaitAll().forEach { (id, owner) ->
                        if (owner != null) PlaylistOwner.record(dao, localId(id), owner)
                    }
                }
            }
        }
        val metas = runCatching { dao.playlistMetas() }.getOrDefault(emptyList()).associateBy { it.playlistId }
        // A change to what an import *means* has to reach playlists Apple has not touched since, so
        // the stamp only skips work for the version that wrote it.
        val upToDate = runCatching { dao.state(IMPORT_VERSION_KEY) }.getOrNull() == IMPORT_VERSION
        val needsRead = remotes.filter { remote ->
            val meta = metas[localId(remote.id)]
            !upToDate || meta == null || remote.lastModified == null || meta.lastModified != remote.lastModified
        }

        // read the tracks before writing anything: a failure is then still a failure of the read,
        // and the loop below only decides what to write
        var read = 0
        val tracks = HashMap<String, List<RemoteSong>>(needsRead.size)
        // the storefront is one request, and only needed when a catalogue copy has to be read
        val storefront = if (needsRead.any { metas[localId(it.id)]?.catalogId != null }) {
            runCatching { client.storefront() }.getOrNull()
        } else {
            null
        }
        needsRead.chunked(Reads).forEach { chunk ->
            coroutineScope {
                chunk.map { remote ->
                    async {
                        // A followed or editorial playlist's library resource lists only the tracks
                        // that are also in the library, so the catalogue copy is the complete list.
                        val catalogId = metas[localId(remote.id)]?.catalogId
                        val loaded = if (catalogId != null && storefront != null) {
                            runCatching { client.catalogPlaylistTracks(catalogId, storefront) }
                                .getOrNull()
                                ?.takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }
                        remote.id to (loaded ?: runCatching { client.playlistTracks(remote.id) }.getOrNull())
                    }
                }.awaitAll().forEach { (id, loaded) ->
                    if (loaded != null) tracks[id] = loaded
                }
            }
            read += chunk.size
            onProgress(read, needsRead.size)
        }

        remotes.forEach { remote ->
            val playlistId = localId(remote.id)
            // a playlist Apple says has not changed since the last import: no read, no write
            if (remote.id !in tracks && metas[playlistId]?.lastModified == remote.lastModified) {
                PlaylistOwner.remember(dao, playlistId, remote, accountLabel)
                songs += runCatching { database.playlistSongMaps(playlistId).size }.getOrDefault(0)
                unchanged++
                return@forEach
            }
            // a playlist whose read failed keeps what it has: an empty list here would empty it
            val loaded = tracks[remote.id] ?: run {
                skipped++
                return@forEach
            }
            val chosen = CoverOverrides.get(context, CoverOverrides.Kind.PLAYLIST, playlistId)
            val artwork = chosen ?: AppleCovers.sized(remote.artworkUrl)
                ?: AppleCovers.sized(loaded.firstOrNull()?.artworkUrl)
            // owner is metadata about the playlist resource, so it is remembered even when the
            // membership did not change
            val meta = PlaylistOwner.remember(dao, playlistId, remote, accountLabel)

            // Apple is the source of truth, except for what was added here from another source: a
            // song picked from YouTube has no Apple id, and re-importing must not delete it
            val existing = database.playlistSongMaps(playlistId).sortedBy { it.position }
            val kept = existing.map { it.songId }.filterNot { it.startsWith("AM_") }

            // resolve first: a playlist entry with no song row under it would be a gap in the list,
            // and the row check is a suspend call the transaction below cannot make
            val songsInOrder = mutableListOf<String>()
            val missing = mutableListOf<RemoteSong>()
            for (track in loaded) {
                val catalogId = track.catalogId
                if (catalogId == null) {
                    // an upload: no catalogue id, so there is nothing to fetch it by
                    skipped++
                    continue
                }
                val songId = "AM_$catalogId"
                if (songId !in known) {
                    // A followed Apple playlist holds songs that are not in the library. Dropping
                    // them left the playlist short of what Apple shows, so they come in as songs
                    // that are not in the library (inLibrary stays null) - the playlist is whole,
                    // the library is not inflated.
                    missing += track
                    known += songId
                }
                songsInOrder += songId
            }

            val merged = songsInOrder + kept.filter { it !in songsInOrder }
            val signature = merged.joinToString(",")
            val current = runCatching { database.playlist(playlistId).first() }.getOrNull()?.playlist
            val alreadyRight = current != null &&
                current.name == remote.name &&
                current.thumbnailUrl == artwork &&
                current.isEditable == remote.canEdit &&
                current.remoteSongCount == merged.size &&
                existing.map { it.songId } == merged

            // nothing about this playlist moved: keep the rows, skip the link write, say so
            if (alreadyRight) {
                dao.link(playlistId)?.let { link ->
                    if (link.lastSignature != signature) dao.saveLink(link.copy(lastSignature = signature))
                }
                ApplePlaylistPush.seed(playlistId, signature)
                songs += merged.size
                unchanged++
                return@forEach
            }

            if (missing.isNotEmpty()) {
                extra += runCatching { writeMissing(database, missing) }.getOrDefault(0)
            }

            runCatching {
                // the row, whether it is new or a re-import: insert is IGNORE, so an existing
                // playlist would keep yesterday's name and never become visible in the sidebar
                val row = PlaylistEntity(
                    id = playlistId,
                    // the playlist is Apple's: its name is theirs to change, not ours
                    name = remote.name,
                    browseId = remote.id,
                    createdAt = current?.createdAt ?: LocalDateTime.now(),
                    lastUpdateTime = LocalDateTime.now(),
                    // the app lists only playlists it has in its own sidebar
                    bookmarkedAt = current?.bookmarkedAt ?: LocalDateTime.now(),
                    isEditable = remote.canEdit,
                    remoteSongCount = merged.size,
                    thumbnailUrl = artwork,
                )
                database.transaction {
                    insert(row)
                    update(row)
                    clearPlaylist(playlistId)
                    merged.forEachIndexed { position, songId ->
                        insert(PlaylistSongMap(playlistId = playlistId, songId = songId, position = position))
                    }
                }
                // linked on the way in: the guard needs the link before anything can be pushed back
                // the signature rides along, so the watcher does not mistake an import for an edit
                dao.saveLink(
                    AppleLinkEntity(
                        playlistId = playlistId,
                        remoteId = remote.id,
                        remoteName = remote.name,
                        lastSignature = signature,
                    ),
                )
                songs += merged.size
                // it came from Apple, so it already matches: no push on the way out
                ApplePlaylistPush.seed(playlistId, signature)
            }
        }

        runCatching { dao.saveState(AppleStateEntity(IMPORT_VERSION_KEY, IMPORT_VERSION)) }
        return Result(
            playlists = remotes.size,
            songs = songs,
            skipped = skipped,
            unchanged = unchanged,
            outsideTheLibrary = extra,
        )
    }

    /**
     * Songs a playlist holds that the library does not - a followed Apple playlist is full of them.
     * Written as songs, with `inLibrary` left null: the playlist is whole and the library's own
     * lists, counts and artists do not grow to include music nobody saved.
     */
    private suspend fun writeMissing(database: MusicDatabase, songs: List<RemoteSong>): Int {
        val artistIds = HashMap<String, String>()
        var written = 0
        database.transaction {
            songs.forEach { song ->
                val catalogId = song.catalogId ?: return@forEach
                val songId = "AM_$catalogId"
                val artistName = song.artist.ifBlank { "Unknown artist" }
                val artistId = artistIds.getOrPut(artistName) {
                    database.artistByName(artistName)?.id ?: ArtistEntity.generateArtistId()
                }
                insert(
                    SongEntity(
                        id = songId,
                        title = song.title,
                        duration = ((song.durationMs ?: 0L) / 1000L).toInt(),
                        thumbnailUrl = AppleCovers.sized(song.artworkUrl),
                        albumName = song.album,
                        // no albumId: the Albums shelf is the library's albums, and this is not one
                        inLibrary = null,
                    ),
                )
                // insert is IGNORE: an artist that already exists keeps what it has
                insert(ArtistEntity(id = artistId, name = artistName))
                insert(SongArtistMap(songId = songId, artistId = artistId, position = 0))
                written++
            }
        }
        return written
    }
}
