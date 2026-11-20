// LibrarySongsScreen.kt

package com.mocha.mochymusic.ui.screens.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavController

// MOCHY-APPLE: the page itself is the fork's, with Apple's order and the sort control
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    com.mocha.mochymusic.applesync.ui.LibrarySongsPage(navController, modifier, pureBlack)
}
