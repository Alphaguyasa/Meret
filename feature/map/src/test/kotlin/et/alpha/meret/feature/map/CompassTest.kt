package et.alpha.meret.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CompassTest {
    @Test
    fun `a map turned either way is measured from north the short way round`() {
        assertThat(degreesOffNorth(0.0)).isEqualTo(0.0)
        assertThat(degreesOffNorth(30.0)).isEqualTo(30.0)
        assertThat(degreesOffNorth(330.0)).isEqualTo(30.0)
        assertThat(degreesOffNorth(-30.0)).isEqualTo(30.0)
        assertThat(degreesOffNorth(180.0)).isEqualTo(180.0)
    }

    @Test
    fun `just short of a full turn is north up`() {
        assertThat(degreesOffNorth(359.9)).isWithin(1e-9).of(0.1)
        assertThat(degreesOffNorth(360.0)).isEqualTo(0.0)
        assertThat(degreesOffNorth(720.2)).isWithin(1e-9).of(0.2)
    }
}
