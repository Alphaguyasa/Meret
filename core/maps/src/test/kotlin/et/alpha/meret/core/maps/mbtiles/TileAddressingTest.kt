package et.alpha.meret.core.maps.mbtiles

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class TileAddressingTest {
    @Test
    fun `the row flip mirrors each zoom level`() {
        assertThat(xyzToTmsRow(z = 0, y = 0)).isEqualTo(0)
        assertThat(xyzToTmsRow(z = 1, y = 0)).isEqualTo(1)
        assertThat(xyzToTmsRow(z = 2, y = 1)).isEqualTo(2)
        // Addis Ababa (9.03 N, 38.76 E) at z14 is XYZ 9955/7779: 16383 - 7779 = 8604.
        assertThat(xyzToTmsRow(z = 14, y = 7_779)).isEqualTo(8_604)
    }

    @Test
    fun `the row flip is its own inverse`() {
        for (z in listOf(0, 3, 14, 30)) {
            for (y in listOf(0, (1 shl z) / 2, (1 shl z) - 1)) {
                assertThat(xyzToTmsRow(z, xyzToTmsRow(z, y))).isEqualTo(y)
            }
        }
    }

    @Test
    fun `the row flip refuses a zoom that overflows`() {
        assertThrows(IllegalArgumentException::class.java) { xyzToTmsRow(z = 31, y = 0) }
        assertThrows(IllegalArgumentException::class.java) { xyzToTmsRow(z = -1, y = 0) }
    }

    @Test
    fun `only addresses inside the grid are valid`() {
        assertThat(isValidTile(0, 0, 0)).isTrue()
        assertThat(isValidTile(14, 16_383, 16_383)).isTrue()
        assertThat(isValidTile(14, 16_384, 0)).isFalse()
        assertThat(isValidTile(2, 0, 4)).isFalse()
        assertThat(isValidTile(2, -1, 0)).isFalse()
        assertThat(isValidTile(31, 0, 0)).isFalse()
    }

    @Test
    fun `gzip is detected from the first two bytes`() {
        assertThat(isGzip(byteArrayOf(0x1f, 0x8b.toByte(), 0x08))).isTrue()
        assertThat(isGzip(byteArrayOf(0x1a, 0x05))).isFalse()
        assertThat(isGzip(byteArrayOf(0x1f))).isFalse()
        assertThat(isGzip(byteArrayOf())).isFalse()
    }
}
