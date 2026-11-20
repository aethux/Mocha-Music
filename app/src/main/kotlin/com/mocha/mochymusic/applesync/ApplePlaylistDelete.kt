// ApplePlaylistDelete.kt
//
// The other half of [ApplePlaylistCreate]: a playlist this app made on Apple can be taken off Apple
// again, by hand, from the playlist's own menu.
//
// It is the narrowest possible permission. [AppleGuard] refuses a delete of anything that was in the
// library before the sync began, and refuses anything this app does not own - so a playlist that was
// made here can be removed, and one the account has had for years cannot, whatever the menu offers.
// The local playlist is not touched: it stops being a copy of anything, which is what unlinking means.

package com.mocha.mochymusic.applesync

import android.content.Context
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.db.AppleAuditEntity
import com.mocha.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mocha.mochymusic.ui.component.TopToaster
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ApplePlaylistDelete {

    private const val Host = "https://amp-api.music.apple.com"

    /** A delete lands a moment later, so the read that proves it is asked more than once. */
    private const val ReadBackAttempts = 4
    private const val ReadBackGapMs = 1_500L

    data class Result(
        val deleted: Boolean = false,
        val verified: Boolean = false,
        /** null when it went through; a sentence for the user when it did not */
        val refusal: String? = null,
        /** the name that was deleted, for the toast */
        val name: String? = null,
    )

    suspend fun delete(
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
        val tokens = entry.tokens()

        val link = runCatching { dao.link(playlistId) }.getOrNull()
            ?: return Result(refusal = context.getString(R.string.apple_playlist_unlinked))
        val pair = tokens.read()
            ?: return Result(refusal = context.getString(R.string.apple_playlist_sign_in))

        val ref = AppleRef(AppleKind.PLAYLIST, link.remoteId)
        val owned = runCatching { dao.ownedContains(AppleKind.PLAYLIST.name, link.remoteId) > 0 }
            .getOrDefault(false)
        val decision = AppleGuard.decide(
            op = AppleOp(
                write = AppleWrite.DELETE_PLAYLIST,
                playlistId = playlistId,
                playlistName = link.remoteName,
                targets = listOf(ref),
            ),
            context = AppleGuardContext(
                writesEnabled = AppleWritePrefs.enabled(context),
                // nothing in the epoch is deletable, and the owned table is where a playlist this app
                // made is recorded - both are the promise that this only removes what it created
                epoch = emptySet(),
                owned = if (owned) setOf(ref) else emptySet(),
                linkedPlaylists = setOf(playlistId),
            ),
        )
        if (decision is AppleDecision.Refused) {
            dao.audit(
                AppleAuditEntity(
                    at = System.currentTimeMillis(),
                    op = "PLAYLIST_DELETE",
                    target = link.remoteId,
                    decision = "REFUSE",
                    reason = decision.reason.name,
                ),
            )
            val said = when (decision.reason) {
                AppleRefusal.NOT_OWNED ->
                    context.getString(R.string.apple_playlist_delete_not_ours, link.remoteName.orEmpty())
                else -> context.getString(R.string.apple_playlist_delete_refused, decision.reason.name)
            }
            if (!quiet) TopToaster.error(said)
            return Result(refusal = said, name = link.remoteName)
        }

        val status = runCatching { request(pair.headers, link.remoteId) }.getOrElse { -1 }
        val accepted = status in 200..299

        // A 204 is also what Apple answers to a call it ignored, so the proof is the playlist no
        // longer being in Apple's list.
        var gone = false
        if (accepted) {
            val client = AppleClient(pair)
            for (attempt in 0 until ReadBackAttempts) {
                val listed = runCatching { client.playlists(maxPages = 1) }.getOrNull()
                if (listed?.none { it.id == link.remoteId } == true) {
                    gone = true
                    break
                }
                if (attempt < ReadBackAttempts - 1) delay(ReadBackGapMs)
            }
        }

        val reason = when {
            !accepted -> context.getString(R.string.apple_playlist_delete_refused, "HTTP $status")
            !gone -> context.getString(R.string.apple_playlist_delete_unverified)
            else -> null
        }
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = "PLAYLIST_DELETE",
                target = link.remoteId,
                decision = if (accepted) "ALLOW" else "ERROR",
                httpStatus = status.takeIf { it > 0 },
                verified = gone,
                reason = reason,
            ),
        )

        if (gone) {
            // the local playlist stays exactly as it is; it simply stops being a copy of anything
            runCatching { dao.unlink(playlistId) }
            runCatching { dao.disown(AppleKind.PLAYLIST.name, link.remoteId) }
        }

        if (!quiet) {
            when {
                reason != null -> TopToaster.error(reason)
                else -> TopToaster.show(
                    text = context.getString(R.string.apple_playlist_deleted, link.remoteName.orEmpty()),
                    iconRes = R.drawable.delete,
                )
            }
        }

        return Result(
            deleted = accepted,
            verified = gone,
            refusal = reason,
            name = link.remoteName,
        )
    }

    private suspend fun request(headers: Map<String, String>, remoteId: String): Int =
        withContext(Dispatchers.IO) {
            val connection = (URL("$Host/v1/me/library/playlists/$remoteId").openConnection()
                as HttpURLConnection).apply {
                requestMethod = "DELETE"
                connectTimeout = 15_000
                readTimeout = 20_000
                headers.forEach { (name, value) -> setRequestProperty(name, value) }
            }
            try {
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }
}
