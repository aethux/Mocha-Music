// AppleSongWritesEntryPoint.kt

package com.mocha.mochymusic.applesync.di

import com.mocha.mochymusic.applesync.AppleTokens
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import com.mocha.mochymusic.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * What one tap on a heart or a library button needs: the tokens for Apple, the sidecar for the guard
 * and the audit, and the app's own database for the row that follows Apple's answer.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppleSongWritesEntryPoint {
    fun dao(): AppleSyncDao
    fun tokens(): AppleTokens
    fun database(): MusicDatabase
}
