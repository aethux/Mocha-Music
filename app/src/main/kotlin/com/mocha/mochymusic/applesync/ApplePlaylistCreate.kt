// ApplePlaylistCreate.kt
//
// A playlist made here, made on Apple too. Apple has no way to make a playlist that only exists in
// one place, and until now a local playlist could only be *linked* to one of the account's own —
// which meant the first push of a new playlist would replace somebody else's list.
//
// So this makes a new one: [AppleWrite.CREATE_PLAYLIST] through [AppleGuard] like every other write,
// the id out of Apple's answer, the link row saved so the watcher may push to it, then the songs sent
// by the same [ApplePlaylistWrites.push] every linked playlist uses. Nothing is claimed until the
// playlist is read back from Apple.

package com.mocha.mochymusic.applesync

import android.content.Context
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.db.AppleAuditEntity
import com.mocha.mochymusic.applesync.db.AppleLinkEntity
import com.mocha.mochymusic.applesync.db.AppleOwnedEntity
import com.mocha.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mocha.mochymusic.ui.component.TopToaster
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object ApplePlaylistCreate {

    private const val Host = "https://amp-api.music.apple.com"

    /** A create lands a moment later, so the read that proves it is asked more than once. */
    private const val ReadBackAttempts = 4
    private const val ReadBackGapMs = 1_500L

    private data class Answer(val status: Int, val body: String)

    data class Result(
        /** The Apple playlist this one now is. Null when nothing was made. */
        val remoteId: String? = null,
        /** How many songs the follow-up push put in it. */
        val pushed: Int = 0,
        /** Apple holds the playlist and its songs, read back rather than assumed. */
        val verified: Boolean = false,
        /** null when it went through; a sentence for the user when it did not */
        val refusal: String? = null,
    )

    /**
     * Makes [playlistId] on Apple, links it, and sends its songs.
     *
     * An already-linked playlist is left alone and answers with its link: creating a second copy of
     * the same list is never what someone meant by tapping the row again.
     */
    suspend fun create(
        context: Context,
        playlistId: String,
        quiet: Boolean = false,
    ): Result = withContext(Dispatchers.IO) { run(context, playlistId, quiet) }

    private suspend fun run(context: Context, playlistId: String, quiet: Boolean): Result {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppleSongWritesEntryPoint::class.java,
        )
        val dao = entry.dao()
        val database = entry.database()
        val tokens = entry.tokens()

        runCatching { dao.link(playlistId) }.getOrNull()?.let { existing ->
            return Result(remoteId = existing.remoteId, refusal = null)
        }

        val pair = tokens.read()
            ?: return Result(refusal = context.getString(R.string.apple_playlist_sign_in))
        val name = runCatching { database.playlistEntity(playlistId)?.name }.getOrNull()
            ?: return Result(refusal = context.getString(R.string.apple_playlist_create_failed))

        val decision = AppleGuard.decide(
            op = AppleOp(write = AppleWrite.CREATE_PLAYLIST, playlistName = name),
            context = AppleGuardContext(
                writesEnabled = AppleWritePrefs.enabled(context),
                // nothing of the account's is being touched: this is a new object, and the epoch is
                // about what was already there
                epoch = emptySet(),
                owned = emptySet(),
                linkedPlaylists = emptySet(),
            ),
        )
        if (decision is AppleDecision.Refused) {
            dao.audit(
                AppleAuditEntity(
                    at = System.currentTimeMillis(),
                    op = "PLAYLIST_CREATE",
                    target = playlistId,
                    decision = "REFUSE",
                    reason = decision.reason.name,
                ),
            )
            val said = context.getString(R.string.apple_playlist_create_refused, decision.reason.name)
            if (!quiet) TopToaster.error(said)
            return Result(refusal = said)
        }

        val body = JSONObject()
            .put("attributes", JSONObject().put("name", name))
            .toString()
        val answer = runCatching { post(pair.headers, body) }
            .getOrElse { error -> Answer(-1, error.message ?: "no answer") }
        val id = Regex(""""id"\s*:\s*"(p\.[A-Za-z0-9]+)"""")
            .find(answer.body)?.groupValues?.get(1)
        val accepted = answer.status in 200..299 && id != null

        // A create is accepted before it exists, so the only evidence is Apple's own list carrying
        // it. `playlistTrackTotal` is not that evidence: it reads `meta.total`, which an empty new
        // playlist does not have, so a good create came back unverified.
        var readBack = false
        if (accepted) {
            val client = AppleClient(pair)
            for (attempt in 0 until ReadBackAttempts) {
                val listed = runCatching { client.playlists(maxPages = 1) }.getOrNull()
                if (listed?.any { it.id == id } == true) {
                    readBack = true
                    break
                }
                if (attempt < ReadBackAttempts - 1) delay(ReadBackGapMs)
            }
        }

        val reason = when {
            !accepted -> PlaylistBody.sentence(answer.status, answer.body)
            !readBack -> context.getString(R.string.apple_playlist_create_unverified)
            else -> null
        }
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = "PLAYLIST_CREATE",
                target = id ?: playlistId,
                decision = if (accepted) "ALLOW" else "ERROR",
                httpStatus = answer.status.takeIf { it > 0 },
                verified = readBack,
                reason = reason,
            ),
        )

        if (id == null) {
            val said = reason ?: context.getString(R.string.apple_playlist_create_failed)
            if (!quiet) TopToaster.error(said)
            return Result(refusal = said)
        }

        // recorded before the songs go up: a push that fails still leaves a linked playlist whose id
        // is known, which is what makes the next tap a retry rather than a second playlist
        dao.own(AppleOwnedEntity(AppleKind.PLAYLIST.name, id, System.currentTimeMillis(), name))
        dao.saveLink(
            AppleLinkEntity(
                playlistId = playlistId,
                remoteId = id,
                remoteName = name,
            ),
        )

        val sent = ApplePlaylistWrites.push(context, playlistId, quiet = true)

        if (!quiet) {
            when {
                sent.refusal != null -> TopToaster.error(sent.refusal!!)
                else -> TopToaster.show(
                    text = context.getString(R.string.apple_playlist_created, name),
                    iconRes = R.drawable.sync,
                )
            }
        }

        return Result(
            remoteId = id,
            pushed = sent.pushed,
            verified = readBack && sent.verified,
            refusal = sent.refusal,
        )
    }

    private suspend fun post(headers: Map<String, String>, body: String): Answer = withContext(Dispatchers.IO) {
        val connection = (URL("$Host/v1/me/library/playlists").openConnection()
            as HttpURLConnection).apply {
            requestMethod = "POST"
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
