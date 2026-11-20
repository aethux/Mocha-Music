// SimilarRecommendation.kt

package com.mocha.mochymusic.models

import com.music.innertube.models.YTItem
import com.mocha.mochymusic.db.entities.LocalItem

data class SimilarRecommendation(
    val title: LocalItem,
    val items: List<YTItem>,
)
