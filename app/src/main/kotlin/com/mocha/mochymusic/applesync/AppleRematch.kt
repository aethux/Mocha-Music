// AppleRematch.kt

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.applesync.db.AppleRematchEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The manual pins, in the shape playback can ask about them.
 *
 * Playback resolves a song from its id, in a fetcher that runs on the player's own thread and may not
 * block on a database read for every song, so the whole table is read once and kept in memory; a save
 * or a clear updates both the database and the copy. The table is small - only songs a person chose -
 * and a row that is a few hundred milliseconds stale is only ever stale after a pick, which updates
 * the copy in the same call.
 *
 * Nothing here is automatic. A pin exists because someone picked a recording, and no sync path writes
 * to this table.
 */
@Singleton
class AppleRematch @Inject constructor(private val dao: AppleRematchStore) {

    /** The recording a song plays instead of its own. [source] is a provider id. */
    data class Pin(
        val source: String,
        val remoteId: String,
        val title: String? = null,
        val artist: String? = null,
    )

    private val pins = ConcurrentHashMap<String, Pin>()
    private val loading = Mutex()

    @Volatile
    private var loaded = false

    /** What [songId] is pinned to, or null when the song's own id is what plays. */
    suspend fun pin(songId: String): Pin? {
        ensureLoaded()
        return pins[songId]
    }

    /**
     * The pin as a `TrackQuery.externalId` spells it: `<provider>:<remote id>`, so a module can tell
     * its own id from another provider's. Null for a YouTube pin - YouTube plays the song's own id
     * and has nothing to look up.
     */
    suspend fun externalId(songId: String): String? {
        val pin = pin(songId) ?: return null
        if (pin.source == AppleRematchEntity.SOURCE_YOUTUBE) return null
        return "${pin.source}:${pin.remoteId}"
    }

    /** The stored row, for a sheet that wants to show what is pinned. */
    suspend fun current(songId: String): AppleRematchEntity? = dao.rematch(songId)

    suspend fun save(
        songId: String,
        source: String,
        remoteId: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationMs: Long? = null,
        isrc: String? = null,
    ) {
        dao.saveRematch(
            AppleRematchEntity(
                songId = songId,
                source = source,
                remoteId = remoteId,
                title = title,
                artist = artist,
                album = album,
                durationMs = durationMs,
                isrc = isrc,
                state = AppleRematchEntity.STATE_MANUAL,
                updatedAt = System.currentTimeMillis(),
            ),
        )
        pins[songId] = Pin(source, remoteId, title, artist)
        publishAppleDots()
    }

    /** Back to the app's own recording, and the dot comes back with it. */
    suspend fun clear(songId: String) {
        dao.clearRematch(songId)
        pins.remove(songId)
        publishAppleDots()
    }

    /** Read the table again, for a caller that knows it changed underneath this process. */
    suspend fun reload() = loading.withLock { read() }

    private suspend fun ensureLoaded() {
        if (loaded) return
        loading.withLock {
            if (!loaded) read()
        }
    }

    private suspend fun read() {
        val rows = dao.rematches()
        pins.clear()
        rows.forEach { pins[it.songId] = Pin(it.source, it.remoteId, it.title, it.artist) }
        loaded = true
        publishAppleDots()
    }

    /**
     * A manually matched Apple song has an Apple copy as surely as an imported one does, so the "not
     * on Apple" dot has to know about it. Published here rather than in the list, so the dot and the
     * pin can never disagree.
     */
    private suspend fun publishAppleDots() {
        AppleSyncStatus.setManualApple(
            runCatching { dao.rematchedSongIds(AppleRematchEntity.SOURCE_APPLE).toSet() }.getOrDefault(emptySet()),
        )
    }
}

/**
 * The track id in a pasted Monochrome link, or null when the text isn't one.
 *
 * Monochrome has no search API to pick a recording from, so the sheet takes a link: the id is the last
 * meaningful path segment of `/<track|s>/<id>`. A bare id is accepted too, because a person who has
 * one should not have to invent a URL around it.
 */
internal fun monochromeTrackId(link: String): String? {
    val text = link.trim()
    if (text.isEmpty() || text.any { it == ' ' || it == '\n' || it == '\t' }) return null
    if ("://" !in text && '/' !in text) return text
    val path = text.substringAfter("://", text).substringAfter('/')
    val segments = path.substringBefore('?').substringBefore('#').split('/').filter { it.isNotBlank() }
    return if (segments.size >= 2 && segments[0] in setOf("track", "s")) segments[1] else null
}
