// AppleRematchEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One song, and the recording the user said it is.
 *
 * A Mocha song is identified by its provider id - a YouTube video id, or `AM_<catalogueId>` for
 * something imported from Apple - and playback resolves from that id, so a *different* recording has
 * nowhere to live. This table is that place: [songId] stays what the app knows, and [source] +
 * [remoteId] name the recording that should actually play.
 *
 * [source] is a provider id exactly as the provider order spells it (`applemusic`, `monochrome`,
 * `youtube`), which is what lets the player treat a pin as "ask this one, by this id". [state] is
 * [STATE_MANUAL] for everything here: a row exists only because a person picked it, and nothing in
 * the sync may overwrite or delete one.
 *
 * [title]..[isrc] are what the remote side calls it, kept so the sheet can say what is pinned without
 * another network call.
 */
@Entity(tableName = "rematch", indices = [Index(value = ["source"])])
data class AppleRematchEntity(
    @PrimaryKey val songId: String,
    val source: String,
    val remoteId: String,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationMs: Long? = null,
    val isrc: String? = null,
    val state: String = STATE_MANUAL,
    val updatedAt: Long = 0L,
) {
    companion object {
        /** A person chose this; nothing automatic may replace it. */
        const val STATE_MANUAL = "MANUAL"

        // Provider ids, spelled the way the provider order spells them. A test holds them to it.
        const val SOURCE_APPLE = "applemusic"
        const val SOURCE_MONOCHROME = "monochrome"
        const val SOURCE_YOUTUBE = "youtube"
    }
}
