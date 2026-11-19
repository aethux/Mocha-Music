// CoverOverrideEntity.kt
//
// A cover the person chose themselves, for a playlist, an artist or a song. It lives beside the
// library rather than in it, because nothing on Apple's side can hold it: playlist artwork there is
// derived from its tracks, so a chosen image can only ever be local. An import reads this table and
// leaves those rows alone.

package com.mochyy.mochymusic.applesync.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "cover_override")
data class CoverOverrideEntity(
    /** "<kind>:<id>" - kind is playlist, artist or song */
    @PrimaryKey val key: String,
    /** a file path inside the app's own storage, or an https url for the online lookup */
    val value: String,
    val updatedAt: Long,
)
