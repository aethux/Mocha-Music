// CoverZoom.kt
//
// The player's cover zoom, at the moment a blend of it is needed: the still sits fitted in the mini
// player and cropped to the listener's chosen zoom once the sheet is open, and the lyrics header
// keeps it as a thumbnail. Upstream made this inline at its CoverPicture call; the fork's call site
// goes through AppleCover, so the arithmetic lives here and the seam stays one line.

package com.mocha.mochymusic.applesync.ui

/** The scale to draw a cover at, [zoom] being the listener's setting and 0 the mini player's fit. */
fun openCoverZoom(
    zoom: Float?,
    progress: () -> Float,
    lyricsProgress: () -> Float,
): (() -> Float)? = zoom?.let { chosen ->
    {
        val open = ((progress() - 0.5f) / 0.45f).coerceIn(0f, 1f) *
            (1f - lyricsProgress()).coerceIn(0f, 1f)
        1f - (1f - chosen) * open
    }
}
