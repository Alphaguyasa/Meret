package et.alpha.meret.feature.map

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TrackingStatsFormatTest {
    @Test
    fun `durations read as hours, minutes and seconds`() {
        assertThat(formatDuration(0L)).isEqualTo("0:00:00")
        assertThat(formatDuration(59_999L)).isEqualTo("0:00:59")
        assertThat(formatDuration(6_127_000L)).isEqualTo("1:42:07")
        assertThat(formatDuration(36_000_000L)).isEqualTo("10:00:00")
    }

    @Test
    fun `a clock set back shows zero, not a negative time`() {
        assertThat(formatDuration(-5_000L)).isEqualTo("0:00:00")
    }

    @Test
    fun `whole metres below a kilometre, then kilometres to two places`() {
        assertThat(formatDistance(0.0)).isEqualTo(FormattedDistance("0", inKilometres = false))
        assertThat(formatDistance(999.9)).isEqualTo(FormattedDistance("999", inKilometres = false))
        assertThat(formatDistance(1_000.0)).isEqualTo(FormattedDistance("1.00", true))
        assertThat(formatDistance(4_823.0)).isEqualTo(FormattedDistance("4.82", true))
    }
}
