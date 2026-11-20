// AppleWritePrefs.kt

package com.mocha.mochymusic.applesync

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import com.mocha.mochymusic.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * The switch that lets this app change anything on Apple, off until the user says otherwise.
 *
 * It is its own key in our own namespace, so it is not a preference the rest of the app can read or
 * set, and nothing turns it on except the confirmation on the Apple Music page.
 */
object AppleWritePrefs {
    private val Key = booleanPreferencesKey("appleSyncWritesEnabled")

    suspend fun enabled(context: Context): Boolean = context.dataStore.data.first()[Key] ?: false

    suspend fun set(context: Context, on: Boolean) {
        context.dataStore.edit { it[Key] = on }
    }
}
