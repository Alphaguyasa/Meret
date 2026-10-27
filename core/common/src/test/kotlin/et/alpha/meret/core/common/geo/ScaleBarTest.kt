package et.alpha.meret.core.common.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.math.cos
import kotlin.math.pow

class ScaleBarTest {
    @Test
    fun `picks the longest round distance that fits`() {
        // 96 dp at 1 km per dp: 96 km of ground, so a 50 km bar 50 dp long.
        assertThat(ScaleBars.fit(metresPerUnit = 1_000.0, maxLength = 96.0))
            .isEqualTo(ScaleBar(metres = 50_000.0, length = 50.0))
        assertThat(ScaleBars.fit(metresPerUnit = 1.0, maxLength = 300.0))
            .isEqualTo(ScaleBar(metres = 200.0, length = 200.0))
        assertThat(ScaleBars.fit(metresPerUnit = 1.0, maxLength = 19.0))
            .isEqualTo(ScaleBar(metres = 10.0, length = 10.0))
    }

    @Test
    fun `a distance that fits exactly uses the whole length`() {
        assertThat(ScaleBars.fit(metresPerUnit = 10.0, maxLength = 100.0))
            .isEqualTo(ScaleBar(metres = 1_000.0, length = 100.0))
    }

    @Test
    fun `Addis Ababa at zoom 15 gets a 200 m bar`() {
        // Web Mercator ground resolution at latitude 9.03, zoom 15, per 512-px tile:
        // 2 * pi * 6378137 * cos(lat) / (512 * 2^15) = 2.3591 m per dp.
        val metresPerDp =
            2 * Math.PI * 6_378_137.0 * cos(Math.toRadians(9.03)) / (512 * 2.0.pow(15))

        val bar = ScaleBars.fit(metresPerDp, maxLength = 96.0)!!

        assertThat(bar.metres).isEqualTo(200.0)
        assertThat(bar.length).isWithin(0.01).of(84.78)
    }

    @Test
    fun `the bar never overruns its space, across every zoom`() {
        var metresPerUnit = 0.02
        while (metresPerUnit < 100_000.0) {
            val bar = ScaleBars.fit(metresPerUnit, maxLength = 96.0)!!
            assertThat(bar.length).isAtMost(96.0 + 1e-9)
            // 1-2-5 steps: the next step up is at most 2.5x, so the bar is at least 40% full.
            assertThat(bar.length).isAtLeast(96.0 * 0.4 - 1e-9)
            metresPerUnit *= 1.07
        }
    }

    @Test
    fun `a whole power of ten computed with rounding error still fits`() {
        val bar = ScaleBars.fit(metresPerUnit = 0.1 * 3, maxLength = 1_000.0 / 0.3)!!

        assertThat(bar.length).isAtMost(1_000.0 / 0.3)
        assertThat(bar.metres).isAnyOf(500.0, 1_000.0)
    }

    @Test
    fun `no bar under one metre`() {
        assertThat(ScaleBars.fit(metresPerUnit = 0.01, maxLength = 96.0)).isNull()
    }

    @Test
    fun `no bar from a map that has not measured itself yet`() {
        assertThat(ScaleBars.fit(metresPerUnit = 0.0, maxLength = 96.0)).isNull()
        assertThat(ScaleBars.fit(metresPerUnit = -3.0, maxLength = 96.0)).isNull()
        assertThat(ScaleBars.fit(metresPerUnit = Double.NaN, maxLength = 96.0)).isNull()
        assertThat(ScaleBars.fit(Double.POSITIVE_INFINITY, maxLength = 96.0)).isNull()
        assertThat(ScaleBars.fit(metresPerUnit = 5.0, maxLength = 0.0)).isNull()
    }
}
