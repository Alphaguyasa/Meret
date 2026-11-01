package et.alpha.meret.feature.map

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.waypoint.WaypointRepository
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.WaypointMarker
import et.alpha.meret.domain.waypoint.NewWaypoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapWaypointsViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private val markers = MutableStateFlow<List<WaypointMarker>>(emptyList())
    private val repository =
        object : WaypointRepository {
            override fun observeMarkers() = markers

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
    fun `the map draws what Room holds, and a new waypoint as soon as it is saved`() =
        runTest(dispatcher) {
            val viewModel = MapWaypointsViewModel(repository)
            backgroundScope.launch { viewModel.markers.collect {} }
            assertThat(viewModel.markers.value).isEmpty()

            val saved = WaypointMarker("w-1", LatLng(9.03, 38.74), "household")
            markers.value = listOf(saved)

            assertThat(viewModel.markers.value).containsExactly(saved)
        }
}
