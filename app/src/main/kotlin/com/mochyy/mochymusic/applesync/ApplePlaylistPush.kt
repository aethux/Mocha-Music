// ApplePlaylistPush.kt
//
// Watches the playlist tables and sends linked playlists to Apple on their own, so adding a song
// here shows up in Apple Music without anyone asking for it. Debounced: a batch of adds, a drag to
// reorder and a delete in the same breath become one PUT, which is all Apple accepts anyway.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.db.MusicDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ApplePlaylistPush {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutex = Mutex()

    /** what each linked playlist looked like when it was last sent, so a no-op change costs nothing */
    private val lastSent = mutableMapOf<String, String>()

    /**
     * When each playlist was last attempted. Apple answers 500 for a few of these playlists; a
     * five-second loop then turned one failure into a thousand requests, so an attempt buys at
     * least [RetryAfterMs] of quiet whatever its answer.
     */
    private val lastAttempt = mutableMapOf<String, Long>()
    private const val RetryAfterMs = 30_000L

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
        // the database hands out no invalidation tracker, so this looks rather than listens; the
        // query is two small reads and the signature check stops it from sending anything twice
        scope.launch {
            while (true) {
                runCatching { tick(applicationContext, database, dao) }
                delay(5_000)
            }
        }
    }

    private suspend fun tick(context: Context, database: MusicDatabase, dao: AppleSyncDao) {
        val linked = runCatching { dao.links().first() }.getOrDefault(emptyList())
        linked.forEach { link ->
            // a link row is the permission, whichever way the playlist was linked: an imported one
            // (AM_PL_...) or one made here and pointed at an Apple playlist. Filtering on the id
            // meant the second kind never sent anything, so edits in Mochy never reached Apple.
            val signature = runCatching {
                database.playlistSongMaps(link.playlistId, 0).joinToString(",") { it.songId }
            }.getOrNull() ?: return@forEach

            // no baseline yet: adopt what is here instead of sending it. Pushing at startup would
            // rewrite Apple's playlists with a replica that may be older than they are.
            if (link.lastSignature == null) {
                runCatching { dao.saveLink(link.copy(lastSignature = signature)) }
                lastSent[link.playlistId] = signature
                return@forEach
            }
            if (link.lastSignature == signature || lastSent[link.playlistId] == signature) return@forEach

            // a playlist Apple says the account cannot edit answers 500 to every rewrite; asking
            // again every five seconds is a storm for a request that cannot succeed
            val editable = runCatching { dao.playlist(link.remoteId)?.canEdit }.getOrNull()
            if (editable == false) {
                lastSent[link.playlistId] = signature
                return@forEach
            }

            mutex.withLock {
                if (lastSent[link.playlistId] == signature) return@withLock
                val now = System.currentTimeMillis()
                if (now - (lastAttempt[link.playlistId] ?: 0L) < RetryAfterMs) return@withLock
                lastAttempt[link.playlistId] = now
                val sent = ApplePlaylistWrites.push(context, link.playlistId, quiet = true)
                // an edit made by hand should answer, refusal included: a silent watcher is how a
                // playlist stopped syncing for a day without anyone being able to tell why
                runCatching {
                    val refusal = sent.refusal
                    if (refusal == null) {
                        com.mochyy.mochymusic.ui.component.TopToaster.show(
                            text = context.getString(
                                com.mochyy.mochymusic.R.string.apple_playlist_pushed_short,
                            ),
                            iconRes = com.mochyy.mochymusic.R.drawable.sync,
                        )
                    } else {
                        com.mochyy.mochymusic.ui.component.TopToaster.error(refusal)
                    }
                }
                // only a send that happened is remembered as sent: a refusal is retried on the
                // next edit instead of being treated as the new baseline
                if (sent.refusal == null) {
                    lastSent[link.playlistId] = signature
                    runCatching { dao.saveLink(link.copy(lastSignature = signature)) }
                }
            }
        }
    }

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
