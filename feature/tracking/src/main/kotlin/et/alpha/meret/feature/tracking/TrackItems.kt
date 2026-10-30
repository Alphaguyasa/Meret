package et.alpha.meret.feature.tracking

import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackStatus

/** The row, or the detail header, for this track while the recorder is in [state]. */
internal fun Track.toItem(state: TrackingState): TrackItem = TrackItem(
    id = id,
    name = name,
    startedAtMillis = startedAt,
    distanceMetres = distanceMeters,
    movingDurationMillis = movingDurationMillis,
    progress = progress(state),
    syncState = syncState,
)

private fun Track.progress(state: TrackingState): TrackProgress {
    val recording = (state as? TrackingState.Recording)?.takeIf { it.trackId == id }
    return when {
        status != TrackStatus.ACTIVE && status != TrackStatus.PAUSED -> TrackProgress.Finished
        recording == null -> TrackProgress.NotFinished
        recording.isPaused -> TrackProgress.Paused
        else -> TrackProgress.Recording
    }
}
