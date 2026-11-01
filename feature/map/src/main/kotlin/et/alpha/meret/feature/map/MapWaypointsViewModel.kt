package et.alpha.meret.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.data.waypoint.WaypointRepository
import et.alpha.meret.core.model.geo.WaypointMarker
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The saved waypoints the map draws. Its own ViewModel, like [EarlierTracksViewModel]: it needs
 * nothing from the recording or the camera. A waypoint saved from the sheet appears as soon as
 * Room has it.
 */
@HiltViewModel
class MapWaypointsViewModel
    @Inject
    constructor(
        repository: WaypointRepository,
    ) : ViewModel() {
        val markers: StateFlow<List<WaypointMarker>> =
            repository
                .observeMarkers()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = emptyList(),
                )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
