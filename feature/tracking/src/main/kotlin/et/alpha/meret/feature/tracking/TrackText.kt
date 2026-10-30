package et.alpha.meret.feature.tracking

import android.text.format.DateUtils
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import et.alpha.meret.core.designsystem.format.formatDistance
import et.alpha.meret.core.designsystem.format.formatDuration
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.designsystem.R as DesignR

// How a track reads on the list and on its detail screen.

/** The track's own name, or when it started, in the phone's language and date order. */
@Composable
internal fun TrackItem.title(): String {
    if (!name.isNullOrBlank()) return name
    val context = LocalContext.current
    return DateUtils.formatDateTime(context, startedAtMillis, DATE_FLAGS)
}

@Composable
internal fun TrackItem.stats(): String {
    val (value, unit) = distanceText(distanceMetres)
    val duration = formatDuration(movingDurationMillis)
    return stringResource(R.string.tracks_row_stats, value, unit, duration)
}

/** A distance's number and its translated unit. */
@Composable
internal fun distanceText(metres: Double): Pair<String, String> {
    val distance = formatDistance(metres)
    val unit =
        if (distance.inKilometres) {
            DesignR.string.unit_kilometres
        } else {
            DesignR.string.unit_metres
        }
    return distance.value to stringResource(unit)
}

/** Recording state first; a finished track shows where it stands with the server. */
@Composable
internal fun TrackItem.standing(): Pair<String, Color> {
    val colors = MaterialTheme.colorScheme
    return when (progress) {
        TrackProgress.Recording -> stringResource(R.string.tracks_recording) to colors.primary
        TrackProgress.Paused -> stringResource(R.string.tracks_paused) to colors.primary
        TrackProgress.NotFinished -> stringResource(R.string.tracks_not_finished) to colors.error
        TrackProgress.Finished -> syncLabel(syncState) to colors.onSurfaceVariant
    }
}

@Composable
private fun syncLabel(syncState: SyncState): String = stringResource(
    when (syncState) {
        SyncState.PENDING -> R.string.tracks_sync_pending
        SyncState.SYNCING -> R.string.tracks_sync_syncing
        SyncState.SYNCED -> R.string.tracks_sync_synced
        SyncState.CONFLICT -> R.string.tracks_sync_conflict
    },
)

private const val DATE_FLAGS =
    DateUtils.FORMAT_SHOW_DATE or
        DateUtils.FORMAT_SHOW_YEAR or
        DateUtils.FORMAT_ABBREV_MONTH or
        DateUtils.FORMAT_SHOW_TIME
