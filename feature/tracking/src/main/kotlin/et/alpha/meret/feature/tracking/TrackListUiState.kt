package et.alpha.meret.feature.tracking

import et.alpha.meret.core.model.SyncState

/** The tracks screen. */
sealed interface TrackListUiState {
    /** The tracks have not been read yet. */
    data object Loading : TrackListUiState

    data class Ready(
        /** Newest first. A track waiting out its undo window is not here. */
        val tracks: List<TrackItem>,
        /** The track just deleted, while the undo window is open. */
        val pendingDelete: TrackItem?,
        /** Why the last delete did not happen, until the user dismisses it. */
        val message: TrackListMessage?,
    ) : TrackListUiState
}

/** One track, as its row shows it. A null [name] shows the start date instead. */
data class TrackItem(
    val id: String,
    val name: String?,
    val startedAtMillis: Long,
    val distanceMetres: Double,
    val movingDurationMillis: Long,
    val progress: TrackProgress,
    val syncState: SyncState,
) {
    /** Only a track that is not being recorded can be deleted. */
    val canDelete: Boolean
        get() = progress == TrackProgress.Finished || progress == TrackProgress.NotFinished
}

enum class TrackProgress {
    Recording,
    Paused,

    /**
     * Open in Room, but no recording holds it: a reboot, or a start the service never
     * recorded. Never shown as recording.
     */
    NotFinished,
    Finished,
}

enum class TrackListMessage {
    /** The track started recording again before the delete went through. */
    DeleteRefusedRecording,
    DeleteFailed,
}
