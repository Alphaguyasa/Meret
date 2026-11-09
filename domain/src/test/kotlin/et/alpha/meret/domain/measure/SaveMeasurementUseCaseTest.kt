package et.alpha.meret.domain.measure

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.RingGeoJson
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.abs

class SaveMeasurementUseCaseTest {
    private val store = FakeMeasurementStore()
    private val save = SaveMeasurementUseCase(store)

    private val plot =
        listOf(
            LatLng(9.0300, 38.7400),
            LatLng(9.0312, 38.7403),
            LatLng(9.0310, 38.7415),
            LatLng(9.0298, 38.7411),
        )

    @Test
    fun `a plot's area matches a WGS84 GIS reference within 0,1 percent`() = runTest {
        // GeographicLib (pyproj Geod, WGS84): 17,632.74 m², 533.31 m.
        val result = save(plot, MeasurementMethod.TAPPED, "North field")

        assertThat(result).isEqualTo(AppResult.Success("saved"))
        val saved = store.saved.single()
        assertThat(abs(saved.areaSquareMetres - 17_632.74) / 17_632.74).isLessThan(0.001)
        assertThat(abs(saved.perimeterMetres - 533.31) / 533.31).isLessThan(0.005)
        assertThat(saved.method).isEqualTo(MeasurementMethod.TAPPED)
        assertThat(saved.name).isEqualTo("North field")
    }

    @Test
    fun `the stored GeoJSON round-trips to the ring`() = runTest {
        save(plot, MeasurementMethod.WALKED)

        assertThat(RingGeoJson.decode(store.saved.single().verticesGeoJson)).isEqualTo(plot)
    }

    @Test
    fun `a ring handed over closed is stored and measured as the same plot`() = runTest {
        save(plot, MeasurementMethod.WALKED)
        save(plot + plot.first(), MeasurementMethod.WALKED)

        assertThat(store.saved[1]).isEqualTo(store.saved[0])
    }

    @Test
    fun `a name is normalised like a track's, and no name stays empty`() = runTest {
        save(plot, MeasurementMethod.TAPPED, "  Kebele\n 04  ")
        save(plot, MeasurementMethod.TAPPED, "   ")

        assertThat(store.saved.map { it.name }).containsExactly("Kebele 04", "").inOrder()
    }

    @Test
    fun `a ring of two points is refused and nothing is written`() = runTest {
        assertRefused(plot.take(2))
    }

    @Test
    fun `a ring with only two distinct points is refused`() = runTest {
        assertRefused(listOf(plot[0], plot[1], plot[0], plot[1]))
    }

    @Test
    fun `corners on one line are refused`() = runTest {
        assertRefused(listOf(LatLng(9.0, 38.0), LatLng(9.001, 38.0), LatLng(9.002, 38.0)))
    }

    @Test
    fun `an invalid coordinate is refused`() = runTest {
        assertRefused(plot + LatLng(95.0, 38.0))
        assertRefused(plot + LatLng(Double.NaN, 38.0))
    }

    @Test
    fun `no vertices are refused`() = runTest {
        assertRefused(emptyList())
    }

    @Test
    fun `a storage failure is passed on`() = runTest {
        val failure = AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed))
        store.next = failure

        assertThat(save(plot, MeasurementMethod.TAPPED)).isEqualTo(failure)
    }

    private suspend fun assertRefused(vertices: List<LatLng>) {
        val result = save(vertices, MeasurementMethod.TAPPED)

        assertThat(result)
            .isEqualTo(
                AppResult.Failure(AppError.Invalid(setOf(SaveMeasurementUseCase.FIELD_VERTICES))),
            )
        assertThat(store.saved).isEmpty()
    }

    private class FakeMeasurementStore : MeasurementStore {
        val saved = mutableListOf<NewMeasurement>()
        var next: AppResult<String> = AppResult.Success("saved")

        override suspend fun save(measurement: NewMeasurement): AppResult<String> {
            saved += measurement
            return next
        }
    }
}
