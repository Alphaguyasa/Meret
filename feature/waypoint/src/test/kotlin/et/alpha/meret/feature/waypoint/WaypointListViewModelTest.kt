package et.alpha.meret.feature.waypoint

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.waypoint.WaypointRepository
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.Waypoint
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.WaypointMarker
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.WaypointCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WaypointListViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val stored = MutableStateFlow<List<Waypoint>?>(null)
    private val repository =
        object : WaypointRepository {
            override fun observeMarkers(): Flow<List<WaypointMarker>> = error("not used")

            override fun observeWaypoints(): Flow<List<Waypoint>> = stored.filterNotNull()

            override suspend fun save(waypoint: NewWaypoint): AppResult<String> = error("not used")
        }

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loading until Room answers, then empty on a fresh install`() = runTest(dispatcher) {
        val viewModel = WaypointListViewModel(repository)
        backgroundScope.launch { viewModel.uiState.collect {} }
        assertThat(viewModel.uiState.value).isEqualTo(WaypointListUiState.Loading)

        stored.value = emptyList()

        assertThat(viewModel.uiState.value).isEqualTo(WaypointListUiState.Ready(emptyList()))
    }

    @Test
    fun `rows keep Room's newest-first order, and a new waypoint appears at the top`() =
        runTest(dispatcher) {
            val viewModel = WaypointListViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }
            stored.value = listOf(waypoint("older"))

            stored.value = listOf(waypoint("newer"), waypoint("older"))

            val ready = viewModel.uiState.value as WaypointListUiState.Ready
            assertThat(ready.waypoints.map { it.id }).containsExactly("newer", "older").inOrder()
        }

    @Test
    fun `a row carries what the list shows and the position the map centres on`() =
        runTest(dispatcher) {
            val viewModel = WaypointListViewModel(repository)
            backgroundScope.launch { viewModel.uiState.collect {} }

            stored.value =
                listOf(
                    waypoint("w-1").copy(
                        title = "Borehole",
                        category = "water_point",
                        syncState = SyncState.SYNCED,
                    ),
                )

            val row = (viewModel.uiState.value as WaypointListUiState.Ready).waypoints.single()
            assertThat(row)
                .isEqualTo(
                    WaypointItem(
                        id = "w-1",
                        title = "Borehole",
                        category = WaypointCategory.WATER_POINT,
                        otherCategoryKey = null,
                        recordedAtMillis = RECORDED_AT,
                        position = LatLng(9.03, 38.74),
                        syncState = SyncState.SYNCED,
                    ),
                )
        }

    @Test
    fun `a blank title counts as none, so the row falls back to the category or time`() {
        val row = waypoint("w-1").copy(title = "  ", category = "household").toItem()

        assertThat(row.title).isNull()
        assertThat(row.category).isEqualTo(WaypointCategory.HOUSEHOLD)
    }

    @Test
    fun `a category this build does not know is kept as its key, never dropped`() {
        val row = waypoint("w-1").copy(category = "health_post").toItem()

        assertThat(row.category).isNull()
        assertThat(row.otherCategoryKey).isEqualTo("health_post")
    }

    @Test
    fun `no category at all is neither known nor other`() {
        val row = waypoint("w-1").copy(category = null).toItem()

        assertThat(row.category).isNull()
        assertThat(row.otherCategoryKey).isNull()
    }

    private fun waypoint(id: String) = Waypoint(
        id = id,
        projectId = "local",
        position = LatLng(9.03, 38.74),
        accuracyMeters = 4.0,
        recordedAt = RECORDED_AT,
        updatedAt = RECORDED_AT,
    )

    private companion object {
        const val RECORDED_AT = 1_761_800_000_000L
    }
}
