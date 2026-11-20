// AppleModels.kt

package com.mocha.mochymusic.applesync

/**
 * What the sync knows about the account, in the app's own words rather than Apple's JSON.
 *
 * [libraryId] is the per-account id a playlist write needs. [catalogId] is the global one, and is
 * null for anything the user uploaded or iTunes matched, which is exactly the set that cannot be
 * pushed into a playlist.
 */
data class RemoteSong(
    val libraryId: String,
    val catalogId: String?,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val isrc: String?,
    /** Apple's artwork template, with {w}x{h} placeholders; null when the song has no art. */
    val artworkUrl: String? = null,
    /**
     * The album this song belongs to on Apple, as the row's `relationships.albums` said. It is the
     * album's catalogue id when Apple gives one, its own id otherwise - either is stable per album,
     * which is all the album row needs.
     */
    val albumId: String? = null,
    val albumArtworkUrl: String? = null,
    val albumTrackCount: Int? = null,
    val albumYear: Int? = null,
    /** Where the song sits on its album, so an album page lists it in Apple's order. */
    val trackNumber: Int? = null,
)

/** A song on Apple's catalogue. The id here is the one a rating or a library add is keyed by. */
data class CatalogSong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long?,
    val isrc: String?,
    /** Apple's template with {w}x{h} in it; null when the song has no art. */
    val artworkUrl: String? = null,
)

data class RemotePlaylist(
    val id: String,
    val name: String,
    val canEdit: Boolean,
    /** Apple's own playlist artwork, with {w}x{h} in it; null when it has none. */
    val artworkUrl: String? = null,
    /** Who Apple says made it: an editorial curator, or another person on a shared playlist. */
    val curator: String? = null,
    /** True when the playlist is open to edits from more than its owner. */
    val collaborative: Boolean = false,
    /** Apple's lastModifiedDate: a refresh reads the tracks only when this moved. */
    val lastModified: String? = null,
    /** canDelete: false means the playlist belongs to someone else and is in the library by follow. */
    val canDelete: Boolean = false,
)

/**
 * The result of one read-only audit: what the account holds, and whether anything that existed when
 * the epoch was taken is missing now.
 */
data class AppleAuditReport(
    val storefront: String,
    val playlists: Int,
    val songs: Int,
    val epochSize: Int,
    val addedSinceEpoch: List<String>,
    val removedSinceEpoch: List<String>,
    val trusted: Boolean,
)

/** A failure worth showing to the user; [signedOut] means the fix is to sign in again. */
class AppleSyncException(message: String, val signedOut: Boolean = false) : Exception(message)

/** The epoch and ownership tables key on "kind:id", because both halves together identify an object. */
internal fun AppleRef.key(): String = "${kind.name}:$id"

internal fun appleRefFromKey(key: String): AppleRef? {
    val kind = AppleKind.entries.firstOrNull { key.startsWith("${it.name}:") } ?: return null
    val id = key.removePrefix("${kind.name}:")
    return if (id.isBlank()) null else AppleRef(kind, id)
}
