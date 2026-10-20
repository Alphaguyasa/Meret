package et.alpha.meret.feature.settings.diagnostics

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.diagnostics.GapRecord
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.diagnostics.TrackingStatus
import et.alpha.meret.domain.onboarding.PermissionSnapshot
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * The snapshot the screen and the copied report are built from: every source feeds it, the
 * recording's changes reach it at once, and no position reaches the report even while the
 * engine holds a fix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DiagnosticsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val controller = FakeTrackingController()
    private val viewModels = mutableListOf<DiagnosticsViewModel>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `the snapshot carries every source`() = runDiagnosticsTest {
        viewModel().uiState.test {
            val ready = awaitReady()
            val snapshot = ready.snapshot

            assertThat(snapshot.engine).isEqualTo("PLATFORM")
            assertThat(snapshot.permissions).isEqualTo(PERMISSIONS)
            assertThat(snapshot.appVersionName).isEqualTo("0.1.0")
            assertThat(snapshot.deviceManufacturer).isEqualTo("TECNO")
            assertThat(snapshot.freeStorageBytes).isEqualTo(FREE_BYTES)
            assertThat(snapshot.nowElapsedNanos).isEqualTo(NOW_NANOS)
            assertThat(snapshot.trackingStatus).isEqualTo(TrackingStatus.IDLE)
            assertThat(ready.report).contains("Engine: PLATFORM")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a change in the recording reaches the screen at once`() = runDiagnosticsTest {
        viewModel().uiState.test {
            awaitReady()

            controller.state.value = TrackingState.Recording("track-1", isPaused = true)
            assertThat(awaitReady().snapshot.trackingStatus).isEqualTo(TrackingStatus.PAUSED)

            val gap = GapRecord(NOW_NANOS - 60_000_000_000L, 60_000, isOpen = true)
            controller.health.value = TrackingHealth(isRecording = true, gaps = listOf(gap))
            val ready = awaitReady()
            assertThat(ready.snapshot.health.gaps).containsExactly(gap)
            assertThat(ready.report).contains("still open")
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** The report is pasted into WhatsApp. The engine's fix must not reach it. */
    @Test
    fun `the copied report holds no coordinates`() = runDiagnosticsTest {
        controller.state.value = TrackingState.Recording("track-1", isPaused = false)
        controller.health.value =
            TrackingHealth(isRecording = true, fixCount = 3, lastAccuracyMetres = 4.0)

        viewModel().uiState.test {
            val report = awaitReady().report

            assertThat(report).doesNotContain(LAST_FIX.position.latitude.toString())
            assertThat(report).doesNotContain(LAST_FIX.position.longitude.toString())
            assertThat(report).doesNotContain("track-1")
            assertThat(report).doesNotContainMatch("""\d{1,3}\.\d{4,}""")
            cancelAndIgnoreRemainingEvents()
        }
    }

    private suspend fun ReceiveTurbine<DiagnosticsUiState>.awaitReady(): DiagnosticsUiState.Ready {
        while (true) {
            val item = awaitItem()
            if (item is DiagnosticsUiState.Ready) return item
        }
    }

    /** Ends every ViewModel's scope inside the test, so nothing resumes after `resetMain`. */
    private fun runDiagnosticsTest(body: suspend TestScope.() -> Unit) = runTest(dispatcher) {
        try {
            body()
        } finally {
            viewModels.forEach {
                it.viewModelScope.coroutineContext.job
                    .cancelAndJoin()
            }
        }
    }

    private fun viewModel() = DiagnosticsViewModel(
        controller = controller,
        engine = FakeEngine(),
        permissions = { PERMISSIONS },
        deviceInfo = {
            DeviceInfo(
                appVersionName = "0.1.0",
                appVersionCode = 1,
                manufacturer = "TECNO",
                model = "KI5q",
                freeStorageBytes = FREE_BYTES,
            )
        },
        timeProvider = FixedTime,
        dispatchers = TestDispatchers(dispatcher),
    ).also { viewModels += it }

    private class FakeTrackingController : TrackingController {
        override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)
        override val activeTrack: Flow<Track?> = flowOf(null)
        override val health = MutableStateFlow(TrackingHealth())

        override suspend fun start(trackId: String): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun restore(): AppResult<TrackingState> = AppResult.Success(state.value)

        override suspend fun interruptedTrackId(): String? = null

        override suspend fun pause() = Unit

        override suspend fun resume(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun stop() = Unit
    }

    /** A platform engine holding a fix at a known position, which must never be reported. */
    private class FakeEngine : LocationEngine {
        override val fixes: Flow<RawFix> = emptyFlow()
        override val capabilities =
            EngineCapabilities(
                kind = EngineKind.PLATFORM,
                supportsBatching = false,
                supportsGnssStatus = true,
                reportsSpeed = true,
            )

        override suspend fun start(config: TrackingConfig) = Unit

        override suspend fun stop() = Unit

        override suspend fun lastKnown(): RawFix = LAST_FIX
    }

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = 1_767_225_600_000L

        override fun elapsedRealtimeNanos(): Long = NOW_NANOS
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private companion object {
        const val NOW_NANOS = 90_000_000_000_000L
        const val FREE_BYTES = 2_000_000_000L

        val PERMISSIONS =
            PermissionSnapshot(
                sdkInt = 34,
                fineLocation = true,
                coarseLocation = true,
                notifications = true,
                backgroundLocation = true,
                ignoringBatteryOptimisations = true,
            )

        val LAST_FIX =
            RawFix(
                position = LatLng(9.0300512, 38.7400731),
                accuracyMetres = 4.0,
                elapsedRealtimeNanos = NOW_NANOS,
                recordedAtMillis = 1_767_225_600_000L,
                provider = LocationProvider.GPS,
            )
    }
}
