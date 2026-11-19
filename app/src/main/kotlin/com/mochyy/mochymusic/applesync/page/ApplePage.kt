// ApplePage.kt

package com.mochyy.mochymusic.applesync.page

/**
 * A live music.apple.com page, and the only way this app is allowed to write.
 *
 * Reads go through [com.mochyy.mochymusic.applesync.AppleClient]; writes happen inside the page,
 * where the web player's own MusicKit client and session already are. [eval] runs a script and waits
 * for it to call back, because everything worth doing there is asynchronous.
 */
interface ApplePage {
    suspend fun eval(script: String): String?

    /** A script's own return value, for diagnostics. Null when the transport cannot report one. */
    suspend fun value(script: String): String? = null
}
