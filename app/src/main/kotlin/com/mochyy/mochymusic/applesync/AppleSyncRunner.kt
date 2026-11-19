// AppleSyncRunner.kt

package com.mochyy.mochymusic.applesync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Runs the sync on a scope that does not belong to a screen.
 *
 * A refresh tied to `rememberCoroutineScope` is cancelled the moment the user leaves the library,
 * which cancels the HTTP calls mid-flight and surfaces as an error. This scope lives as long as the
 * process, so navigating away leaves the sync running. It does not survive the process being killed:
 * that needs a foreground service, and is the next step up.
 */
object AppleSyncRunner {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = AtomicBoolean(false)
    private var job: Job? = null

    val isRunning: Boolean get() = running.get()

    /** Starts [work] unless something is already running. Returns false when it declined. */
    fun start(work: suspend () -> Unit): Boolean {
        if (!running.compareAndSet(false, true)) return false
        job = scope.launch {
            try {
                work()
            } catch (e: Exception) {
                AppleSyncStatus.fail(e.message ?: "The sync stopped.")
            } finally {
                running.set(false)
            }
        }
        return true
    }

    /** Only for a user-initiated cancel; leaving the screen must not call this. */
    fun cancel() {
        job?.cancel()
        running.set(false)
    }
}
