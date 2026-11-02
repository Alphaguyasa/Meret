package et.alpha.meret.feature.waypoint

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.data.waypoint.WaypointRepository
import et.alpha.meret.core.model.Waypoint
import et.alpha.meret.domain.waypoint.WaypointCategory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The saved waypoints, newest first, straight from Room. A new one appears as soon as it is saved. */
@HiltViewModel
class WaypointListViewModel
    @Inject
    constructor(
        repository: WaypointRepository,
    ) : ViewModel() {
        val uiState: StateFlow<WaypointListUiState> =
            repository
                .observeWaypoints()
                .map { waypoints -> WaypointListUiState.Ready(waypoints.map { it.toItem() }) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = WaypointListUiState.Loading,
                )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }

internal fun Waypoint.toItem(): WaypointItem {
    val known = WaypointCategory.fromKey(category)
    return WaypointItem(
        id = id,
        title = title?.takeIf { it.isNotBlank() },
        category = known,
        otherCategoryKey = if (known == null) category?.takeIf { it.isNotBlank() } else null,
        recordedAtMillis = recordedAt,
        position = position,
        syncState = syncState,
    )
}
