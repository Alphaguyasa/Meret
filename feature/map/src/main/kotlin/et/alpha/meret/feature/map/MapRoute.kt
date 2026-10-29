package et.alpha.meret.feature.map

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import et.alpha.meret.core.designsystem.component.TrackingStats
import et.alpha.meret.core.maps.view.OfflineMap
import et.alpha.meret.core.maps.view.rememberOfflineMapController
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.roundToInt
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * Owns the [MapViewModel] and puts the offline map under the screen's controls. The phone's
 * position goes to the map as the location puck; the recentre button turns follow mode on.
 * The record button starts, pauses and resumes a track, and a long-press stops it; the track
 * being recorded is drawn under the puck.
 */
@Composable
fun MapRoute(
    onOpenDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val position by viewModel.position.collectAsStateWithLifecycle()
    val recording by viewModel.recording.collectAsStateWithLifecycle()
    val liveLine by viewModel.liveLine.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    MapEvent.RecordingNotStarted -> {
                        val message = R.string.map_recording_not_started
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
    val mapController = rememberOfflineMapController()
    LaunchedEffect(mapController, position) {
        mapController.showPosition(position)
    }
    LaunchedEffect(mapController, liveLine) {
        mapController.showTrackLine(liveLine)
    }

    val nowMillis = rememberNowMillis(ticking = recording != null)
    val stats = recording?.let { trackingStats(it, nowMillis) }

    MapScreen(
        state = state.copy(trackingStats = stats),
        onRecordTap = viewModel::onRecordTap,
        onRecordLongPress = viewModel::onRecordLongPress,
        onAddWaypoint = {},
        onRecentreMap = mapController::recentre,
        onOpenSync = {},
        onOpenDiagnostics = onOpenDiagnostics,
        modifier = modifier,
        contentPadding = contentPadding,
        basemap = { packId, mapModifier ->
            OfflineMap(
                packId = packId,
                session = viewModel.mapSession,
                modifier = mapModifier,
                controller = mapController,
            )
        },
        camera = mapController.readout,
        onZoomIn = mapController::zoomIn,
        onZoomOut = mapController::zoomOut,
        onResetNorth = mapController::resetNorth,
        isFollowing = mapController.isFollowing,
    )
}

/** The wall clock, once a second while [ticking]: the tracking bar's duration. */
@Composable
private fun rememberNowMillis(ticking: Boolean): Long {
    val now by produceState(System.currentTimeMillis(), ticking) {
        value = System.currentTimeMillis()
        while (ticking) {
            delay(TICK_MS)
            value = System.currentTimeMillis()
        }
    }
    return now
}

@Composable
private fun trackingStats(
    readout: RecordingReadout,
    nowMillis: Long,
): TrackingStats {
    val distance = formatDistance(readout.distanceMetres)
    return TrackingStats(
        state = readout.state,
        duration = formatDuration(readout.startedAtMillis?.let { nowMillis - it } ?: 0L),
        distance = distance.value,
        distanceUnit =
            stringResource(
                if (distance.inKilometres) {
                    DesignR.string.unit_kilometres
                } else {
                    DesignR.string.unit_metres
                },
            ),
        pointCount = readout.pointCount.toString(),
        // Segments are not counted yet (item 23 brings the segment list).
        segmentCount = NO_VALUE,
        accuracy = readout.accuracyMetres?.roundToInt()?.toString() ?: NO_VALUE,
    )
}

/** `H:MM:SS`, from zero up. A clock set back mid-track shows 0:00:00, not a negative time. */
internal fun formatDuration(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
}

internal data class FormattedDistance(
    val value: String,
    val inKilometres: Boolean,
)

/** Whole metres below a kilometre, then kilometres to two places. */
internal fun formatDistance(metres: Double): FormattedDistance =
    if (metres < METRES_PER_KILOMETRE) {
        FormattedDistance(metres.coerceAtLeast(0.0).toInt().toString(), inKilometres = false)
    } else {
        FormattedDistance(
            String.format(Locale.ROOT, "%.2f", metres / METRES_PER_KILOMETRE),
            inKilometres = true,
        )
    }

private const val TICK_MS = 1_000L
private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val METRES_PER_KILOMETRE = 1_000.0

/** A dash, not a word: the same in every language. */
private const val NO_VALUE = "–"
