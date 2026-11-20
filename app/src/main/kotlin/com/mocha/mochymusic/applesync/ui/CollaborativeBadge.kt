// CollaborativeBadge.kt
//
// The mark for a playlist more than one person can edit.
//
// Apple's API does not name the other people: the library resource carries only
// `hasCollaboration`, the catalogue resource exposes `curator` (the owner's social profile) and no
// collaborators relationship, and `/collaborators` answers 400. So the badge says what Apple does
// say - that the playlist is shared - rather than inventing names.

package com.mocha.mochymusic.applesync.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mocha.mochymusic.R
import com.mocha.mochymusic.ui.screens.search.SearchColors

@Composable
fun CollaborativeBadge(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    Row(
        modifier = modifier
            .height(24.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .clip(RoundedCornerShape(50))
            .background(SearchColors.Tile)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.group_filled),
            contentDescription = null,
            tint = SearchColors.Primary,
            modifier = Modifier.size(13.dp),
        )
        Text(
            text = "Collaborative",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = SearchColors.Primary,
        )
    }
}
