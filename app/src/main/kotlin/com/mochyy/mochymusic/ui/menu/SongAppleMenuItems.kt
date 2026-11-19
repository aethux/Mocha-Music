// SongAppleMenuItems.kt
//
// The two rows that say what a song is on Apple Music: the recording it plays, and the Apple
// recording its heart and its library flag belong to. Handed back as an array so a menu can spread
// them into its own list, where they belong - between "add to playlist" and "share".

package com.mochyy.mochymusic.ui.menu

import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.ui.component.Material3MenuItemData
import com.mochyy.mochymusic.ui.pick.appleMatchRoute
import com.mochyy.mochymusic.ui.pick.pickRecordingRoute

fun appleSongMenuItems(
    songId: String,
    navController: NavController,
    onDismiss: () -> Unit,
): Array<Material3MenuItemData> = arrayOf(
    Material3MenuItemData(
        title = { Text(text = stringResource(R.string.menu_pick_recording)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.manage_search),
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            navController.navigate(pickRecordingRoute(songId))
        },
    ),
    Material3MenuItemData(
        title = { Text(text = stringResource(R.string.menu_match_apple)) },
        icon = {
            Icon(
                painter = painterResource(R.drawable.sync),
                contentDescription = null,
            )
        },
        onClick = {
            onDismiss()
            navController.navigate(appleMatchRoute(songId))
        },
    ),
)
