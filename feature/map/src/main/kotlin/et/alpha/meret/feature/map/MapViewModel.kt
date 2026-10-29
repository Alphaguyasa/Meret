package et.alpha.meret.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.designsystem.component.RecordingState
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.tracking.RecordingCommands
import et.alpha.meret.domain.tracking.StartTrackingUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The map screen's state: which pack to draw, where the phone is for the location puck, and
 * the recording the record button drives.
 *
 * [mapSession] is handed to the map view; the ViewModel never starts or stops it.
 *
 * The record button reads the [TrackingController]'s state but never calls it: commands go
 * through [RecordingCommands] to the service, the controller's only caller (ADR-009).
 */
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        tilePacks: TilePackRepository,
        devicePosition: DevicePositionRepository,
        val mapSession: OfflineMapSession,
        private val tracking: TrackingController,
        private val startTracking: StartTrackingUseCase,
        private val commands: RecordingCommands,
    ) : ViewModel() {
        /** The phone's position for the puck. Its own flow: it changes every fix. */
        val position: StateFlow<DevicePosition?> =
            devicePosition
                .observePosition()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = null,
                )

        /** The recording in progress, as raw numbers; the route formats and ticks them. */
        val recording: StateFlow<RecordingReadout?> =
            combine(tracking.state, tracking.activeTrack, tracking.health) { state, track, health ->
                recordingReadout(state, track, health)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null,
            )

        /** The recording's line for the map, refreshed every 2 s; null while idle. */
        val liveLine: StateFlow<TrackLine?> =
            tracking.activeLine.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null,
            )

        val uiState: StateFlow<MapUiState> =
            combine(tilePacks.observeTilePacks(), position, recording) { packs, here, recording ->
                MapUiState(
                    basemapPackId = chooseBasemap(packs, here)?.id,
                    recordingState = recording?.state ?: RecordingState.Idle,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MapUiState(),
            )

        private val mutableEvents = Channel<MapEvent>(Channel.BUFFERED)

        /** One-shot messages for the route. */
        val events: Flow<MapEvent> = mutableEvents.receiveAsFlow()

        /** Running from a tap that starts a track until the service records it. */
        private var startJob: Job? = null

        /** Tap: start a track, or pause and resume the one recording. */
        fun onRecordTap() {
            when (val state = tracking.state.value) {
                TrackingState.Idle -> {
                    start()
                }

                is TrackingState.Recording -> {
                    if (state.isPaused) commands.resume() else commands.pause()
                }
            }
        }

        /** Long-press: stop. Nothing to stop while idle. */
        fun onRecordLongPress() {
            if (tracking.state.value is TrackingState.Recording) commands.stop()
        }

        /**
         * A second tap before the service has started the first track is ignored: it would
         * open a second track. If the service has not started within [START_TIMEOUT_MS] (it
         * refused, or location was revoked), the worker is told and may tap again.
         */
        private fun start() {
            if (startJob?.isActive == true) return
            startJob =
                viewModelScope.launch {
                    val started =
                        when (startTracking()) {
                            is AppResult.Failure -> {
                                false
                            }

                            is AppResult.Success -> {
                                withTimeoutOrNull(START_TIMEOUT_MS) {
                                    tracking.state.first { it is TrackingState.Recording }
                                } != null
                            }
                        }
                    if (!started) mutableEvents.send(MapEvent.RecordingNotStarted)
                }
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val START_TIMEOUT_MS = 10_000L
        }
    }

/** The recording's numbers for the tracking bar. [startedAtMillis] is null until Room has it. */
data class RecordingReadout(
    val state: RecordingState,
    val startedAtMillis: Long?,
    val distanceMetres: Double,
    val pointCount: Int,
    val accuracyMetres: Double?,
)

/** Things the map screen says once. */
sealed interface MapEvent {
    /** A tap on the record button did not start a recording. */
    data object RecordingNotStarted : MapEvent
}

/**
 * What the tracking bar shows, or null when nothing records. Room may still hold another open
 * row (a crash before a stop), so only the controller's own track counts.
 */
internal fun recordingReadout(
    state: TrackingState,
    track: Track?,
    health: TrackingHealth,
): RecordingReadout? {
    val recording = state as? TrackingState.Recording ?: return null
    val ours = track?.takeIf { it.id == recording.trackId }
    return RecordingReadout(
        state = if (recording.isPaused) RecordingState.Paused else RecordingState.Recording,
        startedAtMillis = ours?.startedAt,
        distanceMetres = ours?.distanceMeters ?: 0.0,
        pointCount = ours?.pointCount ?: 0,
        accuracyMetres = health.lastAccuracyMetres,
    )
}

/**
 * The newest pack that covers [position], else the newest pack. [packs] is newest first, as
 * the repository gives it. Without a position (no fix yet, no permission) the newest pack
 * is drawn, as before the puck.
 */
internal fun chooseBasemap(
    packs: List<TilePack>,
    position: DevicePosition?,
): TilePack? {
    val here = position?.position ?: return packs.firstOrNull()
    return packs.firstOrNull { here in it.bounds } ?: packs.firstOrNull()
}
