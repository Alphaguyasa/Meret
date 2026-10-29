package et.alpha.meret.core.common.flow

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ThrottleLatestTest {
    @Test
    fun `the first value is at once and the newest follows each period`() = runTest {
        // A value every 600 ms, never on a period boundary.
        val upstream =
            flow {
                for (value in 0..9) {
                    emit(value)
                    delay(600)
                }
            }

        val received = upstream.throttleLatest(2_000).map { it to currentTime }.toList()

        assertThat(received)
            .containsExactly(0 to 0L, 3 to 2_000L, 6 to 4_000L, 9 to 6_000L)
            .inOrder()
    }

    @Test
    fun `a slow upstream passes through untouched`() = runTest {
        val upstream =
            flow {
                emit(1)
                delay(3_000)
                emit(2)
            }

        val received = upstream.throttleLatest(2_000).map { it to currentTime }.toList()

        assertThat(received).containsExactly(1 to 0L, 2 to 3_000L).inOrder()
    }
}
