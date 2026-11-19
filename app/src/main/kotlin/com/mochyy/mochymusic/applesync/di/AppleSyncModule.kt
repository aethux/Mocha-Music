// AppleSyncModule.kt

package com.mochyy.mochymusic.applesync.di

import android.content.Context
import com.mochyy.mochymusic.applesync.AppleRematchStore
import com.mochyy.mochymusic.applesync.AppleTokens
import com.mochyy.mochymusic.applesync.db.AppleRematchEntity
import com.mochyy.mochymusic.applesync.db.AppleSyncDao
import com.mochyy.mochymusic.applesync.db.AppleSyncDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The sidecar's wiring. New code only: nothing here is an edit to an existing Hilt module, which is
 * why the first feature commit needs no seams.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppleSyncModule {

    @Provides
    @Singleton
    fun provideAppleSyncDatabase(@ApplicationContext context: Context): AppleSyncDatabase =
        AppleSyncDatabase.newInstance(context)

    @Provides
    @Singleton
    fun provideAppleSyncDao(database: AppleSyncDatabase): AppleSyncDao = database.dao

    @Provides
    @Singleton
    fun provideAppleTokens(@ApplicationContext context: Context): AppleTokens = AppleTokens(context)

    /**
     * The rematch table, narrowed to the five calls playback and the sheet make, so nothing on the
     * playback path holds the whole sidecar DAO.
     */
    @Provides
    @Singleton
    fun provideAppleRematchStore(dao: AppleSyncDao): AppleRematchStore =
        object : AppleRematchStore {
            override suspend fun rematches() = dao.rematches()
            override suspend fun rematch(songId: String) = dao.rematch(songId)
            override suspend fun rematchedSongIds(source: String) = dao.rematchedSongIds(source)
            override suspend fun saveRematch(row: AppleRematchEntity) = dao.saveRematch(row)
            override suspend fun clearRematch(songId: String) = dao.clearRematch(songId)
        }
}
