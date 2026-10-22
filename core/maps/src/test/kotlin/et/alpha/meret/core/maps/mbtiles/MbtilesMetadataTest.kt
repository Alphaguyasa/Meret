package et.alpha.meret.core.maps.mbtiles

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class MbtilesMetadataTest {
    private val required = mapOf("format" to "pbf", "minzoom" to "0", "maxzoom" to "14")

    @Test
    fun `a pack with only the required rows is accepted`() {
        val metadata = MbtilesMetadata.fromRows(required)

        assertThat(metadata.format).isEqualTo("pbf")
        assertThat(metadata.minZoom).isEqualTo(0)
        assertThat(metadata.maxZoom).isEqualTo(14)
        assertThat(metadata.bounds).isNull()
        assertThat(metadata.center).isNull()
        assertThat(metadata.json).isNull()
    }

    @Test
    fun `a pack missing format or a zoom bound is rejected`() {
        for (key in required.keys) {
            assertThrows(InvalidMbtilesException::class.java) {
                MbtilesMetadata.fromRows(required - key)
            }
        }
        assertThrows(InvalidMbtilesException::class.java) {
            MbtilesMetadata.fromRows(required + ("format" to " "))
        }
    }

    @Test
    fun `an unusable zoom range is rejected`() {
        val bad =
            listOf(
                "minzoom" to "15",
                "maxzoom" to "fourteen",
                "maxzoom" to "31",
                "minzoom" to "-1",
            )
        for (row in bad) {
            assertThrows(InvalidMbtilesException::class.java) {
                MbtilesMetadata.fromRows(required + row)
            }
        }
    }

    @Test
    fun `bounds and center tolerate spaces`() {
        val metadata =
            MbtilesMetadata.fromRows(
                required +
                    mapOf(
                        "bounds" to "32.99, 3.4, 47.99, 14.89",
                        "center" to " 38.75, 9.03, 6 ",
                    ),
            )

        assertThat(metadata.bounds).isEqualTo(TileBounds(32.99, 3.4, 47.99, 14.89))
        assertThat(metadata.center).isEqualTo(TileCenter(38.75, 9.03, 6))
    }

    @Test
    fun `malformed optional rows become null instead of failing the pack`() {
        val malformed =
            listOf(
                "bounds" to "32.99,3.40,47.99",
                "bounds" to "west,south,east,north",
                "bounds" to "32.99,14.89,47.99,3.40",
                "bounds" to "200,3.40,47.99,14.89",
                "center" to "38.75,9.03",
                "center" to "38.75,91,6",
                "center" to "38.75,9.03,6.5",
            )
        for ((key, value) in malformed) {
            val metadata = MbtilesMetadata.fromRows(required + (key to value))
            assertThat(metadata.bounds).isNull()
            assertThat(metadata.center).isNull()
        }
    }
}
