package et.alpha.meret.domain.tracking

import et.alpha.meret.core.common.result.AppResult

/**
 * Starts a new track: writes its row, then asks the service to record into it.
 *
 * The row comes first because the controller refuses a track it cannot find. If the service
 * cannot be started, the row, still empty, is deleted, so a refused start leaves no track
 * that looks open.
 */
public class StartTrackingUseCase(
    private val tracks: NewTrackStore,
    private val commands: RecordingCommands,
) {
    /** The new track's id, once the service has been asked to record into it. */
    public suspend operator fun invoke(): AppResult<String> {
        val created = tracks.create()
        if (created !is AppResult.Success) return created
        val trackId = created.value
        return when (val started = commands.start(trackId)) {
            is AppResult.Success -> {
                AppResult.Success(trackId)
            }

            is AppResult.Failure -> {
                tracks.discardIfEmpty(trackId)
                started
            }
        }
    }
}
