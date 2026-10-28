package et.alpha.meret.core.data.location

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.PreviewLocationSource
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.diagnostics.TrackingHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DevicePositionRepositoryTest {
    private val recording = TrackingState.Recording(trackId = "t1", isPaused = false)

    @Test
    fun `the cached fix comes first, marked stale, then live fixes`() = runTest {
        val engine = FakeEngine(cached = fix(9.01, 38.75, accuracy = 30.0))

        repository(engine, tracking = FakeTracking(recording)).observePosition().test {
            assertThat(awaitItem()).isEqualTo(position(9.01, 38.75, 30.0, isLive = false))

            engine.send(fix(9.02, 38.76, accuracy = 4.0))
            assertThat(awaitItem()).isEqualTo(position(9.02, 38.76, 4.0, isLive = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with no cached fix the puck waits for a live one`() = runTest {
        val engine = FakeEngine(cached = null)

        repository(engine).observePosition().test {
            assertThat(awaitItem()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `no location permission means no puck, not a crash`() = runTest {
        val engine = FakeEngine(cached = null, permissionDenied = true)
        val preview = FakePreview(permissionDenied = true)

        repository(engine, preview).observePosition().test {
            assertThat(awaitItem()).isNull()
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `fixes that cannot be drawn are skipped`() = runTest {
        val engine = FakeEngine(cached = fix(Double.NaN, 38.75, accuracy = 5.0))

        repository(engine, tracking = FakeTracking(recording)).observePosition().test {
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
        val tracking = FakeTracking(TrackingState.Idle)

        repository(engine, tracking = tracking).observePosition().test {
            awaitItem()
            tracking.state.value = recording
            tracking.state.value = recording.copy(isPaused = true)
            tracking.state.value = TrackingState.Idle
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(engine.commands).isEmpty()
    }

    @Test
    fun `with no recording the map gets live fixes of its own`() = runTest {
        val preview = FakePreview()

        repository(FakeEngine(cached = null), preview).observePosition().test {
            assertThat(awaitItem()).isNull()

            preview.send(fix(9.03, 38.74, accuracy = 5.0))
            assertThat(awaitItem()).isEqualTo(position(9.03, 38.74, 5.0, isLive = true))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the map's request lasts exactly as long as someone watches`() = runTest {
        val preview = FakePreview()

        repository(FakeEngine(cached = null), preview).observePosition().test {
            awaitItem()
            preview.awaitRegistered()
            assertThat(preview.isRegistered).isTrue()
            cancelAndIgnoreRemainingEvents()
        }

        assertThat(preview.isRegistered).isFalse()
        assertThat(preview.registrations).isEqualTo(1)
    }

    @Test
    fun `a recording that starts takes over from the map's request`() = runTest {
        val engine = FakeEngine(cached = null)
        val preview = FakePreview()
        val tracking = FakeTracking(TrackingState.Idle)

        repository(engine, preview, tracking).observePosition().test {
            awaitItem()
            preview.awaitRegistered()

            tracking.state.value = recording
            engine.send(fix(9.04, 38.73, accuracy = 3.0))

            assertThat(awaitItem()).isEqualTo(position(9.04, 38.73, 3.0, isLive = true))
            assertThat(preview.isRegistered).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a paused recording gets the map's request back`() = runTest {
        val engine = FakeEngine(cached = null)
        val preview = FakePreview()
        val tracking = FakeTracking(recording)

        repository(engine, preview, tracking).observePosition().test {
            awaitItem()
            engine.awaitListening()
            assertThat(preview.registrations).isEqualTo(0)

            tracking.state.value = recording.copy(isPaused = true)
            preview.send(fix(9.05, 38.72, accuracy = 7.0))

            assertThat(awaitItem()).isEqualTo(position(9.05, 38.72, 7.0, isLive = true))
            assertThat(preview.registrations).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun repository(
        engine: FakeEngine,
        preview: FakePreview = FakePreview(),
        tracking: FakeTracking = FakeTracking(TrackingState.Idle),
    ) = DefaultDevicePositionRepository(engine, preview, tracking)

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

        suspend fun awaitListening() {
            source.subscriptionCount.first { it > 0 }
        }

        /** Delivers [fix] once the repository listens: a hot flow drops it before that. */
        suspend fun send(fix: RawFix) {
            awaitListening()
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

    /** Counts registrations the way a platform request would see them: collect to cancel. */
    private class FakePreview(
        private val permissionDenied: Boolean = false,
    ) : PreviewLocationSource {
        private val source = MutableSharedFlow<RawFix>()
        private val registered = MutableStateFlow(false)
        var registrations = 0
            private set

        val isRegistered: Boolean
            get() = registered.value

        override fun previewFixes(): Flow<RawFix> = if (permissionDenied) {
            flow { throw SecurityException("no location permission") }
        } else {
            source
                .onStart {
                    registrations++
                    registered.value = true
                }.onCompletion { registered.value = false }
        }

        suspend fun awaitRegistered() {
            source.subscriptionCount.first { it > 0 }
        }

        suspend fun send(fix: RawFix) {
            awaitRegistered()
            source.emit(fix)
        }
    }

    private class FakeTracking(
        initial: TrackingState,
    ) : TrackingController {
        override val state = MutableStateFlow(initial)
        override val activeTrack: Flow<Track?> = emptyFlow()
        override val health = MutableStateFlow(TrackingHealth())

        override suspend fun start(trackId: String): AppResult<Unit> = error("not used")

        override suspend fun restore(): AppResult<TrackingState> = error("not used")

        override suspend fun interruptedTrackId(): String? = null

        override suspend fun pause() = Unit

        override suspend fun resume(): AppResult<Unit> = error("not used")

        override suspend fun stop() = Unit
    }
}
