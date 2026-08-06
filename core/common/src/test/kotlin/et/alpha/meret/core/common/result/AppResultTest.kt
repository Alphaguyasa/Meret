package et.alpha.meret.core.common.result

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

class AppResultTest {
    private val storageFailure = AppError.Storage(AppError.Storage.Kind.WriteFailed)

    @Test
    fun `success carries its value and reports no error`() {
        val result: AppResult<Int> = AppResult.Success(7)

        assertThat(result.isSuccess).isTrue()
        assertThat(result.isFailure).isFalse()
        assertThat(result.getOrNull()).isEqualTo(7)
        assertThat(result.errorOrNull()).isNull()
    }

    @Test
    fun `failure carries its error and no value`() {
        val result: AppResult<Int> = AppResult.Failure(storageFailure)

        assertThat(result.isSuccess).isFalse()
        assertThat(result.isFailure).isTrue()
        assertThat(result.getOrNull()).isNull()
        assertThat(result.errorOrNull()).isEqualTo(storageFailure)
    }

    @Test
    fun `map transforms a success and leaves a failure alone`() {
        assertThat(AppResult.Success(3).map { it * 2 }.getOrNull()).isEqualTo(6)

        val failure: AppResult<Int> = AppResult.Failure(storageFailure)
        assertThat(failure.map { error("must not run") }.errorOrNull()).isEqualTo(storageFailure)
    }

    @Test
    fun `mapError rewrites a failure and leaves a success alone`() {
        val replacement = AppError.Conflict(entityType = "waypoint", entityId = "abc")

        val remapped: AppResult<Int> = AppResult.Failure(storageFailure).mapError { replacement }
        assertThat(remapped.errorOrNull()).isEqualTo(replacement)

        val untouched: AppResult<Int> = AppResult.Success(1).mapError { replacement }
        assertThat(untouched.getOrNull()).isEqualTo(1)
    }

    @Test
    fun `flatMap chains and short-circuits`() {
        val chained = AppResult.Success(4).flatMap { AppResult.Success(it + 1) }
        assertThat(chained.getOrNull()).isEqualTo(5)

        val shortCircuited: AppResult<Int> =
            AppResult
                .Failure(storageFailure)
                .flatMap<Int, Int> { error("must not run") }
        assertThat(shortCircuited.errorOrNull()).isEqualTo(storageFailure)
    }

    @Test
    fun `fold picks the matching branch`() {
        assertThat(AppResult.Success(2).fold({ "ok $it" }, { "bad" })).isEqualTo("ok 2")
        assertThat(AppResult.Failure(storageFailure).fold({ "ok" }, { "bad" })).isEqualTo("bad")
    }

    @Test
    fun `getOrElse supplies a fallback only on failure`() {
        val succeeding: AppResult<Int> = AppResult.Success(11)
        val failing: AppResult<Int> = AppResult.Failure(storageFailure)

        assertThat(succeeding.getOrElse { 0 }).isEqualTo(11)
        assertThat(failing.getOrElse { 0 }).isEqualTo(0)
    }

    @Test
    fun `onSuccess and onFailure run the matching side effect and return the receiver`() {
        var seenValue: Int? = null
        var seenError: AppError? = null

        val success: AppResult<Int> = AppResult.Success(9)
        val returned =
            success
                .onSuccess { seenValue = it }
                .onFailure { seenError = it }

        assertThat(returned).isSameInstanceAs(success)
        assertThat(seenValue).isEqualTo(9)
        assertThat(seenError).isNull()

        AppResult.Failure(storageFailure).onFailure { seenError = it }
        assertThat(seenError).isEqualTo(storageFailure)
    }

    @Test
    fun `appCatching wraps a returned value`() {
        assertThat(appCatching { 42 }.getOrNull()).isEqualTo(42)
    }

    @Test
    fun `appCatching converts a throw into Unexpected by default`() {
        val boom = IOException("disk")

        val result = appCatching<Int> { throw boom }

        assertThat(result.errorOrNull()).isEqualTo(AppError.Unexpected(boom))
    }

    @Test
    fun `appCatching honours a caller-supplied error mapping`() {
        val result =
            appCatching<Int>(
                onThrow = { AppError.Storage(AppError.Storage.Kind.Corrupt, it) },
            ) {
                throw IllegalStateException("bad row")
            }

        val error = result.errorOrNull()
        assertThat(error).isInstanceOf(AppError.Storage::class.java)
        assertThat((error as AppError.Storage).kind).isEqualTo(AppError.Storage.Kind.Corrupt)
    }

    @Test
    fun `appCatching rethrows cancellation instead of swallowing it`() {
        assertThrows(CancellationException::class.java) {
            appCatching<Int> { throw CancellationException("cancelled") }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class) // runCurrent, to start the child deterministically
    @Test
    fun `cancelling a coroutine that uses appCatching actually cancels it`() = runTest {
        // The regression this guards: a swallowed CancellationException leaves the
        // coroutine believing it merely failed, and structured concurrency stops working.
        // Tracking cancels its filter coroutine on every pause, so this is a live path.
        val deferred = async { appCatching { awaitCancellationForTest() } }
        runCurrent()

        deferred.cancel()
        deferred.join()

        assertThat(deferred.isCancelled).isTrue()
    }

    private suspend fun awaitCancellationForTest(): Int = awaitCancellation()
}
