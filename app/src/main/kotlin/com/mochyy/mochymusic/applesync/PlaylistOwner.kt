// PlaylistOwner.kt
//
// Who a playlist belongs to.
//
// Apple's library playlist resource does not carry an owner name: the attributes it sends are
// canDelete, canEdit, dateAdded, description, hasCatalog, hasCollaboration, isPublic,
// lastModifiedDate, name, playParams (checked against the live account). So there is no honest way to
// put a person's name on someone else's playlist, and pretending there is would be worse than
// saying what the resource does say: whether the account made the playlist (canDelete) or is holding
// someone else's, and whether it can be edited by more than one person (hasCollaboration).
//
// `curatorName` is still read when a resource has one - a catalogue playlist does - so the field is
// used when Apple gives it, and the fallback is the account's own label.

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.db.PlaylistMetaEntity

object PlaylistOwner {

    /** What to call the owner, or null when the row should keep whatever it has. */
    fun resolve(remote: RemotePlaylist, accountLabel: String?): String? =
        remote.curator?.trim()?.takeIf { it.isNotEmpty() }
            ?: accountLabel?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (remote.canDelete) "You" else "Shared with you"

    /** True when the playlist is somebody else's rather than the account's own. */
    fun isForeign(remote: RemotePlaylist): Boolean = !remote.canDelete && remote.curator == null

    /**
     * Stores the name read from the playlist's catalogue counterpart, and marks it as looked up even
     * when Apple had no name - so an account of playlists costs twenty-six requests once, not every
     * refresh.
     */
    suspend fun record(dao: AppleSyncDao, playlistId: String, owner: PlaylistCatalogue?) {
        val current = runCatching { dao.playlistMeta(playlistId) }.getOrNull() ?: return
        // the name and the catalogue id are learned in the same read, but not in the same version:
        // an install that already has the name still needs the id
        if (current.ownerChecked && current.catalogId != null) return
        val name = owner?.ownerName?.trim()?.takeIf { it.isNotEmpty() } ?: current.ownerName
        runCatching {
            dao.savePlaylistMeta(
                current.copy(
                    ownerName = name,
                    catalogId = owner?.catalogId ?: current.catalogId,
                    ownerChecked = true,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    /** Writes the row only when something in it changed: a refresh with nothing new writes nothing. */
    suspend fun remember(
        dao: AppleSyncDao,
        playlistId: String,
        remote: RemotePlaylist,
        accountLabel: String?,
    ): PlaylistMetaEntity {
        val current = runCatching { dao.playlistMeta(playlistId) }.getOrNull()
        // a name already read from Apple wins over the fallback this function would invent
        val owner = current?.takeIf { it.ownerChecked }?.ownerName
            ?: remote.curator?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolve(remote, accountLabel)
        if (current != null &&
            current.ownerName == owner &&
            current.collaborative == remote.collaborative &&
            current.lastModified == remote.lastModified &&
            current.canDelete == remote.canDelete
        ) {
            return current
        }
        val row = (current ?: PlaylistMetaEntity(playlistId = playlistId)).copy(
            ownerName = owner,
            collaborative = remote.collaborative,
            lastModified = remote.lastModified,
            canDelete = remote.canDelete,
            updatedAt = System.currentTimeMillis(),
        )
        runCatching { dao.savePlaylistMeta(row) }
        return row
    }
}
