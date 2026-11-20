// PlaylistOwnerCreators.kt
//
// The "by …" line on a playlist page.
//
// Upstream builds it from the YouTube account's name, which is why every Apple playlist read
// "by Guest": no YouTube account is signed in here, and an Apple playlist carries no name of its own
// in the library resource. The name lives in the playlist's catalogue counterpart (`curatorName`),
// which the import stores in the sidecar.
//
// Only people go on that line. A second entry for "others" read as a collaborator actually called
// Others, so the collaborative flag rides on the stats line instead: same row, no invented person.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import com.mocha.mochymusic.applesync.db.PlaylistCollaboratorEntity
import com.mocha.mochymusic.applesync.db.PlaylistMetaEntity
import com.mocha.mochymusic.applesync.di.ApplePlaylistEntryPoint
import com.mocha.mochymusic.ui.component.detail.CreatorUi
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APPLE_PLAYLIST_PREFIX = "AM_PL_"

/** Who to credit, and whether the playlist is shared. */
data class PlaylistCredit(
    val creators: List<CreatorUi>,
    /** True when someone other than the owner can add to it: drawn as a badge under the title. */
    val collaborative: Boolean = false,
)

@Composable
fun rememberPlaylistCredit(
    playlistId: String?,
    accountName: String,
    avatarUrl: String?,
): PlaylistCredit {
    val context = LocalContext.current
    val apple = playlistId?.startsWith(APPLE_PLAYLIST_PREFIX) == true
    val meta by produceState<PlaylistMetaEntity?>(initialValue = null, playlistId) {
        if (!apple || playlistId == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao().playlistMeta(playlistId)
            }.getOrNull()
        }
    }
    // names the web player's page listed, read once by the Collaborators screen
    val people by produceState<List<PlaylistCollaboratorEntity>>(initialValue = emptyList(), playlistId) {
        if (!apple || playlistId == null) return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dao = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    ApplePlaylistEntryPoint::class.java,
                ).dao()
                val name = dao.link(playlistId)?.remoteName
                val self = com.mocha.mochymusic.applesync.PlaylistCollaborators.selfName(dao.playlistMetas())
                // the page's own title carries an alt too, and this account is not an "other"
                dao.collaborators(playlistId).filterNot {
                    it.name.equals(name, ignoreCase = true) || it.name.equals(self, ignoreCase = true)
                }
            }.getOrDefault(emptyList())
        }
    }

    if (!apple) {
        val creators = if (accountName.isNotBlank()) {
            listOf(CreatorUi(name = accountName, avatarUrl = avatarUrl))
        } else {
            emptyList()
        }
        return PlaylistCredit(creators)
    }

    val owner = meta?.ownerName ?: return PlaylistCredit(emptyList())
    // the owner first, then whoever the page said shares it
    val creators = buildList {
        add(CreatorUi(name = owner))
        people.forEach { add(CreatorUi(name = it.name)) }
    }
    return PlaylistCredit(
        creators = creators,
        collaborative = meta?.collaborative == true,
    )
}
