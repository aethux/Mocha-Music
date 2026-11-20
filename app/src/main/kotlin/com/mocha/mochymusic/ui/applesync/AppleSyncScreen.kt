// AppleSyncScreen.kt

package com.mocha.mochymusic.ui.applesync

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.mocha.mochymusic.R
import com.mocha.mochymusic.applesync.AppleAuditReport
import com.mocha.mochymusic.ui.component.AppSwitch
import com.mocha.mochymusic.ui.component.DefaultDialog
import com.mocha.mochymusic.ui.component.SettingsGroup
import com.mocha.mochymusic.ui.component.SettingsGroupStyle
import com.mocha.mochymusic.ui.component.SettingsItem
import com.mocha.mochymusic.ui.component.SubSettingsScaffold
import com.mocha.mochymusic.ui.screens.settings.importsync.ImportInfoCard
import java.text.DateFormat
import java.util.Date

const val AppleSyncRoute = "apple_sync"

/**
 * Settings → Integrations → Apple Music.
 *
 * Built the way the YouTube Music page is built: the same scaffold, the same grouped setting rows,
 * the same information card for anything that is running or has just finished. A new integration
 * that invents its own layout reads as a different app, and this one has no reason to.
 */
@Composable
fun AppleSyncScreen(navController: NavController) {
    val viewModel: AppleSyncViewModel = hiltViewModel()
    val state = viewModel.state
    val log by viewModel.auditLog.collectAsState()

    var confirmSignOut by remember { mutableStateOf(false) }
    var confirmWrites by remember { mutableStateOf(false) }
    // MOCHY-APPLE: the cover cache, and the one action that makes the next import fetch again
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val signedIn = state !is AppleSyncViewModel.State.SignedOut

    SubSettingsScaffold(
        title = stringResource(R.string.apple_sync_title),
        onBack = { navController.navigateUp() },
        backLabel = "Integrations",
    ) {
        SettingsGroup(
            title = stringResource(R.string.apple_sync_account_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    if (signedIn) {
                        SettingsItem(
                            title = { Text(viewModel.accountLabel ?: stringResource(R.string.apple_sync_account_header)) },
                            descriptionText = accountLine(state),
                            icon = painterResource(R.drawable.account),
                            iconShape = CircleShape,
                            trailingContent = {
                                TextButton(onClick = { confirmSignOut = true }) {
                                    Text(stringResource(R.string.apple_sync_sign_out))
                                }
                            },
                        )
                    } else {
                        SettingsItem(
                            title = { Text(stringResource(R.string.apple_sync_sign_in_title)) },
                            descriptionText = stringResource(R.string.apple_sync_sign_in_desc),
                            icon = painterResource(R.drawable.login),
                            iconShape = CircleShape,
                            onClick = { navController.navigate(AppleSessionRoute) },
                        )
                    },
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.apple_sync_changes_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    toggleItem(
                        title = stringResource(R.string.apple_sync_allow_title),
                        description = stringResource(R.string.apple_sync_allow_desc),
                        icon = R.drawable.sync,
                        checked = viewModel.writesEnabled,
                        enabled = signedIn,
                        onChange = { on -> if (on) confirmWrites = true else viewModel.setWrites(false) },
                    ),
                ),
        )

        Spacer(Modifier.height(16.dp))

        SettingsGroup(
            title = stringResource(R.string.apple_sync_now_header),
            style = SettingsGroupStyle.Grouped,
            items =
                listOf(
                    SettingsItem(
                        title = { Text(stringResource(R.string.apple_sync_check_title)) },
                        descriptionText = stringResource(R.string.apple_sync_check_desc),
                        icon = painterResource(R.drawable.refresh),
                        iconShape = CircleShape,
                        enabled = signedIn && state !is AppleSyncViewModel.State.Running,
                        onClick = viewModel::runAudit,
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.apple_playlists_import)) },
                        descriptionText = stringResource(R.string.apple_playlists_import_desc),
                        icon = painterResource(R.drawable.playlist_add),
                        iconShape = CircleShape,
                        enabled = signedIn && state !is AppleSyncViewModel.State.Running,
                        onClick = {
                            scope.launch {
                                val result = com.mocha.mochymusic.applesync.ApplePlaylistImport.run(context)
                                com.mocha.mochymusic.ui.component.TopToaster.show(
                                    text = context.getString(
                                        R.string.apple_playlists_imported,
                                        result.playlists,
                                        result.songs,
                                    ),
                                )
                            }
                        },
                    ),
                    SettingsItem(
                        title = { Text(stringResource(R.string.apple_covers_clear)) },
                        descriptionText = stringResource(R.string.apple_covers_clear_desc),
                        icon = painterResource(R.drawable.image),
                        iconShape = CircleShape,
                        onClick = {
                            scope.launch {
                                com.mocha.mochymusic.applesync.AppleCovers.clear(context)
                                com.mocha.mochymusic.ui.component.TopToaster.show(
                                    text = context.getString(R.string.apple_covers_cleared),
                                )
                            }
                        },
                    ),
                ),
        )

        if (state is AppleSyncViewModel.State.Running || state is AppleSyncViewModel.State.Done ||
            state is AppleSyncViewModel.State.Failed
        ) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                when (val current = state) {
                    is AppleSyncViewModel.State.Running -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp)
                            Spacer(Modifier.width(14.dp))
                            Text(
                                text = stringResource(R.string.apple_sync_reading),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }

                    is AppleSyncViewModel.State.Done -> Report(current.report)
                    is AppleSyncViewModel.State.Failed -> {
                        Text(
                            text = current.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    else -> Unit
                }
            }
        }

        if (log.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            ImportInfoCard {
                Text(
                    text = stringResource(R.string.apple_sync_history_header),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                for (row in log.take(6)) {
                    val when_ = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                        .format(Date(row.at))
                    val what =
                        if (row.op == "AUDIT_FIRST_RUN") {
                            stringResource(R.string.apple_sync_baseline_taken)
                        } else {
                            row.op.lowercase().replace('_', ' ')
                        }
                    val verdict =
                        if (row.verified == false) {
                            stringResource(R.string.apple_sync_changed)
                        } else {
                            stringResource(R.string.apple_sync_unchanged)
                        }
                    Text(
                        text = "$when_ · $what · $verdict",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(140.dp))
    }

    if (confirmSignOut) {
        DefaultDialog(
            onDismiss = { confirmSignOut = false },
            title = { Text(stringResource(R.string.apple_sync_sign_out)) },
            buttons = {
                TextButton(onClick = { confirmSignOut = false }) {
                    Text(stringResource(R.string.yt_sync_cancel))
                }
                TextButton(
                    onClick = {
                        confirmSignOut = false
                        viewModel.signOut()
                    },
                ) {
                    Text(stringResource(R.string.apple_sync_sign_out))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.apple_sync_sign_out_confirm),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }

    if (confirmWrites) {
        DefaultDialog(
            onDismiss = { confirmWrites = false },
            title = { Text(stringResource(R.string.apple_sync_allow_title)) },
            buttons = {
                TextButton(onClick = { confirmWrites = false }) {
                    Text(stringResource(R.string.yt_sync_cancel))
                }
                TextButton(
                    onClick = {
                        confirmWrites = false
                        viewModel.setWrites(true)
                    },
                ) {
                    Text(stringResource(R.string.import_sync_btn_ok))
                }
            },
        ) {
            Text(
                text = stringResource(R.string.apple_sync_allow_confirm),
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

private fun accountLine(state: AppleSyncViewModel.State): String = when (state) {
    is AppleSyncViewModel.State.Done ->
        "${state.report.storefront.uppercase()} · ${state.report.songs} songs"
    is AppleSyncViewModel.State.Running -> "Reading…"
    else -> "Connected"
}

@Composable
private fun Report(report: AppleAuditReport) {
    Text(
        text = stringResource(
            if (report.trusted) R.string.apple_sync_untouched else R.string.apple_sync_drift,
        ),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = if (report.trusted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = stringResource(
            R.string.apple_sync_counts,
            report.songs,
            report.playlists,
            report.storefront.uppercase(),
        ),
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        text = stringResource(R.string.apple_sync_baseline, report.epochSize),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (report.addedSinceEpoch.isNotEmpty()) {
        Text(
            text = stringResource(R.string.apple_sync_added, report.addedSinceEpoch.size),
            style = MaterialTheme.typography.bodySmall,
        )
    }
    if (report.removedSinceEpoch.isNotEmpty()) {
        Text(
            text = stringResource(R.string.apple_sync_missing, report.removedSinceEpoch.size),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun toggleItem(
    title: String,
    description: String,
    icon: Int,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
): SettingsItem = SettingsItem(
    title = { Text(title) },
    descriptionText = description,
    icon = painterResource(icon),
    iconShape = CircleShape,
    enabled = enabled,
    trailingContent = { AppSwitch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
)
