// AppleLibraryPull.kt

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.applesync.db.AppleLibrarySongEntity
import com.mocha.mochymusic.applesync.db.ApplePlaylistEntity
import com.mocha.mochymusic.applesync.db.AppleSyncDao

/**
 * Reads the Apple library — songs, favourites and playlists — into the replica.
 *
 * One direction and no writes to Apple, so it is safe to repeat: both replicas are replaced wholesale
 * rather than merged, and a song removed on Apple disappears here on the next pull instead of
 * lingering as a ghost.
 *
 * Progress goes through [AppleSyncStatus] rather than a callback, because the library list, the
 * settings page and the refresh button all show it and would otherwise each keep their own counter.
 */
class AppleLibraryPull(
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens,
) {

    data class Result(val songs: Int, val playlists: Int, val remote: List<RemoteSong>)

    suspend fun run(): Result {
        AppleSyncStatus.start("Reading your Apple Music library…")
        try {
            val pair = tokens.read() ?: throw AppleSyncException("Sign in to Apple Music first.")
            val client = AppleClient(pair)

            val songs = client.librarySongs { read ->
                AppleSyncStatus.update(phase = "Reading your library…", songs = read, totalSongs = 0)
            }
            AppleSyncStatus.update(phase = "Reading your favourites…", songs = songs.size, totalSongs = songs.size)

            // favourites are a rating, and they come in one call per hundred songs
            val rated = runCatching { client.ratings(songs.mapNotNull { it.catalogId }) }
                .getOrDefault(emptyMap())

            val now = System.currentTimeMillis()
            val rows = songs.mapNotNull { song ->
                val catalogId = song.catalogId ?: return@mapNotNull null
                AppleLibrarySongEntity(
                    catalogId = catalogId,
                    libraryId = song.libraryId,
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    durationMs = song.durationMs,
                    isrc = song.isrc,
                    addedAt = null,
                    pulledAt = now,
                    rated = rated[catalogId] == 1L,
                )
            }
            AppleSyncStatus.update(
                phase = "Saving ${rows.size} songs…",
                songs = rows.size,
                totalSongs = songs.size,
            )
            dao.replaceLibrarySongs(rows)

            AppleSyncStatus.update(phase = "Reading your playlists…", songs = rows.size, totalSongs = rows.size)
            val playlists = client.playlists { read ->
                AppleSyncStatus.update(
                    phase = "Reading your playlists…",
                    songs = rows.size,
                    playlists = read,
                    totalSongs = rows.size,
                )
            }
            AppleSyncStatus.update(
                phase = "Saving ${playlists.size} playlists…",
                songs = rows.size,
                playlists = playlists.size,
                totalSongs = rows.size,
                totalPlaylists = playlists.size,
            )
            dao.replacePlaylists(
                playlists.map {
                    ApplePlaylistEntity(
                        remoteId = it.id,
                        name = it.name,
                        trackCount = 0,
                        canEdit = it.canEdit,
                        pulledAt = now,
                    )
                },
            )

            AppleSyncStatus.setSynced(dao.syncedSongIds().toSet())
            // the songs go back to the caller: the import needs the same list, and reading it twice
            // is what looked like a sync that finished and then started over
            return Result(songs = rows.size, playlists = playlists.size, remote = songs)
        } catch (e: Exception) {
            AppleSyncStatus.fail(e.message ?: "Couldn't reach Apple Music")
            throw e
        }
    }
}
