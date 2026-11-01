package et.alpha.meret.domain.waypoint

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.DevicePosition
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SaveWaypointUseCaseTest {
    private val store = FakeWaypointStore()
    private val saveWaypoint = SaveWaypointUseCase(store)

    private val here =
        DevicePosition(
            position = LatLng(9.0301, 38.7402),
            accuracyMetres = 4.5,
            isLive = true,
            altitudeMetres = 2_355.0,
        )

    @Test
    fun `while idle the waypoint is saved where the phone is, on no track`() = runTest {
        val input =
            WaypointInput(
                title = "Borehole",
                category = WaypointCategory.WATER_POINT,
                notes = "Pump broken",
            )

        val result = saveWaypoint(fix = here, recordingTrackId = null, input = input)

        assertThat(result).isEqualTo(AppResult.Success("waypoint-1"))
        assertThat(store.saved)
            .containsExactly(
                NewWaypoint(
                    position = LatLng(9.0301, 38.7402),
                    accuracyMetres = 4.5,
                    altitudeMetres = 2_355.0,
                    trackId = null,
                    category = WaypointCategory.WATER_POINT,
                    title = "Borehole",
                    notes = "Pump broken",
                ),
            )
    }

    @Test
    fun `while recording the waypoint belongs to the track`() = runTest {
        saveWaypoint(fix = here, recordingTrackId = "track-7", input = WaypointInput())

        assertThat(store.saved.single().trackId).isEqualTo("track-7")
    }

    @Test
    fun `with no fix nothing is written`() = runTest {
        val result = saveWaypoint(fix = null, recordingTrackId = null, input = WaypointInput("A"))

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix)))
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `a cached fix is not a position to save at`() = runTest {
        val cached = here.copy(isLive = false)

        val result = saveWaypoint(fix = cached, recordingTrackId = null, input = WaypointInput())

        assertThat(result).isEqualTo(AppResult.Failure(AppError.Gps(AppError.Gps.Kind.NoFix)))
        assertThat(store.saved).isEmpty()
    }

    @Test
    fun `text is trimmed and blank text is saved as none`() = runTest {
        val input = WaypointInput(title = "  Kebele office \n", notes = "   ")

        saveWaypoint(fix = here, recordingTrackId = null, input = input)

        assertThat(store.saved.single().title).isEqualTo("Kebele office")
        assertThat(store.saved.single().notes).isNull()
        assertThat(store.saved.single().category).isNull()
    }

    @Test
    fun `a failed write is returned as it is`() = runTest {
        val failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.OutOfSpace))
        store.failure = failure

        assertThat(saveWaypoint(fix = here, recordingTrackId = null, input = WaypointInput()))
            .isEqualTo(failure)
    }

    @Test
    fun `category keys round-trip and unknown keys are no category`() {
        WaypointCategory.entries.forEach { category ->
            assertThat(WaypointCategory.fromKey(category.key)).isEqualTo(category)
        }
        assertThat(WaypointCategory.fromKey("granary")).isNull()
        assertThat(WaypointCategory.fromKey(null)).isNull()
        assertThat(WaypointCategory.entries.map { it.key }.toSet())
            .hasSize(WaypointCategory.entries.size)
    }

    private class FakeWaypointStore : WaypointStore {
        val saved = mutableListOf<NewWaypoint>()
        var failure: AppResult.Failure? = null

        override suspend fun save(waypoint: NewWaypoint): AppResult<String> {
            failure?.let { return it }
            saved += waypoint
            return AppResult.Success("waypoint-${saved.size}")
        }
    }
}
