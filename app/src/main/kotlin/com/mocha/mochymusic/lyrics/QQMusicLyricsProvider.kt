// QQMusicLyricsProvider.kt

package com.mocha.mochymusic.lyrics

import android.content.Context
import com.music.directlyrics.QQMusic
import com.mocha.mochymusic.constants.EnableQQMusicKey
import com.mocha.mochymusic.utils.dataStore
import com.mocha.mochymusic.utils.get

object QQMusicLyricsProvider : LyricsProvider {
    override val name = "QQMusic"
    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableQQMusicKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        duration: Int,
        album: String?,
    ): Result<String> = QQMusic.getLyrics(title, artist, duration)
}
