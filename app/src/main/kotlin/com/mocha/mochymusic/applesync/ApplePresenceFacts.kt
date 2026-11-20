// ApplePresenceFacts.kt
//
// What Discord is told about a song's identity. An `AM_…` id is an Apple catalogue id: it belongs
// to Apple Music, and a `/watch?v=AM_…` link goes nowhere. Both facts are decided here, in one
// place, rather than in the middle of the presence builder.

package com.mocha.mochymusic.applesync

import com.mocha.mochymusic.db.entities.Song
import com.mocha.mochymusic.models.MediaMetadata

object ApplePresenceFacts {

    /** True only for a song YouTube can actually link to. */
    fun isYouTube(song: Song?, metadata: MediaMetadata): Boolean =
        song?.song?.isLocal != true && metadata.localUri == null &&
            !metadata.id.startsWith("AM_") && !metadata.id.startsWith("LOCAL_")

    /** The catalogue id an `AM_` song carries, so its link opens that song and not a search. */
    fun appleCatalogId(metadata: MediaMetadata): String? =
        metadata.id.removePrefix("AM_").takeIf { metadata.id.startsWith("AM_") && it.isNotBlank() }
}
