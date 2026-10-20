package et.alpha.meret.feature.tracking.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * After a reboot, offers to resume a track that was still open (BUILD_PLAN phase 5h).
 *
 * It never starts [TrackingService] itself. Android 12+ refuses a foreground service started
 * from the background, and a phone that begins tracking on its own after a restart would
 * surprise the worker. The notification's button is the user action that starts it.
 *
 * `BOOT_COMPLETED`, not `LOCKED_BOOT_COMPLETED`: the session lives in credential-protected
 * storage, which cannot be read before the first unlock.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val host = context.applicationContext as? TrackingServiceHost ?: return
        val pending = goAsync()
        // DataStore and Room are read off the main thread; goAsync allows about ten seconds.
        CoroutineScope(Dispatchers.IO).launch {
            try {
                ResumeAfterBoot(context.applicationContext, host.trackingController).run()
            } finally {
                pending.finish()
            }
        }
    }
}
