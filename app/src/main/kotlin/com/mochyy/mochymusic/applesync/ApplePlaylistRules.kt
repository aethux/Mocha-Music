// ApplePlaylistRules.kt
//
// Who may change a playlist: Apple's answer, corrected for the one case it gets in the way of.
//
// Apple's library resource says `canEdit = false` for two very different things: the lists it builds
// for the account (a mix, a radio list - a rewrite is a 500) and the playlists the account shares
// with other people. The second kind is editable by the people on it, and `hasCollaboration` is what
// tells them apart.

package com.mochyy.mochymusic.applesync

import android.content.Context
import com.mochyy.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mochyy.mochymusic.db.entities.Playlist
import dagger.hilt.android.EntryPointAccessors

object ApplePlaylistRules {

    /** True when the import recorded other people on this playlist. */
    suspend fun shared(context: Context, playlistId: String): Boolean = runCatching {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        ).dao().playlistMeta(playlistId)?.collaborative == true
    }.getOrDefault(false)

    /** Whether this playlist has to be left alone: Apple's own, and not shared with anyone. */
    suspend fun isAppleOnly(context: Context, playlist: Playlist): Boolean =
        !playlist.playlist.isEditable && !shared(context, playlist.id)
}
