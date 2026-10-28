package et.alpha.meret.core.location

import kotlinx.coroutines.flow.Flow

/**
 * Live fixes for the map's location puck while no track records.
 *
 * Separate from [LocationEngine] on purpose. The engine belongs to the recording: starting
 * or stopping it from the map would change what a track writes, and its request batches
 * fixes (`maxUpdateDelay` is 5× the interval), which would make the puck jump every 5–15 s.
 * A preview is its own platform request, unbatched, and never written to a track.
 *
 * [previewFixes] is cold. Collecting it registers the request and cancelling the collector
 * removes it, so the request lives exactly as long as the map is in the foreground and
 * listening. Only the newest fix matters for a puck, so a slow collector sees the latest
 * fix rather than a backlog.
 *
 * The flow fails with [SecurityException] when location permission is missing. A device
 * with no GPS at all yields an empty flow.
 */
public fun interface PreviewLocationSource {
    public fun previewFixes(): Flow<RawFix>

    public companion object {
        /** One fix a second: the screen is on anyway, and a puck that lags reads as lost. */
        public const val PREVIEW_INTERVAL_MILLIS: Long = 1_000L
    }
}
