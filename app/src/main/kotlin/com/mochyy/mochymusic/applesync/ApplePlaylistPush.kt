// ApplePlaylistPush.kt
//
// Sends a linked playlist to Apple as soon as its songs change, wherever the change was made.
//
// The signal is the table, not a call site: a playlist's song ids are a Room Flow, so any insert,
// delete or reorder in `playlist_song_map` re-runs it and says "this one moved" - a menu row, a
// swipe, a drag, the add-to-playlist picker, without one line at each. A burst of edits is
// debounced into the single PUT Apple accepts anyway. The poll underneath is only the net: a link
// made while the app is running, or a signal that was missed.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.db.AppleLinkEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.db.MusicDatabase
import com.mochyy.mochymusic.ui.component.TopToaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@OptIn(FlowPreview::class)
object ApplePlaylistPush {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** What each linked playlist looked like when it was last known to match Apple. */
    private val lastSent = mutableMapOf<String, String>()

    /**
     * A failed send buys this playlist quiet. A change is not an admission to retry something Apple
     * has already refused: one bad playlist must not become a thousand requests.
     */
    private val lastFailure = mutableMapOf<String, Long>()
    private const val RetryAfterMs = 30_000L

    /** A burst of edits is collected this long first: ten adds in a row are one PUT either way. */
    private const val BurstMs = 600L

    /** The net under the change signal, for a link made while the app runs. */
    private const val PollMs = 20_000L

    /** The "Apple won't change this one" sentence is said once per change, not once per check. */
    private val lastTold = mutableMapOf<String, String>()

    @Volatile
    private var started = false

    /** Call once, from the application. Safe to call again. */
    fun start(applicationContext: Context) {
        if (started) return
        started = true
        val entry = dagger.hilt.android.EntryPointAccessors.fromApplication(
            applicationContext,
            com.mochyy.mochymusic.applesync.di.AppleSongWritesEntryPoint::class.java,
        )
        val database = entry.database()
        val dao = entry.dao()

        val changed = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        val dirty = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        val watching = mutableSetOf<String>()
        // Room invalidates a query when its table is written, so every linked playlist's ids Flow
        // re-runs on any playlist edit. What moved is decided here, by the ids themselves, so one
        // playlist's add does not mark the other twenty-five.
        val seenIds = mutableMapOf<String, String>()

        scope.launch {
            runCatching {
                dao.links().collect { links ->
                    links.filter { watching.add(it.playlistId) }.forEach { link ->
                        scope.launch {
                            runCatching {
                                database.playlistSongIds(link.playlistId).collect { ids ->
                                    val now = ids.sorted().joinToString(",")
                                    if (seenIds[link.playlistId] == now) return@collect
                                    seenIds[link.playlistId] = now
                                    dirty.add(link.playlistId)
                                    changed.tryEmit(Unit)
                                }
                            }
                        }
                    }
                }
            }
        }

        scope.launch {
            changed.debounce(BurstMs).collect {
                val ids = synchronized(dirty) { dirty.toList().also { dirty.clear() } }
                ids.forEach { playlistId ->
                    runCatching { check(applicationContext, database, dao, playlistId, talk = true) }
                }
            }
        }

        scope.launch {
            while (true) {
                runCatching { poll(applicationContext, database, dao) }
                delay(PollMs)
            }
        }
    }

    private suspend fun poll(context: Context, database: MusicDatabase, dao: AppleSyncDao) {
        val links = runCatching { dao.links().first() }.getOrDefault(emptyList())
        links.forEach { link ->
            runCatching { check(context, database, dao, link.playlistId, talk = false) }
        }
    }

    /**
     * Sends one playlist when what it holds now is not what Apple holds.
     *
     * [talk] is the difference between a person's edit and the safety net: an edit answers in a
     * toast, whatever came of it. The net says nothing about a send that worked - it exists for the
     * playlist nobody touched - and only speaks when something is wrong.
     */
    private suspend fun check(
        context: Context,
        database: MusicDatabase,
        dao: AppleSyncDao,
        playlistId: String,
        talk: Boolean,
    ) {
        val link = runCatching { dao.link(playlistId) }.getOrNull() ?: return
        val signature = signatureOf(database, playlistId) ?: return
        if (link.lastSignature == signature || lastSent[playlistId] == signature) return

        // A link with no baseline adopts what is here instead of sending it: pushing at that moment
        // would rewrite Apple's playlist with a replica that may be older than it is. Linking on
        // import writes the baseline for the same reason.
        if (link.lastSignature == null) {
            runCatching { dao.saveLink(link.copy(lastSignature = signature)) }
            lastSent[playlistId] = signature
            return
        }

        // A playlist the account only follows answers 500 to every rewrite, and asking again every
        // few seconds is a storm for a request that cannot succeed. It is said once instead: an add
        // Apple will never see must not look like one it took.
        val editable = runCatching { dao.playlist(link.remoteId)?.canEdit }.getOrNull()
            ?: runCatching { database.playlistEntity(playlistId)?.isEditable }.getOrNull()
        if (editable == false) {
            lastSent[playlistId] = signature
            if (talk) complainFrozen(context, playlistId, signature)
            return
        }

        send(context, dao, link, signature, talk)
    }

    private suspend fun send(
        context: Context,
        dao: AppleSyncDao,
        link: AppleLinkEntity,
        signature: String,
        talk: Boolean,
    ) = mutex.withLock {
        if (lastSent[link.playlistId] == signature) return@withLock
        val now = System.currentTimeMillis()
        if (now - (lastFailure[link.playlistId] ?: 0L) < RetryAfterMs) return@withLock

        // A removal said so already, with an undo beside it, and a second toast would take that undo
        // away - so a removal that worked is silent and an addition is announced. Either way a
        // failure is announced.
        val before = link.lastSignature?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty()
        val after = signature.split(',').filter { it.isNotEmpty() }.toSet()
        val tookSomethingOut = (before - after).isNotEmpty()

        val sent = ApplePlaylistWrites.push(context, link.playlistId, quiet = true)
        val refusal = sent.refusal
        if (refusal == null) {
            lastSent[link.playlistId] = signature
            lastFailure.remove(link.playlistId)
            if (talk) {
                when {
                    !sent.verified -> TopToaster.error(context.getString(R.string.apple_playlist_unverified))
                    tookSomethingOut -> Unit
                    else -> TopToaster.show(
                        text = context.getString(R.string.apple_playlist_pushed_short),
                        iconRes = R.drawable.sync,
                    )
                }
            }
        } else {
            lastFailure[link.playlistId] = now
            if (talk) TopToaster.error(refusal)
        }
    }

    /** Once per change: the same refusal on every poll would be noise nobody can act on. */
    private fun complainFrozen(context: Context, playlistId: String, signature: String) {
        if (lastTold[playlistId] == signature) return
        lastTold[playlistId] = signature
        runCatching { TopToaster.error(context.getString(R.string.apple_playlist_not_editable)) }
    }

    private fun signatureOf(database: MusicDatabase, playlistId: String): String? =
        runCatching {
            database.playlistSongMaps(playlistId, 0).joinToString(",") { it.songId }
        }.getOrNull()

    /**
     * Says this playlist already matches Apple, without sending anything.
     *
     * An import writes the playlist from Apple's own list: from the watcher's side that looks
     * exactly like a person editing it, and pushing it back would rewrite the account with what it
     * just gave us - and, for a song the import had to skip, remove it from the Apple playlist.
     */
    fun seed(playlistId: String, signature: String) {
        lastSent[playlistId] = signature
    }

    /** Sends one playlist now, for a menu action that says so. */
    suspend fun pushNow(context: Context, playlistId: String) =
        ApplePlaylistWrites.push(context, playlistId, quiet = false)
}
