package et.alpha.meret.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.maps.OfflineMapSession
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The map screen's state. For now that is which pack to draw: the newest installed one.
 * Choosing the pack that covers the user's position comes with the location puck (6f).
 *
 * [mapSession] is handed to the map view; the ViewModel never starts or stops it.
 */
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        tilePacks: TilePackRepository,
        val mapSession: OfflineMapSession,
    ) : ViewModel() {
        val uiState: StateFlow<MapUiState> =
            tilePacks
                .observeTilePacks()
                .map { packs -> MapUiState(basemapPackId = packs.firstOrNull()?.id) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = MapUiState(),
                )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
