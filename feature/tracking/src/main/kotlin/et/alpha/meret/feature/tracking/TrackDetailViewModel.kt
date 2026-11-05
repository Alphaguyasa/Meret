package et.alpha.meret.feature.tracking

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.model.ElevationSample
import et.alpha.meret.core.model.SegmentSummary
import et.alpha.meret.core.model.geo.BoundingBox
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** The track detail screen. */
sealed interface TrackDetailUiState {
    data object Loading : TrackDetailUiState

    /** Discarded, or never on this phone: a stale link. */
    data object NotFound : TrackDetailUiState

    data class Ready(
        val track: TrackItem,
        val pointCount: Int,
        val segments: List<SegmentSummary>,
        /** Fewer than two samples draws no profile. */
        val elevation: List<ElevationSample>,
        /** The ground the track covers; null before its first accepted fix (nothing to show). */
        val bounds: BoundingBox? = null,
    ) : TrackDetailUiState
}

/** One-shot messages from the track detail screen's view model. */
sealed interface TrackDetailEvent {
    /** A rename did not reach Room; the old name still shows. */
    data object RenameFailed : TrackDetailEvent
}

/**
 * One track: its totals, its segments and its elevation profile, kept current while it is
 * still recording. The track id comes from the navigation route, [TRACK_ID_ARG]. A rename is
 * written to Room, and the screen shows it when Room does.
 */
@HiltViewModel
class TrackDetailViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: TrackRepository,
        tracking: TrackingController,
    ) : ViewModel() {
        private val trackId: String = checkNotNull(savedStateHandle[TRACK_ID_ARG])

        val uiState: StateFlow<TrackDetailUiState> =
            combine(repository.observeTrackDetail(trackId), tracking.state) { detail, state ->
                if (detail == null) {
                    TrackDetailUiState.NotFound
                } else {
                    TrackDetailUiState.Ready(
                        track = detail.track.toItem(state),
                        pointCount = detail.track.pointCount,
                        segments = detail.summary.segments,
                        elevation = detail.summary.elevation,
                        bounds = detail.track.bounds,
                    )
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TrackDetailUiState.Loading,
            )

        private val mutableEvents = Channel<TrackDetailEvent>(Channel.BUFFERED)

        /** Collected while the screen is started. */
        val events: Flow<TrackDetailEvent> = mutableEvents.receiveAsFlow()

        /** The user's name for the track; blank removes it. */
        fun onRename(name: String) {
            viewModelScope.launch {
                if (repository.renameTrack(trackId, name) is AppResult.Failure) {
                    mutableEvents.send(TrackDetailEvent.RenameFailed)
                }
            }
        }

        companion object {
            /** The navigation argument holding the track id. */
            const val TRACK_ID_ARG: String = "trackId"
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }
