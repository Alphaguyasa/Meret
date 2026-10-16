package et.alpha.meret.feature.tracking.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.feature.tracking.R

/**
 * The recording notification (LOCATION_ENGINE.md §The foreground service).
 *
 * Field workers check it constantly, so it carries what they need without opening the app:
 * recording or paused, elapsed time, distance, point count, and Pause/Resume and Stop.
 * Low importance and silent: it updates on every batch write and must never buzz.
 */
internal class TrackingNotifications(
    private val context: Context,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.tracking_channel_description)
                setShowBadge(false)
            }
        manager.createNotificationChannel(channel)
    }

    /**
     * Replaces the shown notification. Without `POST_NOTIFICATIONS` (Android 13+) the system
     * drops it silently; the permission flow (roadmap item 8) asks for it before recording.
     */
    fun update(notification: Notification) {
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun build(
        state: TrackingState,
        track: Track?,
    ): Notification {
        val recording = state as? TrackingState.Recording
        val title =
            when {
                recording == null -> R.string.tracking_notification_starting
                recording.isPaused -> R.string.tracking_notification_paused
                else -> R.string.tracking_notification_recording
            }
        val builder =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tracking)
                .setContentTitle(context.getString(title))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(openApp())

        if (track != null) {
            builder.setContentText(
                context.getString(
                    R.string.tracking_notification_stats,
                    track.distanceMeters / METRES_PER_KILOMETRE,
                    track.pointCount,
                ),
            )
        }
        if (recording != null && !recording.isPaused && track != null) {
            // Elapsed since the track began, ticking without a notification update per second.
            builder.setUsesChronometer(true).setShowWhen(true).setWhen(track.startedAt)
        }
        if (recording != null) {
            if (recording.isPaused) {
                builder.addAction(0, context.getString(R.string.tracking_action_resume), resume())
            } else {
                builder.addAction(0, context.getString(R.string.tracking_action_pause), pause())
            }
            builder.addAction(0, context.getString(R.string.tracking_action_stop), stop())
        }
        return builder.build()
    }

    private fun pause() = serviceAction(TrackingCommand.ACTION_PAUSE, REQUEST_PAUSE)

    private fun resume() = serviceAction(TrackingCommand.ACTION_RESUME, REQUEST_RESUME)

    private fun stop() = serviceAction(TrackingCommand.ACTION_STOP, REQUEST_STOP)

    private fun serviceAction(
        action: String,
        requestCode: Int,
    ): PendingIntent = PendingIntent.getService(
        context,
        requestCode,
        Intent(context, TrackingService::class.java).setAction(action),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openApp(): PendingIntent? = context.packageManager
        .getLaunchIntentForPackage(context.packageName)
        ?.let { launch ->
            PendingIntent.getActivity(
                context,
                REQUEST_OPEN,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

    companion object {
        const val CHANNEL_ID = "tracking"
        const val NOTIFICATION_ID = 1001

        private const val METRES_PER_KILOMETRE = 1_000.0
        private const val REQUEST_OPEN = 0
        private const val REQUEST_PAUSE = 1
        private const val REQUEST_RESUME = 2
        private const val REQUEST_STOP = 3
    }
}
