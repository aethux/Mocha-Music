// ApplePlaylistLinker.kt

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.applesync.db.AppleLinkEntity
import com.mocha.mochymusic.applesync.db.AppleSyncDao

/**
 * Connects a local playlist to one on Apple, and answers the only question that matters before a
 * sync: how much of one already exists in the other.
 *
 * Linking is the permission. The guard refuses writes to any playlist it has not been told about, so
 * a link row is exactly the user saying "this one, and not the others". Unlinking takes the
 * permission away again without touching anything on Apple.
 */
class ApplePlaylistLinker(
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens,
) {

    data class Compare(
        val localCount: Int,
        val remoteCount: Int,
        val matched: Int,
        val localOnly: Int,
        val remoteOnly: Int,
        val unmapped: Int,
    )

    suspend fun linked(playlistId: String): AppleLinkEntity? = dao.link(playlistId)

    /** Every playlist on Apple, so one can be picked by name. Reads only. */
    suspend fun candidates(): List<RemotePlaylist> = AppleClient(tokenPair()).playlists()

    suspend fun link(playlistId: String, remote: RemotePlaylist) {
        dao.saveLink(
            AppleLinkEntity(
                playlistId = playlistId,
                remoteId = remote.id,
                remoteName = remote.name,
            ),
        )
    }

    suspend fun unlink(playlistId: String) = dao.unlink(playlistId)

    private suspend fun tokenPair(): AppleTokens.Pair =
        tokens.read() ?: throw AppleSyncException("Sign in to Apple Music first.")

    /**
     * What the two lists have in common.
     *
     * A local song counts as matched when this fork already knows its catalogue id; songs added in
     * Mocha have no such mapping yet, and they are reported separately rather than guessed at.
     */
    suspend fun compare(playlistId: String, remoteId: String, localSongIds: List<String>): Compare {
        val remote = AppleClient(tokenPair()).playlistTracks(remoteId).mapNotNull { it.catalogId }.toSet()
        val mapped = localSongIds.mapNotNull { dao.track(it)?.catalogId }.toSet()
        return Compare(
            localCount = localSongIds.size,
            remoteCount = remote.size,
            matched = mapped.intersect(remote).size,
            localOnly = mapped.minus(remote).size,
            remoteOnly = remote.minus(mapped).size,
            unmapped = localSongIds.size - localSongIds.mapNotNull { dao.track(it)?.catalogId }.size,
        )
    }
}
