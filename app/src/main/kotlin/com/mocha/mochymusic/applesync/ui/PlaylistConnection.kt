// PlaylistConnection.kt
//
// Two small pieces a playlist screen needs from this fork, kept here so an upstream file gains one
// line instead of five.
//
// The cue is the point: a playlist in this app either is or is not a copy of one on Apple Music, and
// after a send or a delete that is the thing a person wants to see without opening a menu. It is read
// once per composition - the link table is tiny and primary-keyed - so the line cannot go stale in
// the way an async prefetch can.

package com.mocha.mochymusic.applesync.ui

import android.content.Context
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.ui.applesync.appleCollaboratorsRoute
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/**
 * " · Connected to Apple Music" or " · Not on Apple Music", for the line under a playlist's name.
 *
 * A single keyed read on a background thread, then the string: it is called from composition, where
 * the alternative was an async read that a menu-sheet snapshot would report before it landed.
 */
fun appleConnectionSuffix(context: Context, playlistId: String): String {
    val link = runCatching {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            ApplePlaylistEntryPoint::class.java,
        )
        runBlocking(Dispatchers.IO) { entry.dao().link(playlistId) }
    }.getOrNull()
    val said = if (link != null) {
        context.getString(R.string.menu_apple_connected_suffix)
    } else {
        context.getString(R.string.menu_apple_not_connected_suffix)
    }
    return " · $said"
}

/** The collaborators row's action, so the screen keeps one line where it had five. */
fun appleCollaboratorsNav(navController: NavController, playlistId: String): () -> Unit = {
    navController.navigate(appleCollaboratorsRoute(playlistId))
}
