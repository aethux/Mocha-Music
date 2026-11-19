// AppleLibraryWrites.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.db.AppleOwnedEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.page.ApplePage
import com.mochyy.mochymusic.applesync.page.ApplePageScripts
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * The library half of the sandbox, in the order the library itself is learned: add a song, take it
 * out again, and only then the favourite, which is a rating hanging off a library row.
 *
 * One stage per call, same as the playlist run, with the same guard in front of every stage. The
 * songs come from a Queen search and are not chosen by reading the library first: the guard already
 * refuses to delete anything that predates it, so a pre-existing pick is caught at the delete rather
 * than paid for with fifteen requests at the start.
 */
class AppleLibraryWrites(
    private val page: ApplePage,
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens.Pair,
    private val writesEnabled: Boolean,
) : AppleStepper {

    enum class Stage(val label: String) {
        ADD("add a song to the library"),
        REMOVE("remove it from the library"),
        ADD_FAV("add a second song, for favourites"),
        FAVOURITE("love it"),
        UNFAVOURITE("unlove it"),
        REMOVE_FAV("remove it from the library"),
        DONE(""),
    }

    private val client = AppleClient(tokens)
    private val lines = mutableListOf<String>()

    /** What was added, and the library id once Apple's library shows it. */
    private data class Pick(val catalogId: String, val label: String, var libraryId: String? = null)

    private var picks: List<String> = emptyList()
    private var librarySong: Pick? = null
    private var favouriteSong: Pick? = null

    /** Called with a line while a stage is still running, so a slow poll is never silent. */
    var onProgress: ((String) -> Unit)? = null

    var stage: Stage = Stage.ADD
        private set

    override val finished: Boolean get() = stage == Stage.DONE
    override val nextLabel: String get() = if (finished) "" else stage.label

    override suspend fun next(): StepReport {
        if (finished) return StepReport(ok = true, lines = lines.toList())
        return try {
            when (stage) {
                Stage.ADD -> librarySong = addToLibrary(twoPicks().first())
                Stage.REMOVE -> {
                    removeOne(librarySong)
                    librarySong = null
                }
                Stage.ADD_FAV -> favouriteSong = addToLibrary(twoPicks()[1])
                Stage.FAVOURITE -> love()
                Stage.UNFAVOURITE -> unlove()
                Stage.REMOVE_FAV -> {
                    removeOne(favouriteSong)
                    favouriteSong = null
                }
                Stage.DONE -> Unit
            }
            stage = Stage.entries[stage.ordinal + 1]
            StepReport(ok = true, lines = lines.toList())
        } catch (e: Exception) {
            note("stopped at ${stage.label}: ${e.message ?: e::class.java.simpleName}")
            stage = Stage.DONE
            runCatching { abortCleanup() }
            StepReport(ok = false, lines = lines.toList())
        }
    }

    private fun note(line: String) {
        lines += line
    }

    private suspend fun twoPicks(): List<String> {
        if (picks.size >= 2) return picks
        onProgress?.invoke("asking Apple for Queen songs…")
        picks = client.searchSongs("queen", limit = 10).distinct()
        if (picks.size < 2) throw AppleSyncException("Apple returned fewer than two Queen songs")
        return picks
    }

    // ---- stages ---------------------------------------------------------------------------

    private suspend fun addToLibrary(catalogId: String): Pick {
        val label = client.catalogSong(catalogId)?.let { "${it.first} — ${it.second}" } ?: catalogId
        note("adding: $label")
        decide(AppleOp(AppleWrite.ADD_TO_LIBRARY, targets = listOf(AppleRef(AppleKind.CATALOG_SONG, catalogId))))
        // the add takes the id as a query parameter and no body; a data array is answered with 400
        val (status, _) = call("POST", "/v1/me/library?ids[songs]=$catalogId", null)
        audit("LIBRARY_ADD", catalogId, status, verified = false)
        if (status !in 200..299) throw AppleSyncException("Apple refused the add of $label (HTTP $status)")
        note("asked Apple to add it (HTTP $status)")
        return Pick(catalogId, label)
    }

    private suspend fun removeOne(pick: Pick?) {
        val song = pick ?: throw AppleSyncException("Nothing was added in this run; start again")
        val libraryId = libraryIdFor(song)
            ?: throw AppleSyncException("Apple has not listed ${song.label} yet; run this step again in a moment")
        decide(
            AppleOp(AppleWrite.REMOVE_FROM_LIBRARY, targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, libraryId))),
        )
        val (status, _) = call("DELETE", "/v1/me/library/songs/$libraryId")
        if (status !in 200..299) throw AppleSyncException("Apple refused removing ${song.label} (HTTP $status)")
        audit("LIBRARY_REMOVE", libraryId, status, verified = false)
        dao.disown(AppleKind.LIBRARY_SONG.name, libraryId)
        note("asked Apple to remove ${song.label} (HTTP $status)")
        note("check Apple Music: it should be gone")
    }

    private suspend fun love() {
        val pick = favouriteSong ?: throw AppleSyncException("Nothing was added for favourites; start again")
        val libraryId = libraryIdFor(pick)
            ?: throw AppleSyncException("Apple has not listed ${pick.label} yet; run this step again in a moment")
        decide(AppleOp(AppleWrite.SET_RATING, targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, libraryId))))

        // the rating body was the one shape in this feature nobody had proven, so every candidate is
        // tried and every answer is printed. Guessing again would cost another round trip with the
        // user and produce less information than one tap does here.
        // Apple's own documentation and every working client agree on this one: ratings live on
        // api.music.apple.com (not amp-api), the body type is "rating" singular, and a song's
        // catalogue id and library id share the same rating, so the catalogue id is the safer key.
        val ids = buildList {
            pick.catalogId.takeIf { it.isNotBlank() }?.let { add("catalogue" to it) }
            add("library" to libraryId)
        }
        val body = """{"type":"rating","attributes":{"value":1}}"""
        for ((kind, id) in ids) {
            val (status, response) = call("PUT", "/v1/me/ratings/songs/$id", body, RatingsHost)
            note("PUT $kind id $id → HTTP $status ${response.trim().take(160)}")
            if (status in 200..299) {
                audit("RATING_SET", id, status, verified = false)
                note("loved ${pick.label}; check the heart")
                return
            }
        }
        throw AppleSyncException("Apple refused both ids; the answers are above")
    }

    private suspend fun unlove() {
        val pick = favouriteSong ?: throw AppleSyncException("Nothing was favourited; start again")
        val libraryId = libraryIdFor(pick)
            ?: throw AppleSyncException("No library row for ${pick.label}; run this step again in a moment")
        decide(AppleOp(AppleWrite.CLEAR_RATING, targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, libraryId))))

        // the rating is shared between the catalogue and library id, so either clears it
        val ids = listOfNotNull(pick.catalogId.takeIf { it.isNotBlank() }, libraryId)
        for (id in ids) {
            val (status, response) = call("DELETE", "/v1/me/ratings/songs/$id", null, RatingsHost)
            note("DELETE id $id → HTTP $status ${response.trim().take(120)}")
            if (status in 200..299) {
                audit("RATING_CLEARED", id, status, verified = false)
                note("unloved ${pick.label}; check the heart is gone")
                return
            }
        }
        throw AppleSyncException("Apple refused clearing the rating")
    }

    // ---- lookup ---------------------------------------------------------------------------

    /**
     * The library row for a pick, or null if Apple has not listed it yet.
     *
     * Search first: one request, and it does not care where in the list the row landed. A full read
     * is the last resort, because the library list is not sorted by date added and its first page is
     * therefore the wrong place to look for something that was just put in.
     */
    private suspend fun libraryIdFor(pick: Pick): String? {
        pick.libraryId?.let { return it }
        val title = pick.label.substringBefore(" — ").trim()

        // a rating needs the *library* id, the one Apple prefixes with "i."; the library search can
        // answer with catalogue-shaped rows, and a catalogue id in that path is a 400 "Invalid Path
        // Value", which is exactly what it did
        repeat(3) {
            val rows = runCatching { client.librarySearch(title) }.getOrNull().orEmpty()
                .filter { it.libraryId.startsWith("i.") }
            val found = rows.firstOrNull { it.catalogId == pick.catalogId }
                ?: rows.firstOrNull { it.title.equals(title, ignoreCase = true) }
            if (found != null) return own(found.libraryId, pick)
            kotlinx.coroutines.delay(1_500)
        }

        return runCatching { client.librarySongs() }.getOrNull().orEmpty()
            .firstOrNull { it.catalogId == pick.catalogId && it.libraryId.startsWith("i.") }
            ?.let { own(it.libraryId, pick) }
    }

    private suspend fun own(libraryId: String, pick: Pick): String {
        pick.libraryId = libraryId
        dao.own(AppleOwnedEntity(AppleKind.LIBRARY_SONG.name, libraryId, System.currentTimeMillis(), pick.label))
        return libraryId
    }

    private suspend fun abortCleanup() {
        for (pick in listOfNotNull(favouriteSong, librarySong)) {
            val libraryId = pick.libraryId ?: runCatching { libraryIdFor(pick) }.getOrNull()
            if (libraryId == null) {
                note("cleanup: no library row for ${pick.label}; leaving it to you")
                continue
            }
            runCatching { call("DELETE", "/v1/me/ratings/songs/$libraryId") }
            runCatching { call("DELETE", "/v1/me/library/songs/$libraryId") }
            note("cleanup: asked Apple to remove ${pick.label}")
        }
        favouriteSong = null
        librarySong = null
    }

    private suspend fun decide(op: AppleOp) {
        val context = AppleGuardContext(
            writesEnabled = writesEnabled,
            epoch = dao.epoch().map { AppleRef(AppleKind.valueOf(it.kind), it.remoteId) }.toSet(),
            owned = buildSet {
                dao.owned(AppleKind.PLAYLIST.name).forEach { add(AppleRef(AppleKind.PLAYLIST, it.remoteId)) }
                dao.owned(AppleKind.LIBRARY_SONG.name).forEach { add(AppleRef(AppleKind.LIBRARY_SONG, it.remoteId)) }
            },
            linkedPlaylists = dao.links().first().map { it.playlistId }.toSet(),
        )
        when (val decision = AppleGuard.decide(op, context)) {
            is AppleDecision.Allowed -> Unit
            is AppleDecision.Refused -> {
                audit(op.write.name, op.targets.firstOrNull()?.id, null, verified = false, reason = decision.reason.name)
                throw AppleSyncException("Refused: ${decision.reason}")
            }
        }
    }

    private suspend fun audit(op: String, target: String?, httpStatus: Int?, verified: Boolean, reason: String? = null) {
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = op,
                target = target,
                decision = if (reason == null) "ALLOW" else "REFUSE",
                reason = reason,
                httpStatus = httpStatus,
                verified = verified,
            ),
        )
    }

    private suspend fun call(
        method: String,
        path: String,
        body: String? = null,
        host: String = Host,
    ): Pair<Int, String> {
        val raw = page.eval(ApplePageScripts.request(method, host + path, body))
            ?: throw AppleSyncException("The page didn't answer the $method (it may still be loading)")
        val json = runCatching { JSONObject(raw) }.getOrElse {
            throw AppleSyncException("The page answered with something unreadable")
        }
        json.optString("error").takeIf { it.isNotBlank() && it != "null" }?.let {
            throw AppleSyncException("The page reported: $it")
        }
        return json.optInt("status", -1) to json.optString("body")
    }

    private companion object {
        /** Library writes and favourites live on different hosts; this is not a typo. */
        const val Host = "https://amp-api.music.apple.com"
        const val RatingsHost = "https://api.music.apple.com"
    }
}
