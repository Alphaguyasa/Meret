package et.alpha.meret.feature.map

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.BoundingBox
import org.junit.Test

class EarlierTracksAreaTest {
    @Test
    fun `the first area is the screen with half a screen on every side`() {
        val area = EarlierTracksArea.next(loaded = null, visible = SCREEN)

        assertThat(area.minLatitude).isWithin(TOLERANCE).of(8.98)
        assertThat(area.maxLatitude).isWithin(TOLERANCE).of(9.06)
        assertThat(area.minLongitude).isWithin(TOLERANCE).of(38.70)
        assertThat(area.maxLongitude).isWithin(TOLERANCE).of(38.78)
    }

    @Test
    fun `a pan of less than half a screen keeps the loaded area`() {
        val loaded = EarlierTracksArea.next(null, SCREEN)

        val panned = SCREEN.shifted(latitude = 0.015, longitude = -0.015)

        assertThat(EarlierTracksArea.next(loaded, panned)).isSameInstanceAs(loaded)
    }

    @Test
    fun `a longer pan loads around the new screen`() {
        val loaded = EarlierTracksArea.next(null, SCREEN)
        val panned = SCREEN.shifted(latitude = 0.03, longitude = 0.0)

        val next = EarlierTracksArea.next(loaded, panned)

        assertThat(next).isEqualTo(EarlierTracksArea.next(null, panned))
    }

    @Test
    fun `zooming out loads a bigger area`() {
        val loaded = EarlierTracksArea.next(null, SCREEN)
        val zoomedOut = BoundingBox(8.96, 38.68, 9.08, 38.80)

        assertThat(EarlierTracksArea.next(loaded, zoomedOut))
            .isEqualTo(EarlierTracksArea.next(null, zoomedOut))
    }

    @Test
    fun `about one zoom level in keeps the area, two load a smaller one`() {
        val loaded = EarlierTracksArea.next(null, SCREEN)
        val oneLevel = BoundingBox(9.009, 38.729, 9.031, 38.751)
        val twoLevels = BoundingBox(9.015, 38.735, 9.025, 38.745)

        assertThat(EarlierTracksArea.next(loaded, oneLevel)).isSameInstanceAs(loaded)
        assertThat(EarlierTracksArea.next(loaded, twoLevels))
            .isEqualTo(EarlierTracksArea.next(null, twoLevels))
    }

    @Test
    fun `the area never runs past the poles or the antimeridian`() {
        val world = BoundingBox(-80.0, -170.0, 80.0, 170.0)

        val area = EarlierTracksArea.next(null, world)

        assertThat(area).isEqualTo(BoundingBox(-85.0, -180.0, 85.0, 180.0))
    }

    private fun BoundingBox.shifted(
        latitude: Double,
        longitude: Double,
    ) = BoundingBox(
        minLatitude = minLatitude + latitude,
        minLongitude = minLongitude + longitude,
        maxLatitude = maxLatitude + latitude,
        maxLongitude = maxLongitude + longitude,
    )

    private companion object {
        /** About 4 km across, around Addis Ababa. */
        val SCREEN = BoundingBox(9.0, 38.72, 9.04, 38.76)
        const val TOLERANCE = 1e-9
    }
}
