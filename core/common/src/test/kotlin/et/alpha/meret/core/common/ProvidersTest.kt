package et.alpha.meret.core.common

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.StandardDispatcherProvider
import et.alpha.meret.core.common.telemetry.NoOpTelemetry
import et.alpha.meret.core.common.time.SystemTimeProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test

class ProvidersTest {
    @Test
    fun `the standard dispatcher provider hands back the real dispatchers`() {
        val provider = StandardDispatcherProvider()

        assertThat(provider.default).isSameInstanceAs(Dispatchers.Default)
        assertThat(provider.io).isSameInstanceAs(Dispatchers.IO)
    }

    @Test
    fun `constructing the dispatcher provider does not require a main dispatcher`() {
        // Regression guard: reading Dispatchers.Main eagerly in the constructor would
        // make this class unconstructable in any plain JVM unit test.
        val provider = StandardDispatcherProvider()

        assertThat(provider).isNotNull()
    }

    @OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain
    @Test
    fun `the main dispatchers resolve once a main dispatcher is installed`() {
        Dispatchers.setMain(StandardTestDispatcher())
        try {
            val provider = StandardDispatcherProvider()

            assertThat(provider.main).isSameInstanceAs(Dispatchers.Main)
            assertThat(provider.mainImmediate).isSameInstanceAs(Dispatchers.Main.immediate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `the system time provider returns a plausible wall clock and a monotonic counter`() {
        val provider = SystemTimeProvider()

        val wallClock = provider.nowMillis()
        val first = provider.elapsedRealtimeNanos()
        val second = provider.elapsedRealtimeNanos()

        assertThat(wallClock).isGreaterThan(1_600_000_000_000L)
        assertThat(second).isAtLeast(first)
    }

    @Test
    fun `the no-op telemetry accepts everything and does nothing`() {
        NoOpTelemetry.setAttribute("engine", "fused")
        NoOpTelemetry.recordEvent("tracking_gap", mapOf("seconds" to "840"))
        NoOpTelemetry.recordNonFatal(IllegalStateException("boom"))
        NoOpTelemetry.recordEvent("no_attributes")
    }
}
