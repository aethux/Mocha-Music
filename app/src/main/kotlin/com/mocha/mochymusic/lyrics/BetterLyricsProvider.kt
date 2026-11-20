// BetterLyricsProvider.kt

package com.mocha.mochymusic.lyrics

import android.content.Context
import com.mocha.mochymusic.betterlyrics.BetterLyrics
import com.mocha.mochymusic.constants.EnableBetterLyricsKey
import com.mocha.mochymusic.utils.dataStore
import com.mocha.mochymusic.utils.get

object BetterLyricsProvider : LyricsProvider {
    override val name = "BetterLyrics"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableBetterLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = BetterLyrics.getLyrics(title, artist, duration, album)

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
        callback: (String) -> Unit,
    ) {
        BetterLyrics.getAllLyrics(title, artist, duration, album, callback)
    }
}
