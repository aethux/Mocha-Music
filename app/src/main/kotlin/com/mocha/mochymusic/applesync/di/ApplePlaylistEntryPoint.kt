// ApplePlaylistEntryPoint.kt

package com.mocha.mochymusic.applesync.di

import com.mocha.mochymusic.applesync.ApplePlaylistLinker
import com.mocha.mochymusic.applesync.AppleTokens
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import com.mocha.mochymusic.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** What a playlist menu needs, without threading a ViewModel through the menu. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ApplePlaylistEntryPoint {
    fun dao(): AppleSyncDao
    fun tokens(): AppleTokens
    fun database(): MusicDatabase
}
