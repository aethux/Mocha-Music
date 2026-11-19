// ApplePlaylistWrites.kt
//
// Mochy's playlist, sent to Apple. Apple has no "remove one song" call - the ordered list is the
// truth - so every change here is one PUT of the whole list, resolved from each local song's Apple
// library id. A song with no Apple copy is left out and counted, never guessed at.
//
// Three rules, in the order they run:
//  - Apple decides who may change the playlist: its own `canEdit` is asked first, because a playlist
//    the account only follows answers 500 to a rewrite rather than saying it is not allowed;
//  - every request is described to [AppleGuard] before it is built;
//  - a 2xx is not evidence. The playlist is read back and compared, because a replacement Apple did
//    not make still answers 204.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mochyy.mochymusic.ui.component.TopToaster
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ApplePlaylistWrites {

    private const val Host = "https://amp-api.music.apple.com"

    data class Result(
        val pushed: Int,
        val skipped: Int,
        /** Apple's list is the one that was sent, read back and compared. */
        val verified: Boolean = false,
        /** null when it went through; a sentence for the user when it did not */
        val refusal: String? = null,
    )

    private data class Answer(val status: Int, val body: String)

    /**
     * Sends the playlist's songs, in its order, to the Apple playlist it is linked to.
     *
     * Runs off the caller's thread: the playlist's songs are read with Room's plain (non-suspend)
     * queries, which it refuses to answer on the main thread - a menu tap calls this from there.
     *
     * [quiet] is for the watcher: a sync that runs on its own should not talk over the person
     * using the app. A refusal is still returned, so a caller can decide what to say.
     */
    suspend fun push(context: Context, playlistId: String, quiet: Boolean = true): Result =
        withContext(Dispatchers.IO) { send(context, playlistId, quiet) }

    private suspend fun send(context: Context, playlistId: String, quiet: Boolean): Result {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppleSongWritesEntryPoint::class.java,
        )
        val dao = entry.dao()
        val database = entry.database()
        val tokens = entry.tokens()

        val link = dao.link(playlistId)
            ?: return Result(0, 0, refusal = context.getString(R.string.apple_playlist_unlinked))
        val pair = tokens.read()
            ?: return Result(0, 0, refusal = context.getString(R.string.apple_playlist_sign_in))

        // Apple's own answer about who may change this playlist. The replica is the engine's copy of
        // it; the playlist row is what the import wrote. Only when neither knows is it left open.
        val editable = runCatching { dao.playlist(link.remoteId)?.canEdit }.getOrNull()
            ?: runCatching { database.playlistEntity(playlistId)?.isEditable }.getOrNull()
            ?: true

        // the songs that can be sent, in order: Apple takes its own library ids and nothing else
        val maps = database.playlistSongMaps(playlistId, 0)
        val signature = maps.joinToString(",") { it.songId }
        val libraryIds = mutableListOf<String>()
        var skipped = 0
        for (map in maps) {
            val libraryId = runCatching { dao.track(map.songId)?.libraryId }.getOrNull()
                ?: runCatching {
                    val catalogId = dao.track(map.songId)?.catalogId
                    catalogId?.let { dao.librarySong(it)?.libraryId }
                }.getOrNull()
            if (libraryId == null) skipped++ else libraryIds += libraryId
        }
        if (libraryIds.isEmpty()) {
            return Result(0, skipped, refusal = context.getString(R.string.apple_playlist_nothing_to_send))
        }

        val decision = AppleGuard.decide(
            op = AppleOp(
                write = AppleWrite.REPLACE_TRACKS,
                playlistId = playlistId,
                playlistName = link.remoteName,
                playlistEditable = editable,
                targets = listOf(AppleRef(AppleKind.PLAYLIST, link.remoteId)),
            ),
            context = AppleGuardContext(
                writesEnabled = AppleWritePrefs.enabled(context),
                // a linked playlist is the permission: the epoch still refuses deletes and renames
                epoch = emptySet(),
                owned = emptySet(),
                linkedPlaylists = setOf(playlistId),
            ),
        )
        if (decision is AppleDecision.Refused) {
            dao.audit(
                AppleAuditEntity(
                    at = System.currentTimeMillis(),
                    op = "PLAYLIST_REPLACE",
                    target = link.remoteId,
                    decision = "REFUSE",
                    reason = decision.reason.name,
                ),
            )
            // a playlist the account only follows is not a failure to retry: say what it is
            val said = if (decision.reason == AppleRefusal.PLAYLIST_NOT_EDITABLE) {
                context.getString(R.string.apple_playlist_not_editable)
            } else {
                context.getString(R.string.apple_playlist_refused, decision.reason.name)
            }
            dao.saveLink(link.copy(lastError = said, lastSignature = signature))
            if (!quiet) TopToaster.error(said)
            return Result(0, skipped, refusal = said)
        }

        val answer = runCatching { put(link.remoteId, pair.headers, PlaylistBody.of(libraryIds)) }
            .getOrElse { error -> Answer(-1, error.message ?: "no answer") }
        val sent = answer.status in 200..299

        // the read that proves it: a 204 is also what Apple says to a call it ignored
        val readBack = if (sent) {
            runCatching { AppleClient(pair).playlistTracks(link.remoteId).map { it.libraryId } }.getOrNull()
        } else {
            null
        }
        val verified = readBack != null && PlaylistBody.sameOrder(libraryIds, readBack)

        val reason = when {
            !sent -> PlaylistBody.sentence(answer.status, answer.body)
            !verified -> PlaylistBody.describeMismatch(libraryIds, readBack)
            else -> null
        }
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = "PLAYLIST_REPLACE",
                target = link.remoteId,
                decision = if (sent) "ALLOW" else "ERROR",
                httpStatus = answer.status.takeIf { it > 0 },
                verified = verified,
                reason = reason,
            ),
        )
        // the link remembers, so the menu can say what happened last time without asking Apple - and
        // so a restart is not mistaken for an edit
        dao.saveLink(
            link.copy(
                lastSyncAt = if (verified) System.currentTimeMillis() else link.lastSyncAt,
                lastError = reason,
                lastSignature = signature,
            ),
        )

        if (!quiet) {
            when {
                verified -> TopToaster.show(
                    text = context.resources.getQuantityString(
                        R.plurals.apple_playlist_pushed,
                        libraryIds.size,
                        libraryIds.size,
                    ),
                    iconRes = R.drawable.sync,
                )
                sent -> TopToaster.error(context.getString(R.string.apple_playlist_unverified))
                else -> TopToaster.error(context.getString(R.string.apple_playlist_push_failed))
            }
        }

        return if (sent) {
            Result(libraryIds.size, skipped, verified)
        } else {
            Result(0, skipped, refusal = context.getString(R.string.apple_playlist_push_failed))
        }
    }

    /** The one request, with Apple's answer kept so a refusal can be repeated back to the user. */
    private suspend fun put(
        remoteId: String,
        headers: Map<String, String>,
        body: String,
    ): Answer = withContext(Dispatchers.IO) {
        val connection = (URL("$Host/v1/me/library/playlists/$remoteId/tracks").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "PUT"
            connectTimeout = 15_000
            readTimeout = 20_000
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            setRequestProperty("Content-Type", "application/json")
            doOutput = true
        }
        try {
            connection.outputStream.use { it.write(body.toByteArray()) }
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().decodeToString() }
                .orEmpty()
            Answer(status, text)
        } finally {
            connection.disconnect()
        }
    }
}
