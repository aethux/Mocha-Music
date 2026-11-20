// AppleClient.kt

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.importer.account.AccountHttp
import com.mocha.mochymusic.importer.account.AccountImportException
import com.mocha.mochymusic.importer.array
import com.mocha.mochymusic.importer.idString
import com.mocha.mochymusic.importer.long
import com.mocha.mochymusic.importer.obj
import com.mocha.mochymusic.importer.string
import kotlinx.serialization.json.JsonObject

/**
 * Reads only, on purpose.
 *
 * There is no write method here and there will not be one: writes go through the page transport, and
 * a REST door would be exactly the thing the guard exists to keep closed. Requests reuse
 * [AccountHttp], which already waits out 429s, marks a 401 as "sign in again" and turns transport
 * failures into something a user can read.
 */
class AppleClient(private val tokens: AppleTokens.Pair) {

    suspend fun storefront(): String =
        page("/v1/me/storefront")?.let(AppleLibrary::storefront)
            ?: throw AppleSyncException("Apple didn't say which storefront this account is in.")

    /**
     * Library playlists, Apple's own "Favorite Songs" included: reading it is fine, writing never.
     *
     * `include=catalog` is what makes the owner visible: the library resource has no owner field,
     * but its catalogue counterpart carries `curatorName`, which is the name the web player shows.
     */
    suspend fun playlists(maxPages: Int = 40, onPage: (Int) -> Unit = {}): List<RemotePlaylist> =
        all(
            first = "/v1/me/library/playlists?limit=100&include=catalog",
            maxPages = maxPages,
            parse = AppleLibrary::playlists,
            onPage = onPage,
            // the next page is a bare offset: without this every page after the first has no owner
            keep = "&include=catalog",
        )

    /**
     * Who owns one playlist. The library *list* does not carry it - `include=catalog` there returns
     * the relationship without attributes - but the single resource does, and its catalogue
     * counterpart carries `curatorName`, the same name the web player prints.
     */
    suspend fun playlistCurator(playlistId: String): PlaylistCatalogue? = runCatching {
        val page = page("/v1/me/library/playlists/$playlistId?include=catalog") ?: return null
        AppleLibrary.playlistOwner(page)
    }.getOrNull()

    /** A catalogue playlist's tracks: every one, in Apple's order, not just the library's. */
    suspend fun catalogPlaylistTracks(catalogId: String, storefront: String, maxPages: Int = 40): List<RemoteSong> =
        all(
            first = "/v1/catalog/$storefront/playlists/$catalogId/tracks?limit=100",
            maxPages = maxPages,
            parse = { page -> AppleLibrary.catalogTracks(page) },
        )

    /**
     * How many tracks Apple itself says a playlist holds, before any paging of ours: `meta.total`
     * on the library copy, `attributes.trackCount` on the catalogue counterpart.
     *
     * Imported counts were compared only against themselves, so a read that stopped early looked
     * exactly like a playlist that is that short.
     */
    suspend fun playlistTrackTotal(playlistId: String): Int? =
        runCatching {
            page("/v1/me/library/playlists/$playlistId/tracks?limit=1")
                ?.get("meta").obj()?.long("total")?.toInt()
        }.getOrNull()

    suspend fun catalogPlaylistTotal(catalogId: String, storefront: String): Int? =
        runCatching {
            page("/v1/catalog/$storefront/playlists/$catalogId")
                ?.get("data").array()?.firstOrNull()?.obj()
                ?.get("attributes").obj()?.long("trackCount")?.toInt()
        }.getOrNull()

    /**
     * The shapes a library playlist's entries can be asked for, counted, so a short import can be
     * told apart from a short playlist: `tracks` is the endpoint the import uses, `inline` is the
     * resource's own relationship, `rel` is the relationship endpoint.
     */
    suspend fun probePlaylistShapes(playlistId: String): String {
        val tracks = runCatching { page("/v1/me/library/playlists/$playlistId/tracks?limit=100") }.getOrNull()
        val inline = runCatching { page("/v1/me/library/playlists/$playlistId?include=tracks") }.getOrNull()
        val relationship = runCatching {
            page("/v1/me/library/playlists/$playlistId/relationships/tracks?limit=100")
        }.getOrNull()
        val inlineTracks = inline?.get("data").array()?.firstOrNull()?.obj()
            ?.get("relationships").obj()?.get("tracks").obj()
        return "tracks " + tracks?.get("data").array()?.size + "/" + tracks?.get("meta").obj()?.long("total") +
            " · inline " + inlineTracks?.get("data").array()?.size + "/" + inlineTracks?.get("meta").obj()?.long("total") +
            " · relationship " + relationship?.get("data").array()?.size + "/" + relationship?.get("meta").obj()?.long("total")
    }

    /**
     * Every song in the library. A 9k library is about 90 pages, so the default cap is generous.
     *
     * [onPage] reports after each hundred, which is what keeps a long read honest: a counter that
     * only changes when the whole thing finishes is indistinguishable from a stuck one.
     */
    suspend fun librarySongs(
        maxPages: Int = 400,
        onPage: (Int) -> Unit = {},
    ): List<RemoteSong> = all(
        first = "/v1/me/library/songs?limit=100&include=albums",
        maxPages = maxPages,
        parse = AppleLibrary::songs,
        onPage = onPage,
        // Apple's next page is an offset URL without the include: without this only the first
        // hundred songs come back with their album
        keep = "&include=albums",
    )

    /**
     * The library's own search: one request that finds a song by name.
     *
     * It exists because the library list is not ordered by date added, so the newest page is the
     * wrong place to look for something that was just put in, and a full read is fifteen requests.
     */
    suspend fun librarySearch(term: String, limit: Int = 25): List<RemoteSong> {
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        val page = page("/v1/me/library/search?term=$query&types=library-songs&limit=$limit") ?: return emptyList()
        val songs = page["results"].obj()?.get("library-songs").obj() ?: return emptyList()
        return AppleLibrary.songs(songs)
    }

    /**
     * The first page of the library only.
     *
     * Used to watch for a song that was just added: a full read is fifteen round trips and made a
     * half-minute poll look like a hang. Newly added songs land at the top of this list.
     */
    suspend fun libraryHead(): List<RemoteSong> =
        page("/v1/me/library/songs?limit=100")?.let(AppleLibrary::songs).orEmpty()

    /** Catalog ids for a search term, best match first. Reads only; used to pick sandbox songs. */
    suspend fun searchSongs(term: String, limit: Int = 10): List<String> {
        val storefront = storefront()
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        val page = page("/v1/catalog/$storefront/search?term=$query&types=songs&limit=$limit") ?: return emptyList()
        val results = page["results"].obj() ?: return emptyList()
        return results["songs"].obj()?.get("data").array().orEmpty().mapNotNull { it.obj()?.idString("id") }
    }

    /**
     * Catalogue rows for a search term, with everything the matcher needs to score them.
     *
     * Separate from [searchSongs], which returns bare ids: a match is decided on title, artist,
     * album and length, and none of those exist until the rows themselves are read.
     */
    suspend fun searchCatalog(term: String, limit: Int = 15): List<CatalogSong> {
        val storefront = storefront()
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        val page = page("/v1/catalog/$storefront/search?term=$query&types=songs&limit=$limit") ?: return emptyList()
        val songs = page["results"].obj()?.get("songs").obj() ?: return emptyList()
        return AppleLibrary.catalogSongs(songs)
    }

    /** Every kind of result in one request: songs, artists, albums, playlists, videos. */
    suspend fun searchAll(term: String, limit: Int = 12): JsonObject? {
        val storefront = storefront()
        val query = java.net.URLEncoder.encode(term, "UTF-8")
        return page(
            "/v1/catalog/$storefront/search?term=$query" +
                "&types=songs,artists,albums,playlists,music-videos&limit=$limit",
        )
    }

    /**
     * A catalogue song's covers: its still artwork and, when the album has one, the motion loop.
     *
     * One request, `include=albums`, because the animated artwork belongs to the album and asking
     * for the song alone would never see it.
     */
    suspend fun catalogCover(id: String): com.mocha.mochymusic.applesync.AppleCovers.AppleCover? {
        val storefront = storefront()
        val song = runCatching {
            page("/v1/catalog/$storefront/songs/$id?extend=editorialVideo&include=albums")
        }.getOrNull() ?: return null
        val row = song["data"].array().orEmpty().firstOrNull()?.obj() ?: return null
        val attributes = row["attributes"].obj()
        val still = attributes?.get("artwork").obj()?.string("url")
        // some songs carry their own motion loop
        val songMotion = attributes?.get("editorialVideo").obj()
            ?.get("motionDetailSquare").obj()?.string("video")
        if (songMotion != null) return com.mocha.mochymusic.applesync.AppleCovers.AppleCover(songMotion, still)

        // otherwise it belongs to the album, which needs its own request with the extension
        val albumId = row["relationships"].obj()?.get("albums").obj()
            ?.get("data").array().orEmpty().firstOrNull()?.obj()?.idString("id")
        if (albumId == null) return com.mocha.mochymusic.applesync.AppleCovers.AppleCover(null, still)
        val album = runCatching {
            page("/v1/catalog/$storefront/albums/$albumId?extend=editorialVideo")
        }.getOrNull()
        val albumAttributes = album?.get("data").array().orEmpty().firstOrNull()?.obj()?.get("attributes").obj()
        val motion = albumAttributes?.get("editorialVideo").obj()
            ?.get("motionDetailSquare").obj()?.string("video")
            ?: albumAttributes?.get("editorialVideo").obj()?.get("motionDetailTall").obj()?.string("video")
        return com.mocha.mochymusic.applesync.AppleCovers.AppleCover(motion, still)
    }

    /** A catalogue song's name and artist, so a write can say what it is about to touch. */
    suspend fun catalogSong(id: String): Pair<String, String>? {
        val storefront = storefront()
        val page = runCatching { page("/v1/catalog/$storefront/songs/$id") }.getOrNull() ?: return null
        val row = page["data"].array().orEmpty().firstOrNull()?.obj() ?: return null
        val attributes = row["attributes"].obj() ?: return null
        val title = attributes.string("name") ?: return null
        return title to (attributes.string("artistName") ?: "")
    }

    /**
     * Favourites for many songs in one go: 1 is loved, -1 is disliked, anything absent has no rating.
     *
     * Ratings live on api.music.apple.com while the library lives on amp-api, and the endpoint takes
     * a comma separated list, so a whole library costs a handful of requests instead of one per song.
     */
    suspend fun ratings(catalogIds: List<String>): Map<String, Long> {
        if (catalogIds.isEmpty()) return emptyMap()
        val out = mutableMapOf<String, Long>()
        for (chunk in catalogIds.chunked(100)) {
            val page = page("https://api.music.apple.com/v1/me/ratings/songs?ids=${chunk.joinToString(",")}")
                ?: continue
            for (item in page["data"].array()) {
                val obj = item.obj() ?: continue
                val id = obj.idString("id") ?: continue
                obj["attributes"].obj()?.long("value")?.let { out[id] = it }
            }
        }
        return out
    }

    /**
     * The account's rating for a library song: 1 loved, -1 disliked, null when it has none.
     *
     * A 404 is the normal answer for "no rating", not a failure, so it becomes null rather than an
     * exception.
     */
    suspend fun rating(songId: String): Long? {
        val page = runCatching { page("/v1/me/ratings/songs/$songId") }.getOrNull() ?: return null
        val row = page["data"].array().orEmpty().firstOrNull()?.obj() ?: return null
        return row["attributes"].obj()?.long("value")
    }

    /** One linked playlist's entries, in order, with each entry's library id. */
    suspend fun playlistTracks(playlistId: String, maxPages: Int = 400): List<RemoteSong> =
        all("/v1/me/library/playlists/$playlistId/tracks?limit=100", maxPages, AppleLibrary::songs)

    private suspend fun <T> all(
        first: String,
        maxPages: Int,
        parse: (JsonObject) -> List<T>,
        onPage: (Int) -> Unit = {},
        /**
         * A query parameter the first page asked for and every page after it must keep. Apple's
         * `next` is a bare offset URL: without this, page two of the library read comes back with
         * no `relationships`, and 1,300 songs arrive with no album.
         */
        keep: String = "",
    ): List<T> {
        val out = mutableListOf<T>()
        var url = first
        var pages = 0
        val kept = keep.removePrefix("&").removePrefix("?").substringBefore("&")
        val key = kept.substringBefore('=')
        while (pages++ < maxPages) {
            val page = page(url) ?: break
            out += parse(page)
            // reported per page, not per call: a fifteen-page read that only speaks at the end is a
            // counter stuck on zero for half a minute
            onPage(out.size)
            val next = AppleLibrary.nextPage(page) ?: break
            url = Host + next + if (key.isNotEmpty() && "?$key=" !in next && "&$key=" !in next) keep else ""
        }
        return out
    }

    private suspend fun page(url: String): JsonObject? =
        try {
            AccountHttp.getJson(
                url = if (url.startsWith("http")) url else Host + url,
                headers = tokens.headers,
                service = "Apple Music",
                notFoundAsNull = true,
            )
        } catch (e: AccountImportException) {
            throw AppleSyncException(e.message ?: "Apple Music answered with an error.", signedOut = e.signedOut)
        }

    private companion object {
        const val Host = "https://amp-api.music.apple.com"
    }
}
