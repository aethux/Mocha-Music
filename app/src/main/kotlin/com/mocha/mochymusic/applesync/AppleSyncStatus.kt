// AppleSyncStatus.kt

package com.mocha.mochymusic.applesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * What the sync is doing right now, in one place.
 *
 * Every screen that can see a library read subscribes to this instead of inventing its own spinner:
 * the refresh button, the library list and the settings page all read the same counters, so "how far
 * along is it" has one answer. It lives in memory on purpose — a progress report that survives a
 * restart would be describing something that is no longer running.
 */
object AppleSyncStatus {

    data class Progress(
        val running: Boolean = false,
        val phase: String = "",
        val songs: Int = 0,
        val playlists: Int = 0,
        val totalSongs: Int = 0,
        val totalPlaylists: Int = 0,
        val finishedMessage: String? = null,
        val failed: Boolean = false,
    ) {
        /** 0f..1f for a determinate bar; null while the total is not known yet. */
        val fraction: Float?
            get() {
                val total = totalSongs + totalPlaylists
                if (total <= 0) return null
                return ((songs + playlists).toFloat() / total).coerceIn(0f, 1f)
            }
    }

    var progress by mutableStateOf(Progress())
        private set

    /** Local song ids that have an Apple catalogue id already sitting in the replica. */
    var syncedSongIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** Songs a person matched to an Apple recording by hand: on Apple all the same, no dot. */
    var manualAppleSongIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /**
     * Songs with an Apple track row. A song added from Mocha and matched on Apple has one but no
     * replica row, so `syncedSongIds` alone still dotted it - the last three songs of this library.
     */
    var trackedAppleSongIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** True once a pull has run, so the list does not dot every song before Apple is connected. */
    var known by mutableStateOf(false)
        private set

    /**
     * An `AM_` row came out of Apple's own library, so it is on Apple by construction: the track
     * table only learned about songs added since the sync existed, and its absence dotted every
     * song the first import ever brought in.
     */
    fun isSynced(songId: String): Boolean = songId in syncedSongIds ||
        songId in manualAppleSongIds ||
        songId in trackedAppleSongIds ||
        songId.startsWith("AM_")

    fun setTracked(ids: Set<String>) {
        trackedAppleSongIds = ids
    }

    /** Should this row carry the "not on Apple yet" dot. */
    fun shouldMark(songId: String): Boolean = known && !isSynced(songId)

    fun start(phase: String) {
        progress = Progress(running = true, phase = phase)
    }

    fun update(phase: String, songs: Int = progress.songs, playlists: Int = progress.playlists,
               totalSongs: Int = progress.totalSongs, totalPlaylists: Int = progress.totalPlaylists) {
        progress = progress.copy(
            phase = phase,
            songs = songs,
            playlists = playlists,
            totalSongs = totalSongs,
            totalPlaylists = totalPlaylists,
        )
    }

    fun finish(message: String) {
        progress = progress.copy(running = false, phase = "", finishedMessage = message, failed = false)
    }

    fun fail(message: String) {
        progress = progress.copy(running = false, phase = "", finishedMessage = message, failed = true)
    }

    fun dismiss() {
        progress = progress.copy(finishedMessage = null)
    }

    fun setSynced(ids: Set<String>) {
        syncedSongIds = ids
        known = true
    }

    /** A single song just went to Apple: it stops carrying the dot from here on. */
    fun noteSynced(songId: String) {
        syncedSongIds = syncedSongIds + songId
    }

    fun setManualApple(ids: Set<String>) {
        manualAppleSongIds = ids
    }
}
