// AppleSessionViewModel.kt

package com.mocha.mochymusic.ui.applesync

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import org.json.JSONObject
import com.mocha.mochymusic.applesync.AppleTokens
import com.mocha.mochymusic.applesync.AppleWritePrefs
import com.mocha.mochymusic.applesync.AppleLibraryWrites
import com.mocha.mochymusic.applesync.AppleWrites
import com.mocha.mochymusic.applesync.db.AppleSyncDao
import com.mocha.mochymusic.applesync.page.ApplePage
import com.mocha.mochymusic.applesync.page.ApplePageScripts
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The full-screen session, in the same shape as the importer's: a service's own page, its own header,
 * and a line of text saying what is happening.
 *
 * For now it only proves the page, because the page is the transport every later write will use and
 * a transport that has never been exercised is not a transport.
 */
@HiltViewModel
class AppleSessionViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: AppleSyncDao,
    private val tokens: AppleTokens,
) : ViewModel() {

    var storefront by mutableStateOf("us")
        private set

    var status by mutableStateOf("Opening music.apple.com…")
        private set

    var probe by mutableStateOf<String?>(null)
        private set

    var writesEnabled by mutableStateOf(false)
        private set

    var sandboxLines by mutableStateOf<List<String>>(emptyList())
        private set

    var sandboxRunning by mutableStateOf(false)
        private set

    /** The stage the next tap will run; null means nothing is in flight or the run is finished. */
    var sandboxNext by mutableStateOf<String?>(null)
        private set

    private var stepper: AppleWrites? = null

    var libraryNext by mutableStateOf<String?>(null)
        private set

    var libraryLines by mutableStateOf<List<String>>(emptyList())
        private set

    var libraryRunning by mutableStateOf(false)
        private set

    private var libraryStepper: AppleLibraryWrites? = null

    private var page: ApplePage? = null
    private var inFlight = false

    init {
        viewModelScope.launch {
            dao.account().first()?.storefront?.takeIf { it.isNotBlank() }?.let { storefront = it }
            writesEnabled = AppleWritePrefs.enabled(context)
        }
    }

    private suspend fun refreshTokens(page: ApplePage) {
        val raw = runCatching { page.eval(ApplePageScripts.sessionTokens()) }.getOrNull() ?: return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return
        val developer = json.optString("developer")
        val user = json.optString("user")
        if (developer.isNotBlank() && user.isNotBlank()) {
            tokens.save(developer, user)
            status = "Session tokens refreshed from the page."
        }
    }

    /** The confirmation lives on the screen; this only records the answer. */
    fun setWrites(on: Boolean) {
        viewModelScope.launch {
            AppleWritePrefs.set(context, on)
            writesEnabled = on
        }
    }

    /**
     * The sandbox run: create, add, replace, delete, clean up. Every step is guarded and audited, and
     * it is the only write path that exists until the engine is trusted with real playlists.
     */
    /**
     * Prepares a run and stops. Nothing is written until the first tap, so the run can be watched
     * stage by stage in the Apple Music app.
     */
    fun startSandbox() {
        val live = page ?: run {
            sandboxLines = listOf("Open the page first.")
            return
        }
        if (sandboxRunning) return
        sandboxRunning = true
        sandboxLines = listOf("ready")
        viewModelScope.launch {
            val pair = tokens.read()
            if (pair == null) {
                sandboxLines = listOf("Sign in to Apple Music first.")
                sandboxRunning = false
                return@launch
            }
            stepper = AppleWrites(live, dao, pair, writesEnabled)
            sandboxNext = stepper?.nextLabel
            sandboxRunning = false
        }
    }

    /**
     * Records what the page sends when the user taps a heart in it. Reading the real request beats
     * trying body shapes one at a time, and it is the only way to learn the exact one.
     */
    fun startWatching() {
        val live = page ?: run {
            libraryLines = listOf("Open the page first.")
            return
        }
        viewModelScope.launch {
            val result = runCatching { live.value(ApplePageScripts.installCapture()) }.getOrNull()
            libraryLines = listOf("watching: $result", "heart a song, then tap Read")
        }
    }

    fun describePage() {
        val live = page ?: return
        viewModelScope.launch {
            val raw = runCatching { live.value(ApplePageScripts.describePage()) }.getOrNull()
            val json = runCatching { org.json.JSONObject(raw ?: "{}") }.getOrNull()
            if (json == null) {
                libraryLines = listOf("the page did not describe itself")
                return@launch
            }
            val out = mutableListOf("page: ${json.optString("title").take(60)}")
            out += "href: ${json.optString("href").take(80)}"
            val items = json.optJSONArray("items")
            out += "clickable: ${json.optInt("count")}"
            if (items != null) for (i in 0 until minOf(items.length(), 12)) out += "  ${items.optString(i).take(70)}"
            val likes = json.optJSONArray("likeCandidates")
            if (likes != null) for (i in 0 until likes.length()) out += "heart? ${likes.optString(i).take(60)}"
            libraryLines = out
        }
    }

    fun tapLike() {
        val live = page ?: return
        viewModelScope.launch {
            // the tape is installed here rather than left to the user: the click and the recorder
            // have to be in that order, and a wrong order looks exactly like "nothing happened"
            runCatching { live.value(ApplePageScripts.installCapture()) }
            val result = runCatching { live.value(ApplePageScripts.tapLike()) }.getOrNull()
            libraryLines = listOf("${result ?: "no answer"} — now tap Read what it sent")
        }
    }

    fun readCapture() {
        val live = page ?: return
        viewModelScope.launch {
            val raw = runCatching { live.value(ApplePageScripts.drainCapture()) }.getOrNull()
            val entries = runCatching { org.json.JSONArray(raw ?: "[]") }.getOrNull()
            if (entries == null || entries.length() == 0) {
                libraryLines = listOf("nothing captured yet; tap a heart in the page, then read again")
                return@launch
            }
            val out = mutableListOf<String>()
            for (i in 0 until entries.length()) {
                val item = entries.optJSONObject(i) ?: continue
                out += "${item.optString("method")} ${item.optString("url").take(110)}"
                val body = item.optString("body")
                if (body.isNotBlank() && body != "null") out += "   body: ${body.take(170)}"
            }
            libraryLines = out
        }
    }

    /** The library and favourites run, stepped the same way. */
    fun startLibrary() {
        val live = page ?: run {
            libraryLines = listOf("Open the page first.")
            return
        }
        if (libraryRunning) return
        libraryRunning = true
        libraryLines = listOf("ready")
        viewModelScope.launch {
            val pair = tokens.read()
            if (pair == null) {
                libraryLines = listOf("Sign in to Apple Music first.")
                libraryRunning = false
                return@launch
            }
            libraryStepper = AppleLibraryWrites(live, dao, pair, writesEnabled).also { run ->
                run.onProgress = { line -> libraryLines = libraryLines + line }
            }
            libraryNext = libraryStepper?.nextLabel
            libraryRunning = false
        }
    }

    fun advanceLibrary() {
        val run = libraryStepper ?: return
        if (libraryRunning) return
        libraryRunning = true
        viewModelScope.launch {
            val report = run.next()
            libraryLines = report.lines
            libraryNext = if (run.finished) null else run.nextLabel
            libraryRunning = false
        }
    }

    /** Runs exactly one stage, then re-labels the button for the one after it. */
    fun advanceSandbox() {
        val run = stepper ?: return
        if (sandboxRunning) return
        sandboxRunning = true
        viewModelScope.launch {
            val report = run.next()
            sandboxLines = report.lines
            sandboxNext = if (run.finished) null else run.nextLabel
            sandboxRunning = false
        }
    }

    fun onPage(page: ApplePage?) {
        this.page = page
        if (page == null) {
            inFlight = false
            status = "Page closed"
            return
        }
        if (inFlight) return
        inFlight = true
        status = "Page ready. Checking what the page can see…"
        viewModelScope.launch {
            val bridge = runCatching { page.value("typeof MochyAppleBridge") }.getOrNull()
            status = "Bridge on the page: $bridge. Asking MusicKit for the session…"
            // the page's own session is the freshest one there is: sign in on the phone and the
            // stored user token is dead, which reads as an empty library rather than an auth error
            refreshTokens(page)
            val answer = page.eval(ApplePageScripts.probe())
            probe = answer
            status = when {
                answer == null -> "The page didn't answer (bridge: $bridge). It may still be loading."
                answer.contains("\"status\":200") ->
                    "Page ready: MusicKit is signed in and reaching Apple (bridge: $bridge)."
                else -> "The page answered, but not with a session (bridge: $bridge). See below."
            }
            inFlight = false
        }
    }
}
