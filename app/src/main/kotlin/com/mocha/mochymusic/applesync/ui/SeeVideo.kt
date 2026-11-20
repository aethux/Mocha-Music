// SeeVideo.kt
//
// "See video" is a button in every style, so it says something when YouTube has no video for the
// song instead of turning on a mode that draws nothing.

package com.mocha.mochymusic.applesync.ui

import android.content.Context
import com.mocha.mochymusic.R
import com.mocha.mochymusic.models.MediaMetadata
import com.mocha.mochymusic.ui.component.TopToaster
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

object SeeVideo {

    /**
     * The button's new state. Turning it off is immediate; turning it on asks YouTube first, and
     * switches itself back off with a word when there is no video to show.
     */
    fun toggle(
        show: Boolean,
        scope: CoroutineScope,
        context: Context,
        metadata: MediaMetadata?,
        lookUp: suspend (id: String, title: String, artist: String, isVideoSong: Boolean) -> Any?,
        onNoVideo: () -> Unit,
    ): Boolean {
        if (show) return false
        val meta = metadata ?: return true
        scope.launch {
            val found = runCatching {
                lookUp(meta.id, meta.title, meta.artists.joinToString { it.name }, meta.isVideoSong)
            }.getOrNull()
            if (found == null) {
                onNoVideo()
                TopToaster.error(context.getString(R.string.pick_no_video))
            }
        }
        return true
    }
}
