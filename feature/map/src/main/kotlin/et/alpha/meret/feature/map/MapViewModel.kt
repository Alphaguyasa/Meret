package et.alpha.meret.feature.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.geo.DevicePosition
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * The map screen's state: which pack to draw, and where the phone is for the location puck.
 *
 * [mapSession] is handed to the map view; the ViewModel never starts or stops it.
 */
@HiltViewModel
class MapViewModel
    @Inject
    constructor(
        tilePacks: TilePackRepository,
        devicePosition: DevicePositionRepository,
        val mapSession: OfflineMapSession,
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

        val uiState: StateFlow<MapUiState> =
            combine(tilePacks.observeTilePacks(), position) { packs, here ->
                MapUiState(basemapPackId = chooseBasemap(packs, here)?.id)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = MapUiState(),
            )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
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
