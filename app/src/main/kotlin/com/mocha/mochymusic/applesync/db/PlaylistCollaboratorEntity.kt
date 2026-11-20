// PlaylistCollaboratorEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity

/**
 * Someone other than the owner who can add to an imported playlist.
 *
 * Apple's MusicKit answers do not carry these names at all, so they are read from the web player's
 * own page, where the playlist header lists the people on a shared playlist.
 */
@Entity(tableName = "playlist_collaborator", primaryKeys = ["playlistId", "name"])
data class PlaylistCollaboratorEntity(
    val playlistId: String,
    val name: String,
    val position: Int = 0,
)
