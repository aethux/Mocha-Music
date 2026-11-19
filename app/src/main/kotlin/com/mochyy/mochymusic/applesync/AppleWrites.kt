// AppleWrites.kt

package com.mochyy.mochymusic.applesync

import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.db.AppleLinkEntity
import com.mochyy.mochymusic.applesync.db.AppleOwnedEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.page.ApplePage
import com.mochyy.mochymusic.applesync.page.ApplePageScripts
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The sandbox: the first thing in this fork that changes anything on Apple, and the only shape it is
 * allowed to take for now.
 *
 * It creates a playlist of its own, records it as owned, puts two songs in it, takes one out with the
 * replace call, deletes the playlist, and removes the library rows the exercise created. Every step
 * goes through [AppleGuard] first, every step is read back, and every step lands in the audit log.
 * Anything the user already had is in the epoch, so the guard refuses it before a request is built.
 *
 * It advances one stage at a time. [next] runs the stage it is sitting on and moves on, which is what
 * lets someone open the Apple Music app between steps and watch the change actually land; [run] is
 * the same thing in a loop, so the stepped and automatic paths cannot drift apart.
 */
class AppleWrites(
    private val page: ApplePage,
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens.Pair,
    private val writesEnabled: Boolean,
) : AppleStepper {

    enum class Stage(val label: String) {
        CREATE("create the playlist"),
        ADD("add two songs"),
        REPLACE("remove one song"),
        DELETE("delete the playlist"),
        CLEANUP("remove the library rows"),
        DONE(""),
    }


    private val client = AppleClient(tokens)
    private val lines = mutableListOf<String>()
    private val ownedLibraryIds = mutableListOf<String>()
    private var picks: List<String> = emptyList()
    private var libraryIds: List<String> = emptyList()
    private var sandboxPlaylist: String? = null
    private var localId: String? = null

    var stage: Stage = Stage.CREATE
        private set

    override val finished: Boolean get() = stage == Stage.DONE

    /** What the next tap does, in words, for the button. */
    override val nextLabel: String get() = if (finished) "" else stage.label

    /** Runs the stage it is standing on, and only that stage. */
    override suspend fun next(): StepReport {
        if (finished) return StepReport(ok = true, lines = lines.toList())
        return try {
            when (stage) {
                Stage.CREATE -> createPlaylist()
                Stage.ADD -> addSongs()
                Stage.REPLACE -> removeOneSong()
                Stage.DELETE -> deletePlaylist()
                Stage.CLEANUP -> cleanupLibrary()
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

    /** Every stage in order. */
    suspend fun run(): StepReport {
        while (!finished) {
            val report = next()
            if (!report.ok) return report
        }
        return StepReport(ok = true, lines = lines.toList())
    }

    private fun note(line: String) {
        lines += line
    }

    // ---- stages ---------------------------------------------------------------------------

    private suspend fun createPlaylist() {
        sweepLeftovers()
        val name = "Mochy sandbox " + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())

        decide(AppleOp(AppleWrite.CREATE_PLAYLIST, playlistName = name))
        val description = "temporary; Mochy deletes this again"
        val body = """{"attributes":{"name":"$name","description":"$description"}}"""
        val (status, response) = call("POST", "/v1/me/library/playlists", body)
        if (status !in 200..299) throw AppleSyncException("Apple refused the playlist (HTTP $status)")

        // the id comes out of the raw body and is recorded before anything else can fail, so a run
        // that dies on the next line still knows what it made and the following run can remove it
        val id = Regex(""""id"\s*:\s*"(p\.[A-Za-z0-9]+)"""").find(response)?.groupValues?.get(1)
            ?: throw AppleSyncException("Apple accepted the playlist but the answer carried no id")
        sandboxPlaylist = id
        val local = "sandbox:$id"
        localId = local
        dao.own(AppleOwnedEntity(AppleKind.PLAYLIST.name, id, System.currentTimeMillis(), name))
        dao.saveLink(AppleLinkEntity(playlistId = local, remoteId = id, remoteName = name))
        audit("SANDBOX_CREATE", id, status, verified = true)
        note("created $id")
        note("check Apple Music: the playlist $name should be there")
    }

    private suspend fun addSongs() {
        val id = sandboxPlaylist ?: throw AppleSyncException("No sandbox playlist; start again")
        val local = localId ?: "sandbox:$id"

        // two catalog songs that are not already in the library, so the cleanup at the end cannot
        // take a row that was there before this run
        val library = client.librarySongs().mapNotNull { it.catalogId }.toSet()
        picks = client.searchSongs("bohemian rhapsody queen").filter { it !in library }.take(2)
        if (picks.size < 2) throw AppleSyncException("Could not find two songs that are not already in the library")

        decide(
            AppleOp(
                AppleWrite.ADD_TRACKS,
                playlistId = local,
                targets = picks.map { AppleRef(AppleKind.CATALOG_SONG, it) },
            ),
        )
        val body = """{"data":[${picks.joinToString(",") { """{"id":"$it","type":"songs"}""" }}]}"""
        val (status, _) = call("POST", "/v1/me/library/playlists/$id/tracks", body)
        if (status !in 200..299) throw AppleSyncException("Apple refused the tracks (HTTP $status)")

        val entries = awaitEntries(id, 2)
        if (entries.size < 2) throw AppleSyncException("Only ${entries.size} track(s) landed in the playlist")
        libraryIds = entries.map { it.libraryId }
        libraryIds.forEach {
            dao.own(AppleOwnedEntity(AppleKind.LIBRARY_SONG.name, it, System.currentTimeMillis(), "sandbox track"))
        }
        ownedLibraryIds += libraryIds
        audit("SANDBOX_ADD", id, status, verified = true)
        note("added ${entries.size} tracks")
    }

    private suspend fun removeOneSong() {
        val id = sandboxPlaylist ?: throw AppleSyncException("No sandbox playlist; start again")
        val local = localId ?: "sandbox:$id"
        if (libraryIds.size < 2) throw AppleSyncException("The playlist does not have two entries to remove from")

        val keep = listOf(libraryIds.first())
        decide(
            AppleOp(
                AppleWrite.REPLACE_TRACKS,
                playlistId = local,
                targets = keep.map { AppleRef(AppleKind.LIBRARY_SONG, it) },
            ),
        )
        val body = """{"data":[${keep.joinToString(",") { """{"id":"$it","type":"library-songs"}""" }}]}"""
        val (status, _) = call("PUT", "/v1/me/library/playlists/$id/tracks", body)
        val after = awaitEntries(id, 1)
        if (after.size != 1) throw AppleSyncException("Replace left ${after.size} tracks, expected 1")
        audit("SANDBOX_REPLACE", id, status, verified = true)
        note("replaced the list, ${libraryIds.size} to ${after.size}")
    }

    private suspend fun deletePlaylist() {
        val id = sandboxPlaylist ?: throw AppleSyncException("No sandbox playlist; start again")
        val local = localId ?: "sandbox:$id"

        decide(
            AppleOp(
                AppleWrite.DELETE_PLAYLIST,
                playlistId = local,
                targets = listOf(AppleRef(AppleKind.PLAYLIST, id)),
            ),
        )
        val (status, _) = call("DELETE", "/v1/me/library/playlists/$id")
        val gone = runCatching { client.playlistTracks(id) }.getOrDefault(emptyList()).isEmpty()
        audit("SANDBOX_DELETE", id, status, verified = gone)
        if (!gone) throw AppleSyncException("The playlist is still there after delete")
        dao.disown(AppleKind.PLAYLIST.name, id)
        dao.unlink(local)
        sandboxPlaylist = null
        localId = null
        note("deleted the playlist")
    }

    /** Library rows the run created are the only ones it may delete. */
    private suspend fun cleanupLibrary() {
        val ids = ownedLibraryIds.toList()
        if (ids.isNotEmpty()) {
            for (id in ids) {
                decide(
                    AppleOp(
                        AppleWrite.REMOVE_FROM_LIBRARY,
                        playlistId = null,
                        targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, id)),
                    ),
                )
                val (status, _) = call("DELETE", "/v1/me/library/songs/$id")
                audit("SANDBOX_CLEAN_LIBRARY", id, status, verified = true)
                dao.disown(AppleKind.LIBRARY_SONG.name, id)
                ownedLibraryIds.remove(id)
            }
            // one read answers for all of them; asking per song meant reading the library per song
            val stillThere = runCatching { client.librarySongs() }.getOrDefault(emptyList())
                .map { it.libraryId }.toSet()
            for (id in ids) note("removed library row $id: ${if (id in stillThere) "still there" else "gone"}")
        }
        note("done")
    }

    private suspend fun abortCleanup() {
        sandboxPlaylist?.let { id ->
            runCatching { call("DELETE", "/v1/me/library/playlists/$id") }
            note("cleanup: asked Apple to delete $id")
            sandboxPlaylist = null
            localId = null
        }
        runCatching { cleanupLibrary() }
    }

    /**
     * Playlists from a run that died before recording ownership. The name is this feature's own and
     * the guard still checks the target against the epoch, so this can only ever remove something
     * that did not exist when the baseline was taken.
     */
    private suspend fun sweepLeftovers() {
        val stale = runCatching { client.playlists() }.getOrDefault(emptyList())
            .filter { it.name.startsWith(AppleGuard.SandboxPrefix) }
        for (playlist in stale) {
            decide(
                AppleOp(
                    AppleWrite.DELETE_PLAYLIST,
                    playlistName = playlist.name,
                    targets = listOf(AppleRef(AppleKind.PLAYLIST, playlist.id)),
                ),
            )
            val (status, _) = call("DELETE", "/v1/me/library/playlists/${playlist.id}")
            audit("SANDBOX_SWEEP", playlist.id, status, verified = true)
            dao.disown(AppleKind.PLAYLIST.name, playlist.id)
            note("swept leftover ${playlist.id}")
        }
    }

    private suspend fun awaitEntries(playlistId: String, want: Int): List<RemoteSong> {
        var entries = emptyList<RemoteSong>()
        repeat(6) {
            entries = runCatching { client.playlistTracks(playlistId) }.getOrDefault(emptyList())
            if (entries.size >= want) return entries
            kotlinx.coroutines.delay(1_000)
        }
        return entries
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

    private suspend fun call(method: String, path: String, body: String? = null): Pair<Int, String> {
        val raw = page.eval(ApplePageScripts.request(method, Host + path, body))
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
        const val Host = "https://amp-api.music.apple.com"
    }
}
