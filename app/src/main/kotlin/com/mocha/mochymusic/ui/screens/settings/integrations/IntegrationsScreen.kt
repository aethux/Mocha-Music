// mochy → settings → integrations: a row per service, always all of them, tapping one opens its own page

package com.mocha.mochymusic.ui.screens.settings.integrations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.scrobble.ScrobbleSite
import com.mocha.mochymusic.ui.applesync.AppleSyncRoute
import com.mocha.mochymusic.ui.component.SubSettingsScaffold
import com.mocha.mochymusic.ui.screens.settings.importsync.YouTubeSyncRoute
import com.mocha.mochymusic.ui.screens.settings.integrations.backup.BackupRoute
import com.mocha.mochymusic.ui.screens.settings.integrations.discord.DiscordRoute

const val IntegrationsRoute = "integrations_settings"

// the youtube music logo is a red disc, this fills its corners so it sits as a tile like the others
private val YouTubeMusicRed = Color(0xFFFF0000)

// MOCHY-APPLE: Apple Music's own red, so its row sits with the others as a logo, not a headset
private val AppleMusicRed = Color(0xFFFA243C)
// backup has no brand of its own, so its white cloud sits on a plain blue tile
private val BackupBlue = Color(0xFF2F7CF6)

@Composable
fun IntegrationsScreen(navController: NavController) {
    SubSettingsScaffold(
        title = stringResource(R.string.integrations),
        onBack = { navController.navigateUp() },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // MOCHY-APPLE: Discord is hidden until presence can be shown at all - the gateway
            // refuses an OAuth2 user token (4004), so the page could sign in and do nothing.
            IntegrationRow(
                name = ScrobbleSite.LASTFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LASTFM.label),
                icon = R.drawable.integration_lastfm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LASTFM)) },
            )
            IntegrationRow(
                name = ScrobbleSite.LIBREFM.label,
                description = stringResource(R.string.integration_scrobbling_desc, ScrobbleSite.LIBREFM.label),
                icon = R.drawable.integration_librefm,
                onClick = { navController.navigate(scrobbleServiceRoute(ScrobbleSite.LIBREFM)) },
            )
            IntegrationRow(
                name = stringResource(R.string.module_youtube_name),
                description = stringResource(R.string.module_youtube_desc),
                icon = R.drawable.provider_youtube_music,
                iconBackground = YouTubeMusicRed,
                onClick = { navController.navigate(YouTubeSyncRoute) },
            )
            // MOCHY-APPLE: read-only library audit; the sync itself lands here later
            IntegrationRow(
                name = "Apple Music",
                description = "Read-only library audit",
                icon = R.drawable.provider_apple_music,
                iconBackground = AppleMusicRed,
                onClick = { navController.navigate(AppleSyncRoute) },
            )

            IntegrationRow(
                name = stringResource(R.string.integration_backup_name),
                description = stringResource(R.string.integration_backup_desc),
                icon = R.drawable.backup,
                iconBackground = BackupBlue,
                iconPadding = 7.dp,
                onClick = { navController.navigate(BackupRoute) },
            )
        }

        Spacer(Modifier.height(140.dp))
    }
}
