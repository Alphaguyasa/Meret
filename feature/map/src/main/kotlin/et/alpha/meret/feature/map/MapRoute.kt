package et.alpha.meret.feature.map

import android.widget.Toast
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import et.alpha.meret.core.designsystem.component.TrackingStats
import et.alpha.meret.core.designsystem.format.formatDistance
import et.alpha.meret.core.designsystem.format.formatDuration
import et.alpha.meret.core.maps.view.OfflineMap
import et.alpha.meret.core.maps.view.rememberOfflineMapController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt
import et.alpha.meret.core.designsystem.R as DesignR

/**
 * Owns the [MapViewModel] and puts the offline map under the screen's controls. The phone's
 * position goes to the map as the location puck; the recentre button turns follow mode on.
 * The record button starts, pauses and resumes a track, and a long-press stops it; the track
 * being recorded is drawn under the puck. For a few seconds after a long-press stop, a
 * snackbar offers to resume the same track, in case the stop was a mistake.
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
    val snackbarHost = remember { SnackbarHostState() }
    val stoppedText = stringResource(R.string.map_recording_stopped)
    val resumeText = stringResource(R.string.map_recording_resume)
    LaunchedEffect(viewModel, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            viewModel.events.collect { event ->
                when (event) {
                    MapEvent.RecordingNotStarted -> {
                        val message = R.string.map_recording_not_started
                        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                    }

                    is MapEvent.RecordingStopped -> {
                        launch {
                            // Cancelling showSnackbar takes it down when the window closes.
                            val result =
                                withTimeoutOrNull(UNDO_STOP_WINDOW_MS) {
                                    snackbarHost.showSnackbar(
                                        message = stoppedText,
                                        actionLabel = resumeText,
                                        duration = SnackbarDuration.Indefinite,
                                    )
                                }
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.onUndoStop(event.trackId)
                            }
                        }
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
        snackbar = { SnackbarHost(snackbarHost) },
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
        segmentCount = readout.segmentCount?.toString() ?: NO_VALUE,
        accuracy = readout.accuracyMetres?.roundToInt()?.toString() ?: NO_VALUE,
    )
}

private const val TICK_MS = 1_000L

/** How long a long-press stop can be undone. Long enough to read, short enough to forget. */
private const val UNDO_STOP_WINDOW_MS = 6_000L

/** A dash, not a word: the same in every language. */
private const val NO_VALUE = "–"
