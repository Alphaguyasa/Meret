package et.alpha.meret.core.model.geo

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GeoTypesTest {
    @Test
    fun `a coordinate inside the WGS84 ranges is valid`() {
        assertThat(LatLng(9.0107, 38.7613).isValid).isTrue()
        assertThat(LatLng(0.0, 0.0).isValid).isTrue()
        assertThat(LatLng(-90.0, -180.0).isValid).isTrue()
        assertThat(LatLng(90.0, 180.0).isValid).isTrue()
    }

    @Test
    fun `a coordinate outside the WGS84 ranges is not valid`() {
        assertThat(LatLng(90.1, 0.0).isValid).isFalse()
        assertThat(LatLng(-90.1, 0.0).isValid).isFalse()
        assertThat(LatLng(0.0, 180.1).isValid).isFalse()
        assertThat(LatLng(0.0, -180.1).isValid).isFalse()
    }

    @Test
    fun `a NaN coordinate is not valid`() {
        // Some devices have been seen reporting NaN after a provider restart. It must not
        // pass the gate, and it must not be a special case anywhere else.
        assertThat(LatLng(Double.NaN, 38.0).isValid).isFalse()
        assertThat(LatLng(9.0, Double.NaN).isValid).isFalse()
        assertThat(LatLng(Double.POSITIVE_INFINITY, 0.0).isValid).isFalse()
    }

    @Test
    fun `coordinates are value types`() {
        val point = LatLng(9.0, 38.0)

        assertThat(point).isEqualTo(LatLng(9.0, 38.0))
        assertThat(point.hashCode()).isEqualTo(LatLng(9.0, 38.0).hashCode())
        assertThat(point.copy(latitude = 10.0)).isEqualTo(LatLng(10.0, 38.0))
        assertThat(point.toString()).contains("9.0")
    }

    @Test
    fun `a bounding box reports its centre and spans`() {
        val box =
            BoundingBox(
                minLatitude = 8.0,
                minLongitude = 38.0,
                maxLatitude = 10.0,
                maxLongitude = 39.0,
            )

        assertThat(box.center).isEqualTo(LatLng(9.0, 38.5))
        assertThat(box.latitudeSpan).isEqualTo(2.0)
        assertThat(box.longitudeSpan).isEqualTo(1.0)
    }

    @Test
    fun `a bounding box contains points inside it and rejects points outside`() {
        val box = BoundingBox(8.0, 38.0, 10.0, 39.0)

        assertThat(LatLng(9.0, 38.5) in box).isTrue()
        assertThat(LatLng(8.0, 38.0) in box).isTrue() // corners count
        assertThat(LatLng(7.9, 38.5) in box).isFalse()
        assertThat(LatLng(9.0, 39.1) in box).isFalse()
    }

    @Test
    fun `bounding boxes intersect when they overlap or touch`() {
        val box = BoundingBox(8.0, 38.0, 10.0, 39.0)

        assertThat(box.intersects(BoundingBox(9.0, 38.5, 11.0, 40.0))).isTrue()
        assertThat(box.intersects(BoundingBox(10.0, 39.0, 12.0, 41.0))).isTrue() // edge contact
        assertThat(box.intersects(BoundingBox(11.0, 40.0, 12.0, 41.0))).isFalse()
        assertThat(box.intersects(box)).isTrue()
    }

    @Test
    fun `the union of two boxes covers both`() {
        val west = BoundingBox(8.0, 37.0, 9.0, 38.0)
        val east = BoundingBox(8.5, 39.0, 10.0, 40.0)

        val union = west.union(east)

        assertThat(union).isEqualTo(BoundingBox(8.0, 37.0, 10.0, 40.0))
        assertThat(union.intersects(west)).isTrue()
        assertThat(union.intersects(east)).isTrue()
    }

    @Test
    fun `the corners walk the box in order and all lie inside it`() {
        val box = BoundingBox(8.0, 38.0, 10.0, 39.0)

        val corners = box.corners

        assertThat(corners).hasSize(4)
        assertThat(corners)
            .containsExactly(
                LatLng(8.0, 38.0),
                LatLng(8.0, 39.0),
                LatLng(10.0, 39.0),
                LatLng(10.0, 38.0),
            ).inOrder()
        assertThat(corners.all { it in box }).isTrue()
    }

    @Test
    fun `bounding boxes are value types`() {
        val box = BoundingBox(8.0, 38.0, 10.0, 39.0)

        assertThat(box).isEqualTo(BoundingBox(8.0, 38.0, 10.0, 39.0))
        assertThat(box.hashCode()).isEqualTo(BoundingBox(8.0, 38.0, 10.0, 39.0).hashCode())
        assertThat(box.copy(maxLatitude = 11.0).maxLatitude).isEqualTo(11.0)
        assertThat(box.toString()).contains("38.0")
    }
}
