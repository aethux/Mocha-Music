// AppleLibrary.kt

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.importer.array
import com.mocha.mochymusic.importer.idString
import com.mocha.mochymusic.importer.long
import com.mocha.mochymusic.importer.obj
import com.mocha.mochymusic.importer.string
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Apple's JSON, read leniently. Pure functions, no HTTP: the client below fetches, and the tests
 * feed these recorded shapes.
 *
 * Apple's library rows carry the account's own id in `id` ("i.…") and the global catalogue id inside
 * `attributes.playParams.catalogId`, which is absent for uploads and matched files.
 */
internal object AppleLibrary {

    fun storefront(page: JsonObject): String? = page["data"].array().firstOrNull()?.obj()?.string("id")

    fun playlists(page: JsonObject): List<RemotePlaylist> = page["data"].array().mapNotNull { item ->

        val obj = item.obj() ?: return@mapNotNull null
        val id = obj.idString("id") ?: return@mapNotNull null
        val attributes = obj["attributes"].obj()
        RemotePlaylist(
            id = id,
            name = attributes?.string("name") ?: "Untitled playlist",
            canEdit = attributes?.bool("canEdit") ?: true,
            // Apple leaves this out for a playlist it has no artwork for; the import then falls
            // back to the first track rather than writing a null over a cover already there
            artworkUrl = attributes?.get("artwork").obj()?.string("url"),
            // the library resource has no owner; the catalogue counterpart it includes does
            curator = obj["relationships"].obj()
                ?.get("catalog").obj()
                ?.get("data").array()
                ?.firstOrNull()?.obj()
                ?.get("attributes").obj()
                ?.string("curatorName"),
            // Apple has shipped this under more than one name across revisions of the API
            collaborative = attributes?.bool("isCollaborative") == true ||
                attributes?.bool("hasCollaboration") == true ||
                obj["relationships"].obj()?.get("collaborators") != null,
            lastModified = attributes?.string("lastModifiedDate"),
            canDelete = attributes?.bool("canDelete") ?: false,
        )
    }

    fun playlistOwner(page: JsonObject): PlaylistCatalogue {
        val catalog = page["data"].array().firstOrNull()?.obj()
            ?.get("relationships").obj()?.get("catalog").obj()
            ?.get("data").array()?.firstOrNull()?.obj()
        val attributes = catalog?.get("attributes").obj()
        return PlaylistCatalogue(
            ownerName = attributes?.string("curatorName"),
            catalogId = catalog?.idString("id"),
        )
    }

    /**
     * A catalogue playlist's tracks. Same fields as a catalogue song, plus its position in the
     * playlist, which the playlist's own order needs.
     */
    fun catalogTracks(page: JsonObject): List<RemoteSong> = page["data"].array().mapNotNull { item ->
        val obj = item.obj() ?: return@mapNotNull null
        val id = obj.idString("id") ?: return@mapNotNull null
        val attributes = obj["attributes"].obj()
        RemoteSong(
            catalogId = id,
            // a catalogue track has no library id: the song may not be in the library at all
            libraryId = "",
            isrc = attributes?.string("isrc"),
            title = attributes?.string("name") ?: return@mapNotNull null,
            artist = attributes?.string("artistName").orEmpty(),
            album = attributes?.string("albumName"),
            albumId = attributes?.get("playParams").obj()?.string("id"),
            albumArtworkUrl = attributes?.get("artwork").obj()?.string("url"),
            albumTrackCount = null,
            albumYear = null,
            trackNumber = attributes?.long("trackNumber")?.toInt(),
            durationMs = attributes?.long("durationInMillis"),
            artworkUrl = attributes?.get("artwork").obj()?.string("url"),
        )
    }

    /**
     * Catalogue rows, which carry the same fields as library rows except that the id *is* the
     * catalogue id rather than a library id, and there is no playParams to dig it out of.
     */
    fun catalogSongs(page: JsonObject): List<CatalogSong> = page["data"].array().mapNotNull { item ->
        val obj = item.obj() ?: return@mapNotNull null
        val id = obj.idString("id") ?: return@mapNotNull null
        val attributes = obj["attributes"].obj()
        CatalogSong(
            id = id,
            title = attributes?.string("name") ?: return@mapNotNull null,
            artist = attributes?.string("artistName").orEmpty(),
            album = attributes?.string("albumName"),
            durationMs = attributes?.long("durationInMillis"),
            isrc = attributes?.string("isrc"),
            artworkUrl = attributes?.get("artwork").obj()?.string("url"),
        )
    }

    fun songs(page: JsonObject): List<RemoteSong> = page["data"].array().mapNotNull { item ->
        val obj = item.obj() ?: return@mapNotNull null
        val libraryId = obj.idString("id") ?: return@mapNotNull null
        val attributes = obj["attributes"].obj()
        // `?include=albums` puts the album in relationships; a row without one keeps a null album
        val albumObj = obj["relationships"].obj()
            ?.get("albums").obj()
            ?.get("data").array()
            ?.firstOrNull()?.obj()
        val albumAttributes = albumObj?.get("attributes").obj()
        RemoteSong(
            libraryId = libraryId,
            catalogId = attributes?.get("playParams").obj()?.string("catalogId"),
            title = attributes?.string("name") ?: return@mapNotNull null,
            artist = attributes?.string("artistName").orEmpty(),
            album = attributes?.string("albumName"),
            durationMs = attributes?.long("durationInMillis"),
            isrc = attributes?.string("isrc"),
            artworkUrl = attributes?.get("artwork").obj()?.string("url"),
            // the catalogue id when the album has one, so the same album is one row everywhere
            albumId = albumObj?.let { row ->
                row["attributes"].obj()?.get("playParams").obj()?.string("catalogId")
                    ?: row.idString("id")
            },
            albumArtworkUrl = albumAttributes?.get("artwork").obj()?.string("url"),
            albumTrackCount = albumAttributes?.long("trackCount")?.toInt(),
            albumYear = albumAttributes?.string("releaseDate")?.take(4)?.toIntOrNull(),
            trackNumber = attributes?.long("trackNumber")?.toInt(),
        )
    }

    /** Apple sends booleans as real JSON booleans, which the lenient string reader would drop. */
    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.contentOrNull?.toBooleanStrictOrNull()

    /** Apple pages with a path relative to the host, and no `next` on the last page. */
    fun nextPage(page: JsonObject): String? =
        page.string("next")?.takeIf { it.startsWith("/v1/") }
}
