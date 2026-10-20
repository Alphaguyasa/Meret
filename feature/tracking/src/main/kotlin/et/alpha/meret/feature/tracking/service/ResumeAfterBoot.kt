package et.alpha.meret.feature.tracking.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.feature.tracking.R

/**
 * What [BootReceiver] does: if a track is still open, post one notification with a
 * "Resume recording" button; otherwise nothing.
 *
 * The prompt uses the recording notification's id, so when the worker taps Resume the
 * service's own notification replaces it in place. The button restores the persisted
 * session, which continues the same track in a new segment.
 */
internal class ResumeAfterBoot(
    private val context: Context,
    private val controller: TrackingController,
) {
    private val manager = context.getSystemService(NotificationManager::class.java)

    /** Returns true if the prompt was posted. */
    suspend fun run(): Boolean {
        controller.interruptedTrackId() ?: return false
        ensureChannel()
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tracking)
                .setContentTitle(context.getString(R.string.tracking_interrupted_title))
                .setContentText(context.getString(R.string.tracking_interrupted_body))
                .setStyle(
                    NotificationCompat
                        .BigTextStyle()
                        .bigText(context.getString(R.string.tracking_interrupted_body)),
                ).setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .addAction(0, context.getString(R.string.tracking_action_resume_track), resume())
                .build()
        manager.notify(TrackingNotifications.NOTIFICATION_ID, notification)
        return true
    }

    private fun ensureChannel() {
        val channel =
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.tracking_interrupted_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = context.getString(R.string.tracking_interrupted_channel_description)
            }
        manager.createNotificationChannel(channel)
    }

    /** A notification action may start a foreground service: it is a user interaction. */
    private fun resume(): PendingIntent = PendingIntent.getForegroundService(
        context,
        REQUEST_RESTORE,
        Intent(context, TrackingService::class.java).setAction(TrackingCommand.ACTION_RESTORE),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "tracking_interrupted"
        private const val REQUEST_RESTORE = 10
    }
}
