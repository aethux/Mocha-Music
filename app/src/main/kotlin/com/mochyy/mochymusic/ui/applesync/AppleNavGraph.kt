// AppleNavGraph.kt
//
// The fork's own pages, registered from one file so NavigationBuilder carries one tagged line
// instead of four blocks of boilerplate.

package com.mochyy.mochymusic.ui.applesync

import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.mochyy.mochymusic.ui.pick.AppleMatchArguments
import com.mochyy.mochymusic.ui.pick.AppleMatchRoute
import com.mochyy.mochymusic.ui.pick.AppleMatchScreen
import com.mochyy.mochymusic.ui.pick.PickRecordingArguments
import com.mochyy.mochymusic.ui.pick.PickRecordingRoute
import com.mochyy.mochymusic.ui.pick.PickRecordingScreen

/** Tie a song to an Apple recording, pick the recording it plays, and the two Apple pages. */
fun NavGraphBuilder.appleSyncNavGraph(navController: NavController) {
    composable(route = AppleMatchRoute, arguments = AppleMatchArguments) {
        AppleMatchScreen(
            songId = it.arguments?.getString("songId").orEmpty(),
            navController = navController,
        )
    }
    composable(route = PickRecordingRoute, arguments = PickRecordingArguments) {
        PickRecordingScreen(
            songId = it.arguments?.getString("songId").orEmpty(),
            navController = navController,
        )
    }
    composable(
        route = com.mochyy.mochymusic.applesync.ui.APPLE_SONGS_ROUTE,
        arguments = listOf(androidx.navigation.navArgument("source") { type = androidx.navigation.NavType.StringType }),
    ) {
        com.mochyy.mochymusic.applesync.ui.AppleSongsScreen(
            source = it.arguments?.getString("source").orEmpty(),
            navController = navController,
        )
    }
    composable(
        route = com.mochyy.mochymusic.ui.applesync.AppleCollaboratorsRoute,
        arguments = listOf(androidx.navigation.navArgument("playlistId") { type = androidx.navigation.NavType.StringType }),
    ) {
        com.mochyy.mochymusic.ui.applesync.AppleCollaboratorsScreen(
            playlistId = it.arguments?.getString("playlistId").orEmpty(),
            navController = navController,
        )
    }
    composable(AppleSyncRoute) {
        AppleSyncScreen(navController = navController)
    }
    composable(AppleSessionRoute) {
        AppleSessionScreen(navController = navController)
    }
}
