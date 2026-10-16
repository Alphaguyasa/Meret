package et.alpha.meret.feature.tracking.service

import android.content.Intent

/** What an intent delivered to [TrackingService] asks for. */
internal sealed interface TrackingCommand {
    data class Start(
        val trackId: String,
    ) : TrackingCommand

    data object Pause : TrackingCommand

    data object Resume : TrackingCommand

    data object Stop : TrackingCommand

    /**
     * Pick up the persisted session. What a `START_STICKY` restart delivers: the system
     * recreates the service after process death with a null intent.
     */
    data object Restore : TrackingCommand

    companion object {
        const val ACTION_START = "et.alpha.meret.tracking.START"
        const val ACTION_PAUSE = "et.alpha.meret.tracking.PAUSE"
        const val ACTION_RESUME = "et.alpha.meret.tracking.RESUME"
        const val ACTION_STOP = "et.alpha.meret.tracking.STOP"
        const val EXTRA_TRACK_ID = "et.alpha.meret.tracking.TRACK_ID"

        /** Anything unrecognised restores: the safe reading is "carry on with what exists". */
        fun from(intent: Intent?): TrackingCommand {
            val trackId = intent?.getStringExtra(EXTRA_TRACK_ID)
            return when (intent?.action) {
                ACTION_START -> if (trackId.isNullOrBlank()) Restore else Start(trackId)
                ACTION_PAUSE -> Pause
                ACTION_RESUME -> Resume
                ACTION_STOP -> Stop
                else -> Restore
            }
        }
    }
}
