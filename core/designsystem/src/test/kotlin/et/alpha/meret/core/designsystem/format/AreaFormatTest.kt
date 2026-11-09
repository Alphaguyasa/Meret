package et.alpha.meret.core.designsystem.format

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class AreaFormatTest {
    @Test
    fun `whole square metres below 1,000, then hectares to two places`() {
        assertThat(formatArea(0.0)).isEqualTo(FormattedArea("0", AreaUnit.SQUARE_METRES))
        assertThat(formatArea(999.9)).isEqualTo(FormattedArea("999", AreaUnit.SQUARE_METRES))
        assertThat(formatArea(1_000.0)).isEqualTo(FormattedArea("0.10", AreaUnit.HECTARES))
        assertThat(formatArea(10_000.0)).isEqualTo(FormattedArea("1.00", AreaUnit.HECTARES))
        assertThat(formatArea(23_456.0)).isEqualTo(FormattedArea("2.35", AreaUnit.HECTARES))
    }

    @Test
    fun `an impossible area reads as zero`() {
        val zero = FormattedArea("0", AreaUnit.SQUARE_METRES)

        assertThat(formatArea(-5.0)).isEqualTo(zero)
        assertThat(formatArea(Double.NaN)).isEqualTo(zero)
        assertThat(formatArea(Double.POSITIVE_INFINITY)).isEqualTo(zero)
        assertThat(formatTimad(-5.0).value).isEqualTo("0.00")
        assertThat(formatTimad(Double.NaN).value).isEqualTo("0.00")
    }

    @Test
    fun `one hectare is four timad at the default size`() {
        assertThat(formatTimad(10_000.0)).isEqualTo(FormattedArea("4.00", AreaUnit.TIMAD))
    }

    @Test
    fun `the timad size is configurable`() {
        assertThat(formatTimad(3_000.0, hectaresPerTimad = 0.5).value).isEqualTo("0.60")
    }

    @Test
    fun `no area is zero timad`() {
        assertThat(formatTimad(0.0).value).isEqualTo("0.00")
    }

    @Test
    fun `a timad size that cannot be used falls back to the default`() {
        val expected = formatTimad(10_000.0, DEFAULT_HECTARES_PER_TIMAD)

        assertThat(formatTimad(10_000.0, hectaresPerTimad = 0.0)).isEqualTo(expected)
        assertThat(formatTimad(10_000.0, hectaresPerTimad = -1.0)).isEqualTo(expected)
        assertThat(formatTimad(10_000.0, hectaresPerTimad = Double.NaN)).isEqualTo(expected)
        assertThat(formatTimad(10_000.0, hectaresPerTimad = Double.POSITIVE_INFINITY))
            .isEqualTo(expected)
    }

    @Test
    fun `every unit has its own label`() {
        val labels = AreaUnit.entries.map { it.labelRes }

        assertThat(labels).containsNoDuplicates()
    }
}
