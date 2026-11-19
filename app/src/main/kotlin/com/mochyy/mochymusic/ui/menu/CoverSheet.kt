// CoverSheet.kt
//
// A cover the person picks themselves: one off their phone, or an image link, and "Use Apple's
// cover" to hand it back. The override is what makes the choice survive an import; the row it
// belongs to is written at the same time, so the change shows without waiting for a sync.

package com.mochyy.mochymusic.ui.menu

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mochyy.mochymusic.R
import com.mochyy.mochymusic.applesync.CoverOverrides
import com.mochyy.mochymusic.applesync.ui.CoverImages
import com.mochyy.mochymusic.ui.player.menu.MenuRow
import com.mochyy.mochymusic.ui.component.SheetSearchField
import com.mochyy.mochymusic.ui.component.TopToaster
import kotlinx.coroutines.launch

/**
 * @param onApplied the value that is now the cover - a file or an image link - or null when it
 * went back to Apple's
 */
@Composable
fun CoverSheet(
    kind: CoverOverrides.Kind,
    id: String,
    onDismiss: () -> Unit,
    onApplied: (String?) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember(id) { mutableStateOf("") }
    var busy by remember(id) { mutableStateOf(false) }

    // the system photo picker: no storage permission, and the copy we keep is our own file
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val stored = CoverImages.store(context, kind, id, uri)
            if (stored != null) {
                CoverOverrides.set(context, kind, id, stored)
                onApplied(stored)
                TopToaster.show(text = context.getString(R.string.cover_set))
                onDismiss()
            } else {
                TopToaster.error(context.getString(R.string.cover_image_failed))
            }
            busy = false
        }
    }

    Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text(
            text = stringResource(
                when (kind) {
                    CoverOverrides.Kind.PLAYLIST -> R.string.cover_sheet_title
                    CoverOverrides.Kind.SONG -> R.string.cover_sheet_song
                    CoverOverrides.Kind.ARTIST -> R.string.cover_sheet_artist
                    CoverOverrides.Kind.ALBUM -> R.string.cover_sheet_album
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 8.dp, bottom = 10.dp),
        )

        // the same rows a menu uses: a cover is an action, not a form
        MenuRow(
            icon = R.drawable.image,
            title = stringResource(R.string.cover_sheet_pick),
            enabled = !busy,
            onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            },
        )

        Spacer(Modifier.height(10.dp))

        SheetSearchField(
            value = url,
            onValueChange = { url = it },
            placeholder = stringResource(R.string.cover_sheet_url),
        )
        Spacer(Modifier.height(10.dp))

        MenuRow(
            icon = R.drawable.link,
            title = stringResource(R.string.cover_sheet_use),
            enabled = url.isNotBlank(),
            onClick = {
                val chosen = url.trim()
                scope.launch {
                    // a stored file is ours to delete; a link is not
                    if (CoverImages.isStored(context, CoverOverrides.get(context, kind, id))) {
                        CoverImages.forget(context, kind, id)
                    }
                    CoverOverrides.set(context, kind, id, chosen)
                    onApplied(chosen)
                    TopToaster.show(text = context.getString(R.string.cover_set))
                    onDismiss()
                }
            },
        )

        MenuRow(
            icon = R.drawable.apple_music_me,
            title = stringResource(R.string.cover_sheet_apple),
            enabled = true,
            onClick = {
                scope.launch {
                    if (CoverImages.isStored(context, CoverOverrides.get(context, kind, id))) {
                        CoverImages.forget(context, kind, id)
                    }
                    CoverOverrides.clear(context, kind, id)
                    onApplied(null)
                    TopToaster.show(text = context.getString(R.string.cover_reverted))
                    onDismiss()
                }
            },
        )
    }
}
