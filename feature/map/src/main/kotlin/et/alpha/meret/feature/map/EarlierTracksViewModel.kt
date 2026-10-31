package et.alpha.meret.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.flow.throttleLatest
import et.alpha.meret.core.data.tracking.EarlierTracksRepository
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The finished tracks around the area on screen, which the map draws under the live line.
 * Its own ViewModel: the map's camera is all it needs, and the recording is none of its
 * business (a stopped track simply becomes one of these).
 */
@HiltViewModel
class EarlierTracksViewModel
    @Inject
    constructor(
        private val repository: EarlierTracksRepository,
    ) : ViewModel() {
        /** The ground on screen when the camera last rested; null until the map reports. */
        private val visibleArea = MutableStateFlow<BoundingBox?>(null)

        /**
         * Loaded for an area larger than the screen ([EarlierTracksArea]), so small pans ask
         * Room nothing, and at most once per [AREA_THROTTLE_MS] while the camera keeps coming
         * to rest. Empty until the map has reported where it is.
         */
        @OptIn(ExperimentalCoroutinesApi::class)
        val lines: StateFlow<List<TrackLine>> =
            visibleArea
                .filterNotNull()
                .throttleLatest(AREA_THROTTLE_MS)
                .runningFold(null as BoundingBox?) { loaded, visible ->
                    EarlierTracksArea.next(loaded, visible)
                }.filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { area -> repository.observeEarlierTracks(area) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = emptyList(),
                )

        /** The camera came to rest over [area]. Null, while no map is drawn, is ignored. */
        fun onVisibleAreaChanged(area: BoundingBox?) {
            if (area != null) visibleArea.value = area
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /** A fling ends in several rests; one load a second is plenty. */
            const val AREA_THROTTLE_MS = 1_000L
        }
    }
