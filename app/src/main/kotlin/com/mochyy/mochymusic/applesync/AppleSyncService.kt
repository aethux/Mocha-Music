// AppleSyncService.kt
//
// A refresh that keeps running when the app goes to the background, and says so.
//
// The sync already ran on a process-wide scope, which survives leaving the library screen - but not
// Android taking the process away once the app is hidden, which is exactly when a long read is
// interrupted. A foreground service is the only way to say "this is still doing something" and be
// believed. The notification is the progress report: the phase, the counts, a bar once the total is
// known, and the same sentence the card finishes with.

package com.mochyy.mochymusic.applesync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class AppleSyncService : Service() {

    companion object {
        private const val CHANNEL_ID = "apple_sync"
        private const val NOTIFICATION_ID = 4211

        /** Starts the refresh in the service, or does nothing if one is already running. */
        fun start(context: Context) {
            val intent = Intent(context, AppleSyncService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var poller: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        channel()
        startForeground(NOTIFICATION_ID, progress(AppleSyncStatus.Progress(phase = "Starting…")))

        val started = AppleSyncRunner.start { AppleSyncFlow.run(applicationContext) }
        if (!started) {
            // something is already syncing: report on that one rather than start a second
            AppleSyncStatus.update(phase = AppleSyncStatus.progress.phase.ifBlank { "Syncing…" })
        }

        poller?.cancel()
        poller = scope.launch {
            // AppleSyncRunner is the honest signal: the status object goes quiet between phases
            while (isActive && AppleSyncRunner.isRunning) {
                post(progress(AppleSyncStatus.progress))
                delay(600)
            }
            // one last look, so a finish that landed between two ticks is not missed
            post(done(AppleSyncStatus.progress))
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        poller?.cancel()
        super.onDestroy()
    }

    private fun channel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Apple Music sync", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Reading the Apple Music library, and what it found"
                setShowBadge(false)
            },
        )
    }

    private fun openApp(): PendingIntent {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?: Intent().setClassName(packageName, "$packageName.MainActivity")
        return PendingIntent.getActivity(
            this,
            0,
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** The bar fills on songs then playlists, and hides whenever the total is not known yet. */
    private fun progress(p: AppleSyncStatus.Progress): Notification {
        val counter = when {
            p.totalPlaylists > 0 -> "${p.playlists}/${p.totalPlaylists} playlists"
            p.totalSongs > 0 -> "${p.songs}/${p.totalSongs} songs"
            p.songs > 0 -> "${p.songs} songs"
            else -> null
        }
        val text = listOfNotNull(p.phase.ifBlank { "Syncing…" }, counter).joinToString(" · ")
        val fraction = p.fraction
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Syncing Apple Music")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (fraction == null) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(1000, (fraction * 1000).toInt(), false)
                    setSubText("${(fraction * 100).toInt()}%")
                }
            }
            .setContentIntent(openApp())
            .build()
    }

    /** The finish line: the same sentence the in-app card shows, kept until it is tapped away. */
    private fun done(p: AppleSyncStatus.Progress): Notification {
        val message = p.finishedMessage ?: if (AppleSyncRunner.isRunning) "Still running." else "Finished."
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(if (p.failed) "Apple Music sync stopped" else "Apple Music sync finished")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
    }

    private fun post(notification: Notification) {
        runCatching {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, notification)
        }
    }
}
