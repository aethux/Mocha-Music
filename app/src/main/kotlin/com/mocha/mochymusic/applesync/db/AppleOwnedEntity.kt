// AppleOwnedEntity.kt

package com.mocha.mochymusic.applesync.db

import androidx.room.Entity

/**
 * Objects this app created: playlists it made, library rows it added.
 *
 * Ownership is the only thing that permits a delete. An id that is not here cannot be removed, even
 * when the app is linked to it, which is what makes "test with new playlists only" a rule the code
 * enforces rather than a habit we remember.
 */
@Entity(tableName = "owned", primaryKeys = ["kind", "remoteId"])
data class AppleOwnedEntity(
    val kind: String,
    val remoteId: String,
    val createdAt: Long,
    val label: String? = null,
)
