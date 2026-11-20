// AppleCover.kt
//
// Apple's animated artwork, drawn in the player's own cover box. The still is what shows until the
// cache answers, what shows when there is no motion for the song, and what shows if the loop fails
// to play - so the box, the crop and the clip are the same whichever one is on screen.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.mocha.mochymusic.applesync.AppleCovers

/**
 * The cover for [songId]: its cached Apple motion when there is one, otherwise [still].
 *
 * [songId] is null for artwork that is not the playing song's own (a carousel's neighbour page, an
 * album's art on someone else's card), which is drawn as it always was.
 */
@Composable
fun AppleCover(
    songId: String?,
    modifier: Modifier = Modifier,
    still: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    var animatedUrl by remember(songId) { mutableStateOf<String?>(null) }
    LaunchedEffect(songId) {
        animatedUrl = if (songId != null) AppleCovers.animatedCoverFor(context, songId) else null
    }
    val motion = animatedUrl
    if (motion != null) {
        AnimatedCoverPicture(url = motion, onFailed = { animatedUrl = null }, modifier = modifier)
    } else {
        still(modifier)
    }
}

/**
 * Apple's cover loop, drawn where the still would be and cropped the same way.
 *
 * Silent, no controls, no chrome of its own: the player's own layout keeps deciding the size, the
 * corners and what sits on top.
 */
@Composable
private fun AnimatedCoverPicture(
    url: String,
    onFailed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember(url) {
        androidx.media3.exoplayer.ExoPlayer.Builder(context).build().apply {
            repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
            volume = 0f
            setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(url) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                onFailed()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setKeepContentOnPlayerReset(true)
            }
        },
        update = { view -> view.player = player },
        modifier = modifier,
    )
}
