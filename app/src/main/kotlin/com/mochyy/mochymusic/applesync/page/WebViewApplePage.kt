// WebViewApplePage.kt

package com.mochyy.mochymusic.applesync.page

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/**
 * The page transport over a WebView that is already showing music.apple.com.
 *
 * Two details decide whether this works at all:
 *  - a JavascriptInterface added after the page has loaded is not visible to that page's script
 *    context, so attaching it means reloading once. Without the reload every answer is thrown away
 *    and the transport looks dead.
 *  - scripts answer through the bridge rather than a return value, so a promise or a retry loop
 *    inside the page can take as long as it likes; [eval] still gives up after fifteen seconds
 *    instead of hanging a sync.
 */
class WebViewApplePage(private val webView: WebView) : ApplePage {

    private val results = ConcurrentHashMap<String, CompletableDeferred<String>>()
    private val counter = AtomicInteger()

    init {
        // must happen before loadUrl: an interface added to a page that is already loaded is not
        // visible to that document's scripts, which is what made the page look like a dead transport
        webView.addJavascriptInterface(Bridge(), BRIDGE_NAME)
    }

    @SuppressLint("JavascriptInterface")
    private inner class Bridge {
        @JavascriptInterface
        fun done(id: String?, payload: String?) {
            val waiting = id?.let(results::remove) ?: return
            waiting.complete(payload ?: "null")
        }
    }

    override suspend fun eval(script: String): String? {
        val id = "mochy${counter.incrementAndGet()}"
        val answer = CompletableDeferred<String>()
        results[id] = answer
        val js = script.replace(IdPlaceholder, id)
        withContext(Dispatchers.Main) {
            webView.evaluateJavascript(js) { /* the answer arrives through the bridge */ }
        }
        return withTimeoutOrNull(TimeoutMs) { answer.await() } ?: run {
            results.remove(id)
            null
        }
    }

    /** The script's own return value, for diagnostics: "object" means the bridge is on the page. */
    override suspend fun value(script: String): String? = withContext(Dispatchers.Main) {
        withTimeoutOrNull(5_000) {
            suspendCancellableCoroutine { cont ->
                webView.evaluateJavascript(script) { raw -> cont.resume(raw) }
            }
        }
    }

    companion object {
        const val BRIDGE_NAME = "MochyAppleBridge"
        const val IdPlaceholder = "__MOCHY_ID__"
        private const val TimeoutMs = 15_000L
    }
}
