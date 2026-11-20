// AppleTokens.kt

package com.mocha.mochymusic.applesync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mocha.mochymusic.utils.SecretVault
import com.mocha.mochymusic.utils.dataStore
import kotlinx.coroutines.flow.first

/**
 * The two tokens Apple's web player uses, kept the way the rest of the app keeps secrets.
 *
 * `SecretVault` seals them before they reach disk and opens them on read. The keys are ours rather
 * than entries in `SecretPreferences.SecretKeys`, because that list is an upstream file and adding to
 * it would be a seam; sealing here gets the same result without the edit.
 */
class AppleTokens(private val context: Context) {

    data class Pair(val developer: String, val user: String) {
        val headers: Map<String, String>
            get() = mapOf(
                "Authorization" to "Bearer $developer",
                "Media-User-Token" to user,
                "Origin" to "https://music.apple.com",
                "Referer" to "https://music.apple.com/",
                "Accept" to "application/json",
            )
    }

    suspend fun read(): Pair? {
        val prefs = context.dataStore.data.first()
        val developer = prefs[DeveloperKey]?.let(SecretVault::open)?.takeIf(String::isNotBlank) ?: return null
        val user = prefs[UserKey]?.let(SecretVault::open)?.takeIf(String::isNotBlank) ?: return null
        return Pair(developer, user)
    }

    suspend fun save(developer: String, user: String) {
        context.dataStore.edit { prefs ->
            prefs[DeveloperKey] = SecretVault.seal(developer.trim())
            prefs[UserKey] = SecretVault.seal(user.trim())
        }
    }

    suspend fun clear() {
        context.dataStore.edit { prefs ->
            prefs.remove(DeveloperKey)
            prefs.remove(UserKey)
        }
    }

    private companion object {
        val DeveloperKey = stringPreferencesKey("appleSyncDeveloperToken")
        val UserKey = stringPreferencesKey("appleSyncUserToken")
    }
}
