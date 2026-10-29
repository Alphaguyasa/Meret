package et.alpha.meret.domain.tracking

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import kotlinx.coroutines.test.runTest
import org.junit.Test

class StartTrackingUseCaseTest {
    private val tracks = FakeNewTrackStore()
    private val commands = FakeRecordingCommands()
    private val startTracking = StartTrackingUseCase(tracks, commands)

    @Test
    fun `a new row is written, then the service records into it`() = runTest {
        val result = startTracking()

        assertThat(result).isEqualTo(AppResult.Success("track-1"))
        assertThat(tracks.rows).containsExactly("track-1")
        assertThat(commands.started).containsExactly("track-1")
    }

    @Test
    fun `every start is a new track`() = runTest {
        startTracking()
        startTracking()

        assertThat(commands.started).containsExactly("track-1", "track-2").inOrder()
    }

    @Test
    fun `no row, no service`() = runTest {
        val failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
        tracks.failure = failure

        assertThat(startTracking()).isEqualTo(failure)
        assertThat(commands.started).isEmpty()
    }

    @Test
    fun `a service the system refuses leaves no open track behind`() = runTest {
        val refused = AppResult.Failure(AppError.Unexpected(IllegalStateException("background")))
        commands.failure = refused

        assertThat(startTracking()).isEqualTo(refused)
        assertThat(tracks.rows).isEmpty()
    }

    private class FakeNewTrackStore : NewTrackStore {
        val rows = mutableListOf<String>()
        var failure: AppResult.Failure? = null
        private var next = 0

        override suspend fun create(): AppResult<String> {
            failure?.let { return it }
            val id = "track-${++next}"
            rows += id
            return AppResult.Success(id)
        }

        override suspend fun discardIfEmpty(trackId: String) {
            rows -= trackId
        }
    }

    private class FakeRecordingCommands : RecordingCommands {
        val started = mutableListOf<String>()
        var failure: AppResult.Failure? = null

        override fun start(trackId: String): AppResult<Unit> {
            failure?.let { return it }
            started += trackId
            return AppResult.Success(Unit)
        }

        override fun pause() = Unit

        override fun resume() = Unit

        override fun stop() = Unit
    }
}
