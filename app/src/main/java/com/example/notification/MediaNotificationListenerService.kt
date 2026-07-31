package com.example.notification

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.example.ScreenPulseApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * We don't actually need to read notification content — this service exists only
 * because Android requires an active NotificationListenerService binding before it
 * will grant MediaSessionManager.getActiveSessions(). Once connected, we track each
 * active MediaController's play/pause/stop transitions to log real "this app was
 * actively playing media" sessions, including while the screen is off — something
 * UsageStatsManager (foreground-only) can never see.
 *
 * This can only ever see sessions from the moment Notification Access is granted
 * onward; Android keeps no history of past media sessions to backfill from.
 */
class MediaNotificationListenerService : NotificationListenerService() {

    private val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    // packageName -> (open DB row id, the MediaController.Callback we registered for it)
    private val openSessions = mutableMapOf<String, Long>()
    private val registeredCallbacks = mutableMapOf<MediaController, MediaController.Callback>()

    private val sessionsChangedListener =
        MediaSessionManager.OnActiveSessionsChangedListener { controllers ->
            handleActiveSessions(controllers ?: emptyList())
        }

    override fun onListenerConnected() {
        super.onListenerConnected()
        scope.launch {
            // A previous process may have been killed mid-playback, leaving a row with
            // no endTime. Close those out now so they don't inflate future totals.
            (applicationContext as ScreenPulseApplication).repository.closeDanglingBackgroundMediaSessions()
        }
        try {
            val manager = getSystemService(MediaSessionManager::class.java)
            val componentName = ComponentName(this, MediaNotificationListenerService::class.java)
            manager.addOnActiveSessionsChangedListener(sessionsChangedListener, componentName)
            handleActiveSessions(manager.getActiveSessions(componentName))
        } catch (_: Throwable) {
            // If this ever throws (permission timing edge case on some OEMs), we simply
            // won't get background media data this session — never crash the listener.
        }
        startDanglingSessionSweeper()
    }

    /** BUG FIX: previously, a session only got marked "closed" via a real onPlaybackStateChanged
     *  callback or the one-time cleanup in onListenerConnected(). If this service's process was
     *  killed and restarted WITHOUT onListenerDisconnected() ever firing (common — OS process
     *  death isn't a graceful unbind), the in-memory openSessions map was lost but the DB row
     *  stayed open (endTime = NULL) indefinitely. Since our totals query treats a NULL endTime
     *  as "still ongoing right now", that stale row got counted as active in ANY later query —
     *  e.g. a session that actually ended at 9am appearing to overlap a completely unrelated
     *  11:52–14:03 selection. This periodic sweep closes anything not in our current in-memory
     *  tracking (i.e. genuinely abandoned rows) every 5 minutes, bounding the damage window. */
    private fun startDanglingSessionSweeper() {
        scope.launch {
            while (job.isActive) {
                kotlinx.coroutines.delay(5 * 60_000L)
                try {
                    (applicationContext as ScreenPulseApplication).repository
                        .closeDanglingBackgroundMediaSessions(openSessions.values.toList())
                } catch (_: Throwable) {
                }
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        // Close every still-open session — better to slightly undercount than to leave
        // a session "open" indefinitely if the listener gets unbound.
        val now = System.currentTimeMillis()
        openSessions.values.forEach { id ->
            scope.launch {
                (applicationContext as ScreenPulseApplication).repository.closeBackgroundMediaSession(id, now)
            }
        }
        openSessions.clear()
        registeredCallbacks.keys.forEach { it.unregisterCallback(registeredCallbacks[it]!!) }
        registeredCallbacks.clear()
        job.cancel()
    }

    private fun handleActiveSessions(controllers: List<MediaController>) {
        val currentPackages = controllers.map { it.packageName }.toSet()

        // Stop watching controllers that are no longer active.
        val stale = registeredCallbacks.keys.filter { it.packageName !in currentPackages }
        stale.forEach { controller ->
            registeredCallbacks[controller]?.let { controller.unregisterCallback(it) }
            registeredCallbacks.remove(controller)
        }

        controllers.forEach { controller ->
            if (registeredCallbacks.keys.none { it.packageName == controller.packageName }) {
                val callback = object : MediaController.Callback() {
                    override fun onPlaybackStateChanged(state: PlaybackState?) {
                        onStateChanged(controller.packageName, state)
                    }
                }
                controller.registerCallback(callback)
                registeredCallbacks[controller] = callback
                // Also handle the case where it's already playing at the moment we
                // start watching it (e.g. app was already playing before we connected).
                onStateChanged(controller.packageName, controller.playbackState)
            }
        }
    }

    private fun onStateChanged(packageName: String, state: PlaybackState?) {
        val isPlaying = state?.state == PlaybackState.STATE_PLAYING
        val now = System.currentTimeMillis()
        val app = applicationContext as ScreenPulseApplication

        if (isPlaying && !openSessions.containsKey(packageName)) {
            scope.launch {
                val id = app.repository.openBackgroundMediaSession(packageName, now)
                openSessions[packageName] = id
            }
        } else if (!isPlaying && openSessions.containsKey(packageName)) {
            val id = openSessions.remove(packageName)
            if (id != null) {
                scope.launch { app.repository.closeBackgroundMediaSession(id, now) }
            }
        }
    }

    // We don't need notification content at all, only the listener-connected binding —
    // deliberately left empty.
    override fun onNotificationPosted(sbn: StatusBarNotification?) {}
    override fun onNotificationRemoved(sbn: StatusBarNotification?) {}
}
