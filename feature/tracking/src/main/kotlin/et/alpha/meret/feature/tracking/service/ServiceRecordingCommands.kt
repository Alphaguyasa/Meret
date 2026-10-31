package et.alpha.meret.feature.tracking.service

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.domain.tracking.RecordingCommands
import javax.inject.Inject

/** [RecordingCommands] as intents to [TrackingService]. */
class ServiceRecordingCommands
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : RecordingCommands {
        override fun start(trackId: String): AppResult<Unit> =
            startForeground { TrackingService.start(context, trackId) }

        override fun reopen(trackId: String): AppResult<Unit> =
            startForeground { TrackingService.reopen(context, trackId) }

        override fun pause() = send { TrackingService.pause(context) }

        override fun resume() = send { TrackingService.resume(context) }

        override fun stop() = send { TrackingService.stop(context) }

        private inline fun startForeground(command: () -> Unit): AppResult<Unit> = try {
            command()
            AppResult.Success(Unit)
        } catch (refused: IllegalStateException) {
            // ForegroundServiceStartNotAllowedException (Android 12+): not a user action.
            AppResult.Failure(AppError.Unexpected(refused))
        }

        /**
         * The service is running whenever there is something to pause or stop, so the system
         * allows these. A refusal would mean no recording is running: nothing to do.
         */
        private inline fun send(command: () -> Unit) {
            try {
                command()
            } catch (_: IllegalStateException) {
                // Nothing is recording.
            }
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class RecordingCommandsModule {
    @Binds
    abstract fun bindRecordingCommands(commands: ServiceRecordingCommands): RecordingCommands
}
