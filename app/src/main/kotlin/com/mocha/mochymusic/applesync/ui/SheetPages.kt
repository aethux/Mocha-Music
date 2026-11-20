// SheetPages.kt
//
// A menu opened from inside a menu: the same sheet, with the new menu drawn in place of what it
// showed, and Back returning to what was under it. PopupSheet owns the frame stack; the stack of
// pages belongs here, so the upstream file carries a call instead of a mechanism.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.mocha.mochymusic.R
import com.mocha.mochymusic.ui.component.PopupSheetState

/** The pages one sheet has taken on, on top of the content it was opened with. */
internal class SheetPages(private val base: @Composable ColumnScope.() -> Unit) {

    /**
     * A page is a menu, and a menu can carry a button bar of its own - "Create playlist" lives in
     * one. Drawing the page's content and leaving the bar behind is how the create sheet came up
     * with the picker's pill still under it, so the tap re-opened the sheet that was already open.
     */
    internal class Page(
        val buttonBar: (@Composable () -> Unit)?,
        val content: @Composable ColumnScope.() -> Unit,
    )

    private val stack: SnapshotStateList<Page> = mutableStateListOf()

    /** What the sheet draws: the last page, or its own content when nothing was pushed. */
    val content: @Composable ColumnScope.() -> Unit
        get() = stack.lastOrNull()?.content ?: base

    /** The bar the front page brought, so a nested sheet keeps its own action. */
    val buttonBar: (@Composable () -> Unit)?
        get() = stack.lastOrNull()?.buttonBar

    /** True while a page pushed over the base content is showing. */
    val hasPage: Boolean get() = stack.isNotEmpty()

    fun push(buttonBar: (@Composable () -> Unit)?, page: @Composable ColumnScope.() -> Unit) {
        stack.add(Page(buttonBar, page))
    }

    /** True when it stepped back a page; false when there was no page to step back from. */
    fun back(): Boolean {
        if (stack.isEmpty()) return false
        stack.removeAt(stack.lastIndex)
        return true
    }
}

/**
 * Shows [page] in this frame rather than in a frame of its own, when this frame is a plain menu.
 *
 * A locked, full-bleed or coloured sheet keeps its own frame: its position and its surface are the
 * point of it, and the menu behind it has nothing to return to.
 */
internal fun PopupSheetState.Frame.openAsPage(
    locked: Boolean,
    fullBleed: Boolean,
    surface: Color?,
    buttonBar: (@Composable () -> Unit)?,
    page: @Composable ColumnScope.() -> Unit,
): Boolean {
    if (locked || fullBleed || surface != null || this.locked || this.fullBleed) return false
    pages.push(buttonBar, page)
    return true
}

/** The back arrow a sheet shows while a page is up, and nothing at all when it is on its own. */
@Composable
internal fun SheetPageBack(pages: SheetPages) {
    if (!pages.hasPage) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .clickable { pages.back() }
            .padding(vertical = 8.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.arrow_back),
            contentDescription = "Back",
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}
