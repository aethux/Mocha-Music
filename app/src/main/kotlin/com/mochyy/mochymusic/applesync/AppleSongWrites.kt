// AppleSongWrites.kt

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.db.AppleAuditEntity
import com.mochyy.mochymusic.applesync.db.AppleOwnedEntity
import com.mochyy.mochymusic.applesync.db.AppleRematchEntity
import com.mochyy.mochymusic.applesync.db.AppleTrackEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint
import com.mochyy.mochymusic.db.entities.Song
import com.mochyy.mochymusic.ui.component.TopToaster
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * One tap, one write: the heart and the library button, pushed to Apple as they are pressed.
 *
 * Both use the token pair captured at sign-in, which is the same pair every read already uses, so
 * there is no page to be alive and no session to lose. The hosts differ and that is not a typo:
 * library writes live on `amp-api.music.apple.com`, ratings on `api.music.apple.com`.
 *
 * Two rules from the fork hold here, unchanged:
 *  - every write is a decision, put to [AppleGuard] before a request is built;
 *  - every write is confirmed by a read afterwards, because Apple answers 202 and 204 whether or not
 *    anything happened.
 *
 * A rating is the user's own mark on a song, not a change to what the library holds: it removes
 * nothing and cannot cascade, so the epoch does not refuse it. Removing a song *is* destructive and
 * cascades into playlists, so that stays refused for anything that predates this app.
 */
object AppleSongWrites {

    private const val LibraryHost = "https://amp-api.music.apple.com"
    private const val RatingsHost = "https://api.music.apple.com"
    private const val RatingBody = """{"type":"rating","attributes":{"value":1}}"""

    // 202 lands asynchronously; four looks over six seconds is enough for a library add to appear
    private const val PollAttempts = 4
    private const val PollGapMs = 1_500L

    private data class Answer(val status: Int, val body: String, val retryAfterSeconds: Long? = null)

    // the toast's "Remove anyway" runs after the tap that raised it has returned
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The catalogue id this song exists on Apple as: an imported song carries its own, a rematched
     * one keeps it in the manual mapping, and a YouTube song only has one once the push wrote it.
     */
    suspend fun catalogIdOf(dao: AppleSyncDao, songId: String): String? {
        if (songId.startsWith("AM_")) return songId.removePrefix("AM_").takeIf { it.isNotBlank() }
        return dao.rematch(songId)?.takeIf { it.source == AppleRematchEntity.SOURCE_APPLE }?.remoteId
            ?: dao.track(songId)?.catalogId
    }

    /**
     * Whether taking this song out of the library needs the person's yes. The guard refuses
     * anything that predates the fork, and a toast that vanishes before it is tapped left the row
     * in the library with no way to say yes - so the caller can ask in a dialog instead.
     */
    suspend fun removalNeedsConfirmation(context: Context, song: Song): Boolean {
        val entry = entry(context)
        val dao = entry.dao()
        val catalogId = catalogIdOf(dao, song.song.id) ?: return false
        val libraryId = runCatching { dao.librarySong(catalogId)?.libraryId }.getOrNull() ?: return false
        val decision = AppleGuard.decide(
            op = AppleOp(
                write = AppleWrite.REMOVE_FROM_LIBRARY,
                targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, libraryId)),
                confirmed = false,
            ),
            context = guardContext(context, dao),
        )
        return decision is AppleDecision.Refused &&
            (decision.reason == AppleRefusal.PRE_EXISTING_OBJECT || decision.reason == AppleRefusal.NOT_OWNED)
    }

    /** [catalogIdOf], for callers that only have a Context. */
    suspend fun appleIdFor(context: Context, songId: String): String? = catalogIdOf(entry(context).dao(), songId)

    /**
     * The recording a person chose keeps the Apple identity of the song it replaced.
     *
     * After a rematch the song's own id is no longer `AM_<catalogId>`, so the catalogue id has to
     * live in the manual mapping - which is what makes the next Apple import find the chosen
     * recording instead of inserting the original catalogue id again, and what keeps the heart and
     * the library button pointed at Apple.
     */
    suspend fun keepAppleIdentity(
        context: Context,
        songId: String,
        catalogId: String,
        title: String?,
        artist: String?,
        album: String?,
    ) {
        val dao = entry(context).dao()
        val replica = dao.librarySong(catalogId)
        val now = System.currentTimeMillis()
        dao.saveRematch(
            AppleRematchEntity(
                songId = songId,
                source = AppleRematchEntity.SOURCE_APPLE,
                remoteId = catalogId,
                title = title,
                artist = artist,
                album = album,
                durationMs = replica?.durationMs,
                isrc = replica?.isrc,
                state = AppleRematchEntity.STATE_MANUAL,
                updatedAt = now,
            ),
        )
        // Apple already has this recording as a row of its own: one song, and Apple's is the row
        val entry = entry(context)
        if (songId != "AM_$catalogId") {
            runCatching {
                val target = entry.database().song("AM_$catalogId").first()
                if (target != null) {
                    AppleMerge.absorb(entry.database(), dao, songId, "AM_$catalogId", catalogId)
                }
            }
        }
        dao.saveTrack(
            AppleTrackEntity(
                songId = songId,
                catalogId = catalogId,
                libraryId = replica?.libraryId,
                isrc = replica?.isrc,
                state = AppleTrackEntity.STATE_MANUAL,
                updatedAt = now,
            ),
        )
        AppleSyncStatus.setManualApple(dao.rematchedSongIds(AppleRematchEntity.SOURCE_APPLE).toSet())
    }

    /**
     * The heart, and the truth about it.
     *
     * Returns true when this song is Apple's to change - whether or not Apple accepted, because a
     * YouTube call for an `AM_` id is never the right next step.
     */
    suspend fun pushLike(
        context: Context,
        songId: String,
        liked: Boolean,
        title: String,
        /** the add path pushes a heart it already announced; it must not announce a second one */
        quiet: Boolean = false,
    ): Boolean {
        val entry = entry(context)
        val dao = entry.dao()
        val catalogId = catalogIdOf(dao, songId)
        if (catalogId == null) {
            // a heart on a song Apple has never heard of cannot go anywhere; saying so is the only
            // honest answer, and it is the one cue that was missing from this path
            if (!quiet) {
                TopToaster.show(
                    text = context.getString(R.string.apple_like_unmapped, title),
                    iconRes = R.drawable.favorite_border,
                )
            }
            return false
        }
        val op = if (liked) "RATING_SET" else "RATING_CLEARED"

        val tokens = tokensOrComplain(context, entry, op, catalogId) ?: return true
        if (!writesEnabled(context, op, catalogId, dao)) return true

        if (!quiet) {
            TopToaster.show(
                text = context.getString(if (liked) R.string.apple_like_adding else R.string.apple_like_removing, title),
                iconRes = R.drawable.favorite,
            )
        }

        return try {
            val answer = request(
                method = if (liked) "PUT" else "DELETE",
                url = "$RatingsHost/v1/me/ratings/songs/$catalogId",
                headers = tokens.headers,
                body = if (liked) RatingBody else null,
            )
            if (answer.status !in 200..299 && answer.status != 404) {
                throw AppleSyncException(describe(answer))
            }
            // the only evidence worth having: ask Apple what it thinks now
            val verified = runCatching { rated(tokens, catalogId) }.getOrNull() == liked
            dao.audit(
                AppleAuditEntity(
                    at = System.currentTimeMillis(),
                    op = op,
                    target = catalogId,
                    decision = "ALLOW",
                    httpStatus = answer.status,
                    verified = verified,
                ),
            )
            if (!verified) {
                TopToaster.error(context.getString(R.string.apple_write_unconfirmed, title))
            } else if (!quiet) {
                TopToaster.show(
                    text = context.getString(if (liked) R.string.apple_like_added else R.string.apple_like_removed, title),
                    iconRes = R.drawable.favorite,
                )
            }
            true
        } catch (e: Exception) {
            complain(context, op, catalogId, dao, e)
            true
        }
    }

    /**
     * Add or remove the song in the Apple Music library.
     *
     * A song with no Apple id yet is matched against the catalogue first: adding a song in Mochy has
     * to reach Apple, and the only way to name it there is to find the recording it is. A match is
     * recorded as this song's mapping before the write, so a failure leaves a usable id behind
     * rather than losing the work.
     *
     * Returns true when Apple owns the song, so the caller does not also run the YouTube path and
     * flip the local flag ahead of Apple. A song with no catalogue match returns false: it stays a
     * Mochy-only row, which is the honest answer until the picker can be offered from here.
     */
    /** [pushLibrary], for a caller that only has the row's id (a swipe, a list). */
    suspend fun pushLibrary(
        context: Context,
        songId: String,
        inLibrary: Boolean,
        confirmed: Boolean = false,
    ): Boolean {
        val entry = entry(context)
        val song = runCatching { entry.database().song(songId).first() }.getOrNull() ?: return false
        return pushLibrary(context, song, inLibrary, confirmed)
    }

    suspend fun pushLibrary(
        context: Context,
        song: Song,
        inLibrary: Boolean,
        confirmed: Boolean = false,
    ): Boolean {
        val entry = entry(context)
        val dao = entry.dao()
        val songId = song.song.id
        val title = song.song.title
        val known = catalogIdOf(dao, songId)
        // nothing of Apple's to take out of anything
        if (known == null && !inLibrary) return false
        val op = if (inLibrary) "LIBRARY_ADD" else "LIBRARY_REMOVE"

        val tokens = tokensOrComplain(context, entry, op, known ?: songId) ?: return true
        if (!writesEnabled(context, op, known ?: songId, dao)) return true

        // said before the work, not after it: a catalogue search or Apple's own delay is several
        // seconds of nothing, which is indistinguishable from a dead button
        TopToaster.show(
            text = context.getString(
                if (inLibrary) R.string.apple_write_adding else R.string.apple_write_removing,
                title,
            ),
            thumbnail = song.song.thumbnailUrl,
            iconRes = R.drawable.sync,
        )

        // an add needs a name on Apple: the song's own, or the recording the catalogue matched
        val matched = if (known == null) matchOnApple(tokens, dao, song) else null
        val catalogId = known ?: matched?.id ?: run {
            TopToaster.error(context.getString(R.string.apple_write_no_match, title))
            return false
        }

        return try {
            if (inLibrary) {
                // the add takes the id as a query parameter and no body: a JSON body is a 400
                val answer = request("POST", "$LibraryHost/v1/me/library?ids[songs]=$catalogId", tokens.headers)
                if (answer.status !in 200..299) throw AppleSyncException(describe(answer))
                val libraryId = awaitLibraryId(AppleClient(tokens), dao, catalogId, title)
                dao.audit(
                    AppleAuditEntity(
                        at = System.currentTimeMillis(),
                        op = op,
                        target = catalogId,
                        decision = "ALLOW",
                        httpStatus = answer.status,
                        verified = libraryId != null,
                    ),
                )
                // a heart already set here follows the song into Apple's library: otherwise adding a
                // liked song leaves the two sides disagreeing until it is tapped again
                if (libraryId != null && song.song.liked) {
                    runCatching { pushLike(context, songId, true, title, quiet = true) }
                }
                if (libraryId == null) {
                    // 202 and nothing yet: say so instead of pretending the row is there
                    TopToaster.show(
                        text = context.getString(R.string.apple_write_pending),
                        thumbnail = null,
                        iconRes = R.drawable.sync,
                    )
                    return true
                }
                dao.own(AppleOwnedEntity(AppleKind.LIBRARY_SONG.name, libraryId, System.currentTimeMillis(), title))
                // the mapping learns the library id Apple just gave the row
                dao.saveTrack(
                    AppleTrackEntity(
                        songId = songId,
                        catalogId = catalogId,
                        libraryId = libraryId,
                        state = AppleTrackEntity.STATE_AUTO,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                setLocalLibrary(entry, songId, true)
                TopToaster.show(
                    // a match is the app's guess, so it says which recording went in and can be
                    // checked against the one meant
                    text = if (matched != null) {
                        context.getString(R.string.apple_write_added_as, matched.title, matched.artist)
                    } else {
                        context.getString(R.string.apple_write_added)
                    },
                    thumbnail = song.song.thumbnailUrl,
                    iconRes = R.drawable.library_add_check,
                )
                true
            } else {
                // An absence is not a failure. A song can be in Mochy's library without being in
                // Apple's - imported from a playlist, or added before the sync existed - and there
                // is then nothing on Apple to take out. Refusing left the row stuck in the
                // library forever; removing it here and saying so is the honest answer.
                // The replica can hold a library id Apple has moved on from (a row that was
                // re-added, a playlist song that became a library song). Deleting that id answers
                // 204 and removes nothing, so Apple is asked first and the replica is the fallback.
                val client = AppleClient(tokens)
                val live = runCatching { searchLibrary(client, catalogId, title) }
                val liveId = live.getOrNull()
                val replicaId = runCatching { dao.librarySong(catalogId)?.libraryId }.getOrNull()
                val found: String? = liveId ?: replicaId
                if (live.isSuccess && found == null) {
                    dao.audit(
                        AppleAuditEntity(
                            at = System.currentTimeMillis(),
                            op = op,
                            target = catalogId,
                            decision = "ALLOW",
                            reason = "NO_APPLE_ROW",
                            verified = true,
                        ),
                    )
                    setLocalLibrary(entry, songId, false)
                    // the Apple row for the same recording is the same song: it leaves too, or the
                    // library keeps showing it as saved while this row says it is gone
                    if (songId != "AM_$catalogId") setLocalLibrary(entry, "AM_$catalogId", false)
                    TopToaster.show(
                        text = context.getString(R.string.apple_write_removed_locally),
                        iconRes = R.drawable.delete,
                    )
                    return true
                }
                val libraryId = found
                    ?: throw AppleSyncException("Apple has no library row for this song")
                val decision = AppleGuard.decide(
                    op = AppleOp(
                        write = AppleWrite.REMOVE_FROM_LIBRARY,
                        targets = listOf(AppleRef(AppleKind.LIBRARY_SONG, libraryId)),
                        confirmed = confirmed,
                    ),
                    context = guardContext(context, dao),
                )
                if (decision is AppleDecision.Refused) {
                    dao.audit(
                        AppleAuditEntity(
                            at = System.currentTimeMillis(),
                            op = op,
                            target = libraryId,
                            decision = "REFUSE",
                            reason = decision.reason.name,
                        ),
                    )
                    val needsAYes = decision.reason == AppleRefusal.PRE_EXISTING_OBJECT ||
                        decision.reason == AppleRefusal.NOT_OWNED
                    if (needsAYes && !confirmed) {
                        // a person tapping the button is not the sync engine: say what it will do
                        // and let them own the decision
                        TopToaster.show(
                            text = context.getString(R.string.apple_write_remove_confirm, title),
                            iconRes = R.drawable.library_add_check,
                            actionLabel = context.getString(R.string.apple_write_remove_anyway),
                            onAction = {
                                scope.launch { pushLibrary(context, song, false, confirmed = true) }
                            },
                        )
                    } else {
                        TopToaster.error(context.getString(refusalText(decision.reason)))
                    }
                    return true
                }
                val answer = request("DELETE", "$LibraryHost/v1/me/library/songs/$libraryId", tokens.headers)
                // 404 is the row already being gone, which is the answer this call wanted
                if (answer.status != 404 && answer.status !in 200..299) {
                    throw AppleSyncException(describe(answer))
                }
                // the replica is what used to make this check pass for a song it no longer held
                dao.deleteLibrarySong(catalogId)
                // a delete that lands is a delete the next read no longer shows; a failed read
                // proves nothing, so it is not treated as gone. Apple's library is not always
                // consistent on the next request, so it is asked more than once.
                var gone = false
                for (attempt in 0 until 3) {
                    val after = runCatching { searchLibrary(client, catalogId, title) }
                    if (after.isSuccess && after.getOrNull() == null) {
                        gone = true
                        break
                    }
                    if (attempt < 2) delay(700)
                }
                dao.audit(
                    AppleAuditEntity(
                        at = System.currentTimeMillis(),
                        op = op,
                        target = libraryId,
                        decision = "ALLOW",
                        httpStatus = answer.status,
                        verified = gone,
                    ),
                )
                dao.disown(AppleKind.LIBRARY_SONG.name, libraryId)
                if (gone) {
                    setLocalLibrary(entry, songId, false)
                    if (songId != "AM_$catalogId") setLocalLibrary(entry, "AM_$catalogId", false)
                    TopToaster.show(
                        text = context.getString(R.string.apple_write_removed),
                        iconRes = R.drawable.delete,
                    )
                } else {
                    TopToaster.error(context.getString(R.string.apple_write_unconfirmed, title))
                }
                true
            }
        } catch (e: Exception) {
            complain(context, op, catalogId, dao, e)
            true
        }
    }

    /** [pushLibrary] for a player button, which redraws its label from the row Apple just changed. */
    suspend fun pushLibraryThen(
        context: Context,
        song: Song,
        inLibrary: Boolean,
        after: () -> Unit,
    ): Boolean {
        val owned = pushLibrary(context, song, inLibrary)
        if (owned) after()
        return owned
    }

    /**
     * The Apple recording this song is, when the catalogue agrees strongly enough.
     *
     * [AppleMatcher] refuses anything under its own bar, so a doubt returns null and the song stays
     * as it is - a wrong recording in the user's Apple library is worse than a song that is only in
     * Mochy. A hit is written to the track table straight away: every later write and every dot
     * reads the mapping from there.
     */
    private suspend fun matchOnApple(tokens: AppleTokens.Pair, dao: AppleSyncDao, song: Song): CatalogSong? {
        val artist = song.artists.joinToString(", ") { it.name }
        if (song.song.title.isBlank() || artist.isBlank()) return null
        val match = runCatching {
            AppleMatcher(AppleClient(tokens)).best(
                title = song.song.title,
                artist = artist,
                album = song.song.albumName ?: song.album?.title,
                durationMs = song.song.duration.takeIf { it > 0 }?.times(1000L),
            )
        }.getOrNull() ?: return null
        dao.saveTrack(
            AppleTrackEntity(
                songId = song.song.id,
                catalogId = match.song.id,
                isrc = match.song.isrc,
                state = AppleTrackEntity.STATE_AUTO,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        return match.song
    }

    // ---- the library row ----------------------------------------------------------------------

    /**
     * The `i.…` id for a catalogue song: the replica first (a pull already knows every library row),
     * then Apple's own library search, which is the request that does not care where in the list the
     * row landed.
     */
    private suspend fun libraryIdOf(client: AppleClient, dao: AppleSyncDao, catalogId: String, title: String): String? {
        dao.librarySong(catalogId)?.libraryId?.let { return it }
        // an absence and a broken request must not look the same: this one throws on failure
        return searchLibrary(client, catalogId, title)
    }

    /** The same, for an add: the row is not in the replica yet, so it is waited for. */
    private suspend fun awaitLibraryId(client: AppleClient, dao: AppleSyncDao, catalogId: String, title: String): String? {
        repeat(PollAttempts) { attempt ->
            runCatching { libraryIdOf(client, dao, catalogId, title) }.getOrNull()?.let { return it }
            if (attempt < PollAttempts - 1) delay(PollGapMs)
        }
        return null
    }

    private suspend fun searchLibrary(client: AppleClient, catalogId: String, title: String): String? =
        client.librarySearch(title.ifBlank { catalogId })
            .firstOrNull { it.catalogId == catalogId && it.libraryId.startsWith("i.") }
            ?.libraryId

    private suspend fun setLocalLibrary(entry: AppleSongWritesEntryPoint, songId: String, inLibrary: Boolean) {
        val database = entry.database()
        val row = database.getSongById(songId)?.song ?: return
        if ((row.inLibrary != null) == inLibrary) return
        database.query { update(row.toggleLibrary()) }
    }

    // ---- the guard and the audit --------------------------------------------------------------

    /** The epoch never changes after it is taken, so reading it once per process is enough. */
    @Volatile
    private var epoch: Set<AppleRef>? = null

    private suspend fun guardContext(context: Context, dao: AppleSyncDao): AppleGuardContext {
        val known = epoch ?: dao.epoch()
            .mapNotNull { appleRefFromKey("${it.kind}:${it.remoteId}") }
            .toSet()
            .also { epoch = it }
        return AppleGuardContext(
            writesEnabled = AppleWritePrefs.enabled(context),
            epoch = known,
            owned = dao.owned(AppleKind.LIBRARY_SONG.name)
                .map { AppleRef(AppleKind.LIBRARY_SONG, it.remoteId) }
                .toSet(),
            linkedPlaylists = emptySet(),
        )
    }

    private suspend fun writesEnabled(context: Context, op: String, target: String, dao: AppleSyncDao): Boolean {
        if (AppleWritePrefs.enabled(context)) return true
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = op,
                target = target,
                decision = "REFUSE",
                reason = AppleRefusal.WRITES_DISABLED.name,
            ),
        )
        TopToaster.error(context.getString(R.string.apple_write_off))
        return false
    }

    private suspend fun complain(
        context: Context,
        op: String,
        target: String,
        dao: AppleSyncDao,
        error: Exception,
    ) {
        val signedOut = error is AppleSyncException && error.signedOut
        dao.audit(
            AppleAuditEntity(
                at = System.currentTimeMillis(),
                op = op,
                target = target,
                decision = "ERROR",
                reason = if (signedOut) "SIGNED_OUT" else error.message,
            ),
        )
        TopToaster.error(
            context.getString(
                if (signedOut) R.string.apple_write_signed_out else R.string.apple_write_failed,
                error.message ?: "Apple didn't answer",
            ),
        )
    }

    private suspend fun tokensOrComplain(
        context: Context,
        entry: AppleSongWritesEntryPoint,
        op: String,
        target: String,
    ): AppleTokens.Pair? {
        val tokens = runCatching { entry.tokens().read() }.getOrNull()
        if (tokens != null) return tokens
        complain(
            context = context,
            op = op,
            target = target,
            dao = entry.dao(),
            error = AppleSyncException("Sign in to Apple Music first", signedOut = true),
        )
        return null
    }

    private fun refusalText(reason: AppleRefusal): Int = when (reason) {
        AppleRefusal.WRITES_DISABLED -> R.string.apple_write_off
        AppleRefusal.PRE_EXISTING_OBJECT, AppleRefusal.NOT_OWNED -> R.string.apple_write_preexisting
        else -> R.string.apple_write_failed
    }

    // ---- verification -------------------------------------------------------------------------

    /** Apple's answer for one song's rating: 1 loved, -1 disliked, null none. */
    private suspend fun rated(tokens: AppleTokens.Pair, catalogId: String): Boolean? {
        val answer = request("GET", "$RatingsHost/v1/me/ratings/songs/$catalogId", tokens.headers)
        if (answer.status == 404) return false
        if (answer.status !in 200..299) return null
        return "\"value\":1" in answer.body.replace(" ", "")
    }

    // ---- HTTP ---------------------------------------------------------------------------------

    private suspend fun request(
        method: String,
        url: String,
        headers: Map<String, String>,
        body: String? = null,
    ): Answer = withContext(Dispatchers.IO) {
        val first = send(method, url, headers, body)
        // rate limited: one wait for the length Apple asked for, then its answer stands
        if (first.status != 429) return@withContext first
        delay((first.retryAfterSeconds ?: 2L).coerceIn(1, 30) * 1000)
        send(method, url, headers, body)
    }

    private fun send(method: String, url: String, headers: Map<String, String>, body: String?): Answer {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 20_000
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.use { it.write(body.toByteArray()) }
            }
        }
        return try {
            val status = connection.responseCode
            val text = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.use { it.readBytes().decodeToString() }
                .orEmpty()
            Answer(status, text, connection.getHeaderField("Retry-After")?.trim()?.toLongOrNull())
        } finally {
            connection.disconnect()
        }
    }

    private fun describe(answer: Answer): String {
        val said = answer.body.trim().take(140).takeIf { it.isNotEmpty() }
        return "Apple answered ${answer.status}" + (said?.let { ": $it" } ?: "")
    }

    private fun entry(context: Context): AppleSongWritesEntryPoint =
        EntryPointAccessors.fromApplication(context.applicationContext, AppleSongWritesEntryPoint::class.java)
}
