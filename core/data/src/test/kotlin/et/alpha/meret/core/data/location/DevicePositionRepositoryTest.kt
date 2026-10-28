package et.alpha.meret.core.data.location

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DevicePositionRepositoryTest {
    @Test
    fun `the cached fix comes first, marked stale, then live fixes`() = runTest {
        val engine = FakeEngine(cached = fix(9.01, 38.75, accuracy = 30.0))
        val repository = DefaultDevicePositionRepository(engine)

        repository.observePosition().test {
            assertThat(awaitItem()).isEqualTo(position(9.01, 38.75, 30.0, isLive = false))

            engine.send(fix(9.02, 38.76, accuracy = 4.0))
            assertThat(awaitItem()).isEqualTo(position(9.02, 38.76, 4.0, isLive = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no cached fix the puck waits for a live one`() = runTest {
        val engine = FakeEngine(cached = null)

        DefaultDevicePositionRepository(engine).observePosition().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no location permission means no puck, not a crash`() = runTest {
        val engine = FakeEngine(cached = null, permissionDenied = true)

        DefaultDevicePositionRepository(engine).observePosition().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fixes that cannot be drawn are skipped`() = runTest {
        val engine = FakeEngine(cached = fix(Double.NaN, 38.75, accuracy = 5.0))

        DefaultDevicePositionRepository(engine).observePosition().test {
            assertThat(awaitItem()).isNull()

            engine.send(fix(95.0, 38.75, accuracy = 5.0))
            engine.send(fix(9.0, 38.75, accuracy = Double.NaN))
            engine.send(fix(9.0, 38.75, accuracy = 6.0))

            assertThat(awaitItem()).isEqualTo(position(9.0, 38.75, 6.0, isLive = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `watching the position never starts or stops the engine`() = runTest {
        val engine = FakeEngine(cached = null)

        DefaultDevicePositionRepository(engine).observePosition().test {
            awaitItem()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(engine.commands).isEmpty()
    }

    private fun position(
        latitude: Double,
        longitude: Double,
        accuracy: Double,
        isLive: Boolean,
    ) = DevicePosition(LatLng(latitude, longitude), accuracy, isLive)

    private fun fix(
        latitude: Double,
        longitude: Double,
        accuracy: Double,
    ) = RawFix(
        position = LatLng(latitude, longitude),
        accuracyMetres = accuracy,
        elapsedRealtimeNanos = 1_000L,
        recordedAtMillis = 1_000L,
        provider = LocationProvider.GPS,
    )

    private class FakeEngine(
        private val cached: RawFix?,
        private val permissionDenied: Boolean = false,
    ) : LocationEngine {
        private val source = MutableSharedFlow<RawFix>()
        val commands = mutableListOf<String>()

        override val fixes: Flow<RawFix> = source

        /** Delivers [fix] once the repository listens: a hot flow drops it before that. */
        suspend fun send(fix: RawFix) {
            source.subscriptionCount.first { it > 0 }
            source.emit(fix)
        }

        override val capabilities: EngineCapabilities =
            EngineCapabilities(
                kind = EngineKind.REPLAY,
                supportsBatching = false,
                supportsGnssStatus = false,
                reportsSpeed = false,
            )

        override suspend fun start(config: TrackingConfig) {
            commands += "start"
        }

        override suspend fun stop() {
            commands += "stop"
        }

        override suspend fun lastKnown(): RawFix? {
            if (permissionDenied) throw SecurityException("no location permission")
            return cached
        }
    }
}
