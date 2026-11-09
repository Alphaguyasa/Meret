package et.alpha.meret.feature.measure

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.measure.MeasureRingDraft
import et.alpha.meret.domain.measure.SaveMeasurementUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the measuring panel shows. [areaSquareMetres] is 0 until there are three corners, and
 * [perimeterMetres] with two corners is the one side there is, not there and back.
 */
data class MeasureUiState(
    val vertices: List<LatLng> = emptyList(),
    val areaSquareMetres: Double = 0.0,
    val perimeterMetres: Double = 0.0,
    val canSave: Boolean = false,
) {
    val canUndo: Boolean get() = vertices.isNotEmpty()
}

sealed interface MeasureEvent {
    /** The plot is in Room. The ring has been forgotten; the panel closes. */
    data object Saved : MeasureEvent

    /** The ring cannot be saved as it stands: too few corners, or corners on one line. */
    data object NotAPlot : MeasureEvent

    /** The phone could not write. Nothing was lost: the ring is as it was. */
    data object StorageFailed : MeasureEvent
}

/**
 * The plot being measured by tapping its corners. The ring lives in [MeasureRingDraft], not
 * here, so killing the process mid-plot loses nothing; this class only reads it back.
 */
@HiltViewModel
class MeasureViewModel
    @Inject
    constructor(
        private val draft: MeasureRingDraft,
        private val saveMeasurement: SaveMeasurementUseCase,
        dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val eventChannel = Channel<MeasureEvent>(Channel.BUFFERED)
        private var saving = false

        val events: Flow<MeasureEvent> = eventChannel.receiveAsFlow()

        val uiState: StateFlow<MeasureUiState> =
            draft.vertices
                .map(::summarise)
                .flowOn(dispatchers.default)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = MeasureUiState(),
                )

        /**
         * Adds a corner at [position], the point under the crosshair; null while there is no
         * map to read it from. Tapping again without moving the map adds nothing.
         */
        fun onAddCorner(position: LatLng?) {
            if (position == null || !position.isValid) return
            change { ring -> if (ring.lastOrNull() == position) ring else ring + position }
        }

        fun onUndo() {
            change { it.dropLast(1) }
        }

        fun onClear() {
            change { emptyList() }
        }

        /** Saves the ring under [name] and, once Room has it, forgets the ring. */
        fun onSave(name: String) {
            if (saving) return
            saving = true
            viewModelScope.launch {
                val result =
                    saveMeasurement(draft.vertices.first(), MeasurementMethod.TAPPED, name)
                when (result) {
                    is AppResult.Success -> {
                        draft.update { emptyList() }
                        eventChannel.send(MeasureEvent.Saved)
                    }

                    is AppResult.Failure -> {
                        eventChannel.send(eventFor(result.error))
                    }
                }
                saving = false
            }
        }

        private fun change(transform: (List<LatLng>) -> List<LatLng>) {
            viewModelScope.launch {
                if (draft.update(transform) is AppResult.Failure) {
                    eventChannel.send(MeasureEvent.StorageFailed)
                }
            }
        }

        private fun eventFor(error: AppError): MeasureEvent =
            if (error is AppError.Invalid) MeasureEvent.NotAPlot else MeasureEvent.StorageFailed

        private fun summarise(ring: List<LatLng>): MeasureUiState {
            val closes = ring.size >= SaveMeasurementUseCase.MIN_DISTINCT_VERTICES
            val area = if (closes) GeoMath.ellipsoidalPolygonArea(ring) else 0.0
            return MeasureUiState(
                vertices = ring,
                areaSquareMetres = area,
                perimeterMetres =
                    if (closes) GeoMath.polygonPerimeter(ring) else GeoMath.pathLength(ring),
                canSave =
                    ring.toSet().size >= SaveMeasurementUseCase.MIN_DISTINCT_VERTICES &&
                        area >= SaveMeasurementUseCase.MIN_AREA_SQUARE_METRES,
            )
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
