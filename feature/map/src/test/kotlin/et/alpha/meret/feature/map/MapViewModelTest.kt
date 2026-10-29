package et.alpha.meret.feature.map

import androidx.lifecycle.viewModelScope
import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.location.DevicePositionRepository
import et.alpha.meret.core.data.tilepack.TilePackRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.designsystem.component.RecordingState
import et.alpha.meret.core.maps.OfflineMapSession
import et.alpha.meret.core.maps.server.LocalTileServer
import et.alpha.meret.core.maps.style.StyleProvider
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.tracking.NewTrackStore
import et.alpha.meret.domain.tracking.RecordingCommands
import et.alpha.meret.domain.tracking.StartTrackingUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
class MapViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val packs = MutableStateFlow<List<TilePack>>(emptyList())
    private val repository =
        object : TilePackRepository {
            override fun observeTilePacks(): Flow<List<TilePack>> = packs

            override suspend fun importPack(
                displayName: String?,
                sizeBytes: Long?,
                open: () -> InputStream,
            ): AppResult<TilePack> = error("not used")

            override suspend fun deletePack(packId: String): AppResult<Unit> = error("not used")

            override suspend fun deleteUnregisteredPacks(): Int = error("not used")
        }
    private val tracking = FakeTrackingController()
    private val tracks = FakeNewTrackStore()
    private val commands = FakeRecordingCommands(tracking)
    private val here = MutableStateFlow<DevicePosition?>(null)
    private val positions =
        object : DevicePositionRepository {
            override fun observePosition(): Flow<DevicePosition?> = here
        }

    // Never acquired here: the map view does that, and these tests draw no map.
    private val session =
        OfflineMapSession(
            server = LocalTileServer { null },
            styles = StyleProvider({ error("no style in this test") }, File("unused"), dispatcher),
            io = dispatcher,
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `with no pack installed, the placeholder shows`() = runMapTest { viewModel ->
        viewModel.uiState.test {
            val state = awaitItem()
            assertThat(state.basemapPackId).isNull()
            assertThat(state.basemapInstalled).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the newest installed pack is drawn`() = runMapTest { viewModel ->
        packs.value = listOf(pack("addis-2026"), pack("ethiopia"))

        viewModel.uiState.test {
            val state = awaitPack("addis-2026")
            assertThat(state.basemapInstalled).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `installing and removing a pack reach the screen`() = runMapTest { viewModel ->
        viewModel.uiState.test {
            awaitPack(null)

            packs.value = listOf(pack("ethiopia"))
            awaitPack("ethiopia")

            packs.value = emptyList()
            awaitPack(null)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an older pack that covers the user wins`() = runMapTest { viewModel ->
        packs.value = listOf(pack("addis", ADDIS), pack("ethiopia", ETHIOPIA))
        here.value = position(13.5, 39.47) // Mekelle: outside Addis, inside Ethiopia

        viewModel.uiState.test {
            awaitPack("ethiopia")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `walking out of every pack keeps the newest one drawn`() = runMapTest { viewModel ->
        packs.value = listOf(pack("addis", ADDIS), pack("ethiopia", ETHIOPIA))

        viewModel.uiState.test {
            here.value = position(13.5, 39.47)
            awaitPack("ethiopia")

            here.value = position(-1.29, 36.82) // Nairobi
            awaitPack("addis")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the position reaches the screen for the puck`() = runMapTest { viewModel ->
        viewModel.position.test {
            assertThat(awaitItem()).isNull()

            here.value = position(9.03, 38.74)
            assertThat(awaitItem()).isEqualTo(position(9.03, 38.74))
            cancelAndIgnoreRemainingEvents()
        }
    }

    /**
     * The first item may be the initial state or the first pack list, depending on when the
     * upstream starts; skip until [packId] is drawn. Times out if it never is.
     */
    private suspend fun ReceiveTurbine<MapUiState>.awaitPack(packId: String?): MapUiState {
        var state = awaitItem()
        while (state.basemapPackId != packId) state = awaitItem()
        return state
    }

    @Test
    fun `a tap with nothing recording starts a new track`() = runMapTest { viewModel ->
        viewModel.onRecordTap()

        assertThat(tracks.created).containsExactly("track-1")
        assertThat(commands.sent).containsExactly("start track-1")
        assertThat(tracking.state.value).isEqualTo(TrackingState.Recording("track-1", false))
    }

    @Test
    fun `a second tap before the service records opens no second track`() =
        runMapTest { viewModel ->
            commands.serviceRuns = false

            viewModel.onRecordTap()
            viewModel.onRecordTap()

            assertThat(tracks.created).containsExactly("track-1")
            assertThat(commands.sent).containsExactly("start track-1")
        }

    @Test
    fun `a tap pauses a recording and the next tap resumes it`() = runMapTest { viewModel ->
        tracking.state.value = TrackingState.Recording("track-1", isPaused = false)
        viewModel.onRecordTap()
        tracking.state.value = TrackingState.Recording("track-1", isPaused = true)
        viewModel.onRecordTap()

        assertThat(commands.sent).containsExactly("pause", "resume").inOrder()
        assertThat(tracks.created).isEmpty()
    }

    @Test
    fun `a long-press stops a recording, paused or not, and does nothing while idle`() =
        runMapTest { viewModel ->
            viewModel.onRecordLongPress()
            tracking.state.value = TrackingState.Recording("track-1", isPaused = true)
            viewModel.onRecordLongPress()

            assertThat(commands.sent).containsExactly("stop")
        }

    @Test
    fun `a refused start is reported and leaves no track`() = runMapTest { viewModel ->
        commands.refuse = true

        viewModel.events.test {
            viewModel.onRecordTap()

            assertThat(awaitItem()).isEqualTo(MapEvent.RecordingNotStarted)
            assertThat(tracks.created).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a service that never records is reported, and a new tap may try again`() =
        runMapTest { viewModel ->
            commands.serviceRuns = false

            viewModel.events.test {
                viewModel.onRecordTap()
                advanceTimeBy(9_999L)
                expectNoEvents()
                advanceTimeBy(2L)
                assertThat(awaitItem()).isEqualTo(MapEvent.RecordingNotStarted)

                viewModel.onRecordTap()
                assertThat(commands.sent).containsExactly("start track-1", "start track-2")
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the recording's numbers reach the tracking bar`() = runMapTest { viewModel ->
        tracking.state.value = TrackingState.Recording("track-1", isPaused = true)
        tracking.activeTrack.value = track("track-1", points = 42, metres = 1_234.5)
        tracking.health.value = TrackingHealth(lastAccuracyMetres = 4.4)

        viewModel.uiState.test {
            var state = awaitItem()
            while (state.recordingState != RecordingState.Paused) state = awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(viewModel.recording.value).isEqualTo(
            RecordingReadout(
                state = RecordingState.Paused,
                startedAtMillis = 1_000L,
                distanceMetres = 1_234.5,
                pointCount = 42,
                accuracyMetres = 4.4,
            ),
        )
    }

    @Test
    fun `another open track in Room is not shown as the recording`() {
        val readout =
            recordingReadout(
                TrackingState.Recording("track-2", isPaused = false),
                track("track-1", points = 42, metres = 10.0),
                TrackingHealth(),
            )

        assertThat(readout?.pointCount).isEqualTo(0)
        assertThat(readout?.startedAtMillis).isNull()
        assertThat(recordingReadout(TrackingState.Idle, null, TrackingHealth())).isNull()
    }

    private fun runMapTest(body: suspend TestScope.(MapViewModel) -> Unit) = runTest(dispatcher) {
        val viewModel =
            MapViewModel(
                repository,
                positions,
                session,
                tracking,
                StartTrackingUseCase(tracks, commands),
                commands,
            )
        // Subscribed, as the screen is, so the readout follows the controller.
        backgroundScope.launch { viewModel.recording.collect {} }
        try {
            body(viewModel)
        } finally {
            viewModel.viewModelScope.coroutineContext.job
                .cancelAndJoin()
        }
    }

    private fun track(
        id: String,
        points: Int,
        metres: Double,
    ) = Track(
        id = id,
        projectId = "local",
        deviceId = "device",
        userId = "local",
        startedAt = 1_000L,
        status = TrackStatus.ACTIVE,
        pointCount = points,
        distanceMeters = metres,
        updatedAt = 1_000L,
    )

    private class FakeTrackingController : TrackingController {
        override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)
        override val activeTrack = MutableStateFlow<Track?>(null)
        override val health = MutableStateFlow(TrackingHealth())

        override suspend fun start(trackId: String): AppResult<Unit> = error("the service's call")

        override suspend fun restore(): AppResult<TrackingState> = error("the service's call")

        override suspend fun interruptedTrackId(): String? = error("the service's call")

        override suspend fun pause(): Unit = error("the service's call")

        override suspend fun resume(): AppResult<Unit> = error("the service's call")

        override suspend fun stop(): Unit = error("the service's call")
    }

    private class FakeNewTrackStore : NewTrackStore {
        val created = mutableListOf<String>()
        private var next = 0

        override suspend fun create(): AppResult<String> {
            val id = "track-${++next}"
            created += id
            return AppResult.Success(id)
        }

        override suspend fun discardIfEmpty(trackId: String) {
            created -= trackId
        }
    }

    /** The service: records into a started track at once, unless told it does not run. */
    private class FakeRecordingCommands(
        private val tracking: FakeTrackingController,
    ) : RecordingCommands {
        val sent = mutableListOf<String>()
        var serviceRuns = true
        var refuse = false

        override fun start(trackId: String): AppResult<Unit> {
            if (refuse) return AppResult.Failure(AppError.Unexpected(IllegalStateException()))
            sent += "start $trackId"
            if (serviceRuns) tracking.state.value = TrackingState.Recording(trackId, false)
            return AppResult.Success(Unit)
        }

        override fun pause() {
            sent += "pause"
        }

        override fun resume() {
            sent += "resume"
        }

        override fun stop() {
            sent += "stop"
        }
    }

    private fun position(
        latitude: Double,
        longitude: Double,
    ) = DevicePosition(LatLng(latitude, longitude), accuracyMetres = 5.0, isLive = true)

    private fun pack(
        id: String,
        bounds: BoundingBox = ETHIOPIA,
    ) = TilePack(
        id = id,
        name = id,
        regionCode = "ET",
        path = "/packs/$id.mbtiles",
        bytes = 1L,
        minZoom = 0,
        maxZoom = 14,
        bounds = bounds,
        source = TilePackSource.SIDELOADED,
        installedAt = 0L,
    )

    private companion object {
        val ETHIOPIA = BoundingBox(3.4, 33.0, 14.9, 48.0)
        val ADDIS = BoundingBox(8.8, 38.6, 9.1, 38.95)
    }
}
