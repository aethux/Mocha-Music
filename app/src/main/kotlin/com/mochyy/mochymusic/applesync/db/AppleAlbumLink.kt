// AppleAlbumLink.kt

package com.mochyy.mochymusic.applesync.db

/**
 * One row of `song` as the album import needs it: which song, and which album it is already on.
 * A projection, so the pass can skip songs whose link is already right without reading the rows.
 */
data class AppleAlbumLink(
    val id: String,
    val albumId: String?,
)
