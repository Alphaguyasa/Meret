package et.alpha.meret.core.model.geo

/**
 * An axis-aligned latitude/longitude rectangle.
 *
 * Does **not** model antimeridian wrapping: [minLongitude] is always west of
 * [maxLongitude] in the plain numeric sense. Every deployment area this app targets sits
 * far from 180°, and a wrapping box would make every containment test more expensive for
 * a case that never occurs. If that changes, it changes here and in
 * `GeoMath.boundingBox`, together.
 */
public data class BoundingBox(
    public val minLatitude: Double,
    public val minLongitude: Double,
    public val maxLatitude: Double,
    public val maxLongitude: Double,
) {
    public val center: LatLng
        get() =
            LatLng(
                latitude = (minLatitude + maxLatitude) / 2.0,
                longitude = (minLongitude + maxLongitude) / 2.0,
            )

    public val latitudeSpan: Double get() = maxLatitude - minLatitude

    public val longitudeSpan: Double get() = maxLongitude - minLongitude

    public operator fun contains(point: LatLng): Boolean =
        point.latitude in minLatitude..maxLatitude &&
            point.longitude in minLongitude..maxLongitude

    public fun intersects(other: BoundingBox): Boolean = minLatitude <= other.maxLatitude &&
        maxLatitude >= other.minLatitude &&
        minLongitude <= other.maxLongitude &&
        maxLongitude >= other.minLongitude

    public fun union(other: BoundingBox): BoundingBox = BoundingBox(
        minLatitude = minOf(minLatitude, other.minLatitude),
        minLongitude = minOf(minLongitude, other.minLongitude),
        maxLatitude = maxOf(maxLatitude, other.maxLatitude),
        maxLongitude = maxOf(maxLongitude, other.maxLongitude),
    )

    public val corners: List<LatLng>
        get() =
            listOf(
                LatLng(minLatitude, minLongitude),
                LatLng(minLatitude, maxLongitude),
                LatLng(maxLatitude, maxLongitude),
                LatLng(maxLatitude, minLongitude),
            )
}
