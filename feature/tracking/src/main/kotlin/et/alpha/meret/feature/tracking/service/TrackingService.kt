package et.alpha.meret.feature.tracking.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * The foreground service that keeps a recording alive (LOCATION_ENGINE.md §The foreground
 * service, ARCHITECTURE.md ADR-009).
 *
 * A thin shell around [TrackingController], which owns the recording and its persistence:
 *
 * - `startForeground` on **every** start command, before anything else, including the sticky
 *   restart. Missing the five-second window crashes the app.
 * - `START_STICKY`, and a null or unknown intent restores the persisted session, so a process
 *   killed mid-track comes back to the same track id.
 * - A partial wakelock is held only while recording unpaused, and released on pause, stop and
 *   destroy. A foreground service alone does not keep the CPU awake.
 * - Swiping the app away does not stop it ([onTaskRemoved]).
 * - When there is nothing to record (stop, or a restore with no session) it leaves the
 *   foreground and stops itself.
 */
class TrackingService : Service() {
    private val scope = MainScope()
    private lateinit var controller: TrackingController
    private lateinit var notifications: TrackingNotifications
    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        controller = (application as TrackingServiceHost).trackingController
        notifications = TrackingNotifications(this).also { it.ensureChannel() }
        wakeLock =
            getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
                .apply { setReferenceCounted(false) }

        scope.launch {
            combine(controller.state, controller.activeTrack) { state, track -> state to track }
                .collect { (state, track) -> render(state, track) }
        }
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val refused = enterForeground(notifications.build(controller.state.value, null))
        if (refused != null) {
            // Started from the background, or location permission revoked on Android 14+.
            // Nothing is lost: the session stays persisted for the next user-started run.
            stopSelf()
            return START_NOT_STICKY
        }
        val command = TrackingCommand.from(intent)
        scope.launch { handle(command) }
        return START_STICKY
    }

    /** Keep recording when the user swipes the app away. That is the normal case. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        scope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private suspend fun handle(command: TrackingCommand) {
        when (command) {
            is TrackingCommand.Start -> controller.start(command.trackId)
            TrackingCommand.Pause -> controller.pause()
            TrackingCommand.Resume -> controller.resume()
            TrackingCommand.Stop -> controller.stop()
            is TrackingCommand.Reopen -> controller.reopen(command.trackId)
            TrackingCommand.Restore -> controller.restore()
        }
        if (controller.state.value == TrackingState.Idle) shutDown()
    }

    private fun render(
        state: TrackingState,
        track: Track?,
    ) {
        if (state is TrackingState.Recording && !state.isPaused) {
            acquireWakeLock()
        } else {
            releaseWakeLock()
        }
        if (state is TrackingState.Recording) {
            notifications.update(notifications.build(state, track))
        }
    }

    /** Returns why the system refused, or null once the service is in the foreground. */
    private fun enterForeground(notification: Notification): Exception? = try {
        ServiceCompat.startForeground(
            this,
            TrackingNotifications.NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION,
        )
        null
    } catch (denied: SecurityException) {
        denied
    } catch (notAllowed: IllegalStateException) {
        // ForegroundServiceStartNotAllowedException (Android 12+) is an IllegalStateException.
        notAllowed
    }

    private fun shutDown() {
        releaseWakeLock()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Re-acquired on every update while recording, which restarts the timeout. The timeout is
     * a backstop against a leaked lock draining the battery overnight, not a recording limit.
     */
    private fun acquireWakeLock() {
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
    }

    private fun releaseWakeLock() {
        if (wakeLock.isHeld) wakeLock.release()
    }

    companion object {
        private const val WAKE_LOCK_TAG = "meret:tracking"
        private const val WAKE_LOCK_TIMEOUT_MS = 12L * 60 * 60 * 1_000

        /**
         * Starts recording into [trackId], whose row must exist. Call only from a user action:
         * Android 12+ refuses a foreground service started from the background.
         */
        fun start(
            context: Context,
            trackId: String,
        ) = startInForeground(context, TrackingCommand.ACTION_START, trackId)

        /**
         * Records into [trackId] again, just after a stop made by mistake. A user action
         * only, like [start]: the service has already left the foreground.
         */
        fun reopen(
            context: Context,
            trackId: String,
        ) = startInForeground(context, TrackingCommand.ACTION_REOPEN, trackId)

        fun pause(context: Context) = send(context, TrackingCommand.ACTION_PAUSE)

        fun resume(context: Context) = send(context, TrackingCommand.ACTION_RESUME)

        fun stop(context: Context) = send(context, TrackingCommand.ACTION_STOP)

        private fun startInForeground(
            context: Context,
            action: String,
            trackId: String,
        ) {
            val intent =
                Intent(context, TrackingService::class.java)
                    .setAction(action)
                    .putExtra(TrackingCommand.EXTRA_TRACK_ID, trackId)
            ContextCompat.startForegroundService(context, intent)
        }

        private fun send(
            context: Context,
            action: String,
        ) {
            context.startService(Intent(context, TrackingService::class.java).setAction(action))
        }
    }
}
