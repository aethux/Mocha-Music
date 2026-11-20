// AppleStorefronts.kt
//
// The Apple Music storefront the session page opens in. Apple answers a storefront that is not the
// address it sees with "choose another country" instead of the library, so this is a setting rather
// than something guessed: the chosen code wins, then the account's own storefront, then the bare
// domain and Apple picks.

package com.mocha.mochymusic.applesync

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.mocha.mochymusic.utils.dataStore
import kotlinx.coroutines.flow.first

object AppleStorefronts {
    /** the storefronts people actually pick from; anything Apple knows is still honoured in the URL */
    val regions: List<Pair<String, String>> =
        listOf(
            "in" to "India",
            "us" to "United States",
            "gb" to "United Kingdom",
            "ca" to "Canada",
            "au" to "Australia",
            "de" to "Germany",
            "fr" to "France",
            "nl" to "Netherlands",
            "es" to "Spain",
            "it" to "Italy",
            "se" to "Sweden",
            "no" to "Norway",
            "dk" to "Denmark",
            "fi" to "Finland",
            "pl" to "Poland",
            "br" to "Brazil",
            "mx" to "Mexico",
            "jp" to "Japan",
            "kr" to "South Korea",
            "cn" to "China",
            "sg" to "Singapore",
            "ae" to "United Arab Emirates",
            "sa" to "Saudi Arabia",
            "za" to "South Africa",
            "tr" to "Türkiye",
            "id" to "Indonesia",
            "ph" to "Philippines",
            "th" to "Thailand",
            "my" to "Malaysia",
        )

    private val Key = stringPreferencesKey("appleStorefront")

    fun name(code: String?): String? = regions.firstOrNull { it.first == code }?.second

    suspend fun read(context: Context): String? =
        context.dataStore.data.first()[Key]?.takeIf { it.length == 2 }

    /** null means "let Apple decide", which is what a first run wants */
    suspend fun save(context: Context, code: String?) {
        context.dataStore.edit { prefs ->
            if (code == null) prefs.remove(Key) else prefs[Key] = code
        }
    }
}
