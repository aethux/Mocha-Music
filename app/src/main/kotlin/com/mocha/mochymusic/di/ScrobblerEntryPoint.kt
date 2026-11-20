package com.mocha.mochymusic.di

import com.mocha.mochymusic.scrobble.Scrobbler
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** for WorkManager's scrobble sender, which Hilt doesn't build */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ScrobblerEntryPoint {
    fun scrobbler(): Scrobbler
}
