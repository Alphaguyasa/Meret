package et.alpha.meret.feature.waypoint

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.domain.waypoint.SaveWaypointUseCase
import et.alpha.meret.domain.waypoint.WaypointCategory
import et.alpha.meret.domain.waypoint.WaypointInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The waypoint sheet over the map: where the phone is, what the worker typed, and Save.
 *
 * Save writes the waypoint to Room at the live fix of that moment, attached to the track
 * being recorded if there is one, and nothing else: no network. Without a live fix it is
 * refused (see [SaveWaypointUseCase]).
 *
 * The text and category are kept in [SavedStateHandle], so a rotation or a process death
 * while the app is in the background keeps them. A dismissed sheet keeps them too, for a
 * swipe made by mistake; a saved one starts empty. Draft autosave to Room is item 32.
 */
@HiltViewModel
class WaypointCaptureViewModel
    @Inject
    constructor(
        devicePosition: DevicePositionRepository,
        private val tracking: TrackingController,
        private val saveWaypoint: SaveWaypointUseCase,
        private val savedState: SavedStateHandle,
    ) : ViewModel() {
        private val fix: StateFlow<DevicePosition?> =
            devicePosition
                .observePosition()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = null,
                )

        private val input =
            MutableStateFlow(
                WaypointInput(
                    title = savedState.get<String>(KEY_TITLE).orEmpty(),
                    category = WaypointCategory.fromKey(savedState.get<String>(KEY_CATEGORY)),
                    notes = savedState.get<String>(KEY_NOTES).orEmpty(),
                ),
            )

        val uiState: StateFlow<WaypointCaptureUiState> =
            combine(fix, tracking.state, input) { here, recording, typed ->
                WaypointCaptureUiState(
                    fix = here.toCaptureFix(),
                    onTrack = recording is TrackingState.Recording,
                    title = typed.title,
                    category = typed.category,
                    notes = typed.notes,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = WaypointCaptureUiState(),
            )

        private val mutableEvents = Channel<WaypointCaptureEvent>(Channel.BUFFERED)

        /** One-shot outcomes of Save, for the route. */
        val events: Flow<WaypointCaptureEvent> = mutableEvents.receiveAsFlow()

        private var saveJob: Job? = null

        fun onTitleChange(title: String) {
            savedState[KEY_TITLE] = title
            input.update { it.copy(title = title) }
        }

        fun onNotesChange(notes: String) {
            savedState[KEY_NOTES] = notes
            input.update { it.copy(notes = notes) }
        }

        /** Picks [category], or clears it when it is the one already picked. */
        fun onCategoryTap(category: WaypointCategory) {
            val picked = category.takeUnless { it == input.value.category }
            savedState[KEY_CATEGORY] = picked?.key
            input.update { it.copy(category = picked) }
        }

        /** A second tap while a save is being written is ignored: it would save twice. */
        fun onSave() {
            if (saveJob?.isActive == true) return
            saveJob =
                viewModelScope.launch {
                    val trackId = (tracking.state.value as? TrackingState.Recording)?.trackId
                    val event =
                        when (val result = saveWaypoint(fix.value, trackId, input.value)) {
                            is AppResult.Success -> {
                                clearInput()
                                WaypointCaptureEvent.Saved
                            }

                            is AppResult.Failure -> {
                                WaypointCaptureEvent.NotSaved(
                                    noFix = result.error is AppError.Gps,
                                )
                            }
                        }
                    mutableEvents.send(event)
                }
        }

        private fun clearInput() {
            savedState[KEY_TITLE] = ""
            savedState[KEY_NOTES] = ""
            savedState.remove<String>(KEY_CATEGORY)
            input.value = WaypointInput()
        }

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L
            const val KEY_TITLE = "waypoint_title"
            const val KEY_NOTES = "waypoint_notes"
            const val KEY_CATEGORY = "waypoint_category"
        }
    }

/** Everything the waypoint sheet shows. Saving is always possible once [fix] is located. */
data class WaypointCaptureUiState(
    val fix: CaptureFix = CaptureFix.Waiting,
    /** A track is being recorded, and the waypoint will belong to it. */
    val onTrack: Boolean = false,
    val title: String = "",
    val category: WaypointCategory? = null,
    val notes: String = "",
) {
    val canSave: Boolean get() = fix is CaptureFix.Located
}

/** Where the waypoint would be saved. */
sealed interface CaptureFix {
    /** No live fix yet: none at all, or only the platform's cached one. */
    data object Waiting : CaptureFix

    data class Located(
        val latitude: Double,
        val longitude: Double,
        val accuracyMetres: Double,
    ) : CaptureFix
}

/** What Save did. */
sealed interface WaypointCaptureEvent {
    data object Saved : WaypointCaptureEvent

    /** Nothing was written: there was no live fix ([noFix]), or Room refused the write. */
    data class NotSaved(
        val noFix: Boolean,
    ) : WaypointCaptureEvent
}

internal fun DevicePosition?.toCaptureFix(): CaptureFix = if (this == null || !isLive) {
    CaptureFix.Waiting
} else {
    CaptureFix.Located(position.latitude, position.longitude, accuracyMetres)
}
