package et.alpha.meret.feature.tracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackRepository
import et.alpha.meret.core.data.tracking.TrackingController
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The recorded tracks, and deleting one with an undo window.
 *
 * A delete hides the row at once but writes nothing until [UNDO_WINDOW_MS] has passed
 * without an undo. So a process death inside the window, or leaving the app, keeps the
 * track: nothing was written. A second delete inside the window commits the first one now.
 * Even a committed delete only marks the track discarded; its points stay in Room.
 */
@HiltViewModel
class TrackListViewModel
    @Inject
    constructor(
        private val repository: TrackRepository,
        tracking: TrackingController,
    ) : ViewModel() {
        /** Rows hidden by a delete: the pending one, and committed ones until Room drops them. */
        private val hidden = MutableStateFlow<Set<String>>(emptySet())
        private val pending = MutableStateFlow<String?>(null)
        private val message = MutableStateFlow<TrackListMessage?>(null)
        private var pendingJob: Job? = null

        val uiState: StateFlow<TrackListUiState> =
            combine(
                repository.observeTracks(),
                tracking.state,
                hidden,
                pending,
                message,
            ) { tracks, state, hiddenIds, pendingId, shown ->
                TrackListUiState.Ready(
                    tracks = tracks.filterNot { it.id in hiddenIds }.map { it.toItem(state) },
                    pendingDelete = tracks.firstOrNull { it.id == pendingId }?.toItem(state),
                    message = shown,
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = TrackListUiState.Loading,
            )

        fun deleteTrack(trackId: String) {
            pending.value?.let { earlier ->
                pendingJob?.cancel()
                viewModelScope.launch { commit(earlier) }
            }
            message.value = null
            hidden.update { it + trackId }
            pending.value = trackId
            pendingJob =
                viewModelScope.launch {
                    delay(UNDO_WINDOW_MS)
                    commit(trackId)
                }
        }

        fun undoDelete() {
            val trackId = pending.value ?: return
            pendingJob?.cancel()
            pendingJob = null
            pending.value = null
            hidden.update { it - trackId }
        }

        fun dismissMessage() {
            message.value = null
        }

        private suspend fun commit(trackId: String) {
            if (pending.value == trackId) pending.value = null
            val result = repository.discardTrack(trackId)
            if (result is AppResult.Failure) {
                hidden.update { it - trackId }
                message.value = result.error.toMessage()
            }
        }

        internal companion object {
            /** Long enough to read the snackbar and reach Undo; matches a long snackbar. */
            const val UNDO_WINDOW_MS = 10_000L
            private const val STOP_TIMEOUT_MS = 5_000L
        }
    }

/** A track already gone needs no message: the list is right without it. */
private fun AppError.toMessage(): TrackListMessage? = when {
    this is AppError.Conflict -> TrackListMessage.DeleteRefusedRecording
    this is AppError.Storage && kind == AppError.Storage.Kind.NotFound -> null
    else -> TrackListMessage.DeleteFailed
}
