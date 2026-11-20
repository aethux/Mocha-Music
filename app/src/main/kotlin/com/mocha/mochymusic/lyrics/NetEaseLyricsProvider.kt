// NetEaseLyricsProvider.kt

package com.mocha.mochymusic.lyrics

import android.content.Context
import com.music.directlyrics.NetEase
import com.mocha.mochymusic.constants.EnableNetEaseKey
import com.mocha.mochymusic.utils.dataStore
import com.mocha.mochymusic.utils.get

object NetEaseLyricsProvider : LyricsProvider {
    override val name = "NetEase"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableNetEaseKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = NetEase.getLyrics(title, artist, duration)
}
