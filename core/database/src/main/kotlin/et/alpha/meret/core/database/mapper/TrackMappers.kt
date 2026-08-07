package et.alpha.meret.core.database.mapper

import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackGeometryPoint
import et.alpha.meret.core.database.entity.TrackPointEntity
import et.alpha.meret.core.database.entity.TrackWithPoints
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng

/**
 * The boundary between storage and the domain. Everything above `:core:data` sees the
 * `:core:model` types on the right of these functions and never a Room entity.
 *
 * The bounding box is the one place the two shapes genuinely differ: four nullable columns
 * on the way in, one nullable rectangle on the way out. Reading requires all four to be
 * present, so a row left half-written by an older build degrades to "no bounds" rather than
 * to a rectangle anchored at the equator.
 */
public fun TrackEntity.toDomain(): Track = Track(
    id = id,
    projectId = projectId,
    deviceId = deviceId,
    userId = userId,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    pointCount = pointCount,
    distanceMeters = distanceMeters,
    movingDurationMillis = movingDurationMillis,
    bounds = toBoundingBox(),
    simplifiedGeoJson = simplifiedGeoJson,
    purpose = purpose,
    notes = notes,
    syncState = syncState,
    updatedAt = updatedAt,
)

public fun Track.toEntity(): TrackEntity = TrackEntity(
    id = id,
    projectId = projectId,
    deviceId = deviceId,
    userId = userId,
    name = name,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    pointCount = pointCount,
    distanceMeters = distanceMeters,
    movingDurationMillis = movingDurationMillis,
    bboxMinLatitude = bounds?.minLatitude,
    bboxMinLongitude = bounds?.minLongitude,
    bboxMaxLatitude = bounds?.maxLatitude,
    bboxMaxLongitude = bounds?.maxLongitude,
    simplifiedGeoJson = simplifiedGeoJson,
    purpose = purpose,
    notes = notes,
    syncState = syncState,
    updatedAt = updatedAt,
)

public fun TrackPointEntity.toDomain(): TrackPoint = TrackPoint(
    id = id,
    trackId = trackId,
    segmentIndex = segmentIndex,
    seq = seq,
    position = LatLng(latitude, longitude),
    altitudeMeters = altitudeMeters,
    accuracyMeters = accuracyMeters,
    verticalAccuracyMeters = verticalAccuracyMeters,
    bearingDegrees = bearingDegrees,
    speedMetersPerSecond = speedMetersPerSecond,
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
    provider = provider,
    satellitesUsed = satellitesUsed,
    recordedAt = recordedAt,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    isMock = isMock,
    filteredPosition = toFilteredPosition(),
    filterVariance = filterVariance,
    isOutlier = isOutlier,
)

public fun TrackPoint.toEntity(): TrackPointEntity = TrackPointEntity(
    id = id,
    trackId = trackId,
    segmentIndex = segmentIndex,
    seq = seq,
    latitude = position.latitude,
    longitude = position.longitude,
    altitudeMeters = altitudeMeters,
    accuracyMeters = accuracyMeters,
    verticalAccuracyMeters = verticalAccuracyMeters,
    bearingDegrees = bearingDegrees,
    speedMetersPerSecond = speedMetersPerSecond,
    speedAccuracyMetersPerSecond = speedAccuracyMetersPerSecond,
    provider = provider,
    satellitesUsed = satellitesUsed,
    recordedAt = recordedAt,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    isMock = isMock,
    filteredLatitude = filteredPosition?.latitude,
    filteredLongitude = filteredPosition?.longitude,
    filterVariance = filterVariance,
    isOutlier = isOutlier,
)

/**
 * Sorts on the way out. `@Relation` makes no ordering promise, and a track drawn in
 * storage order is a scribble.
 */
public fun TrackWithPoints.toDomain(): Pair<Track, List<TrackPoint>> = track.toDomain() to
    points
        .sortedWith(compareBy({ it.segmentIndex }, { it.seq }))
        .map(TrackPointEntity::toDomain)

/**
 * Splits a track's geometry into one polyline per segment.
 *
 * A segment break is a pause/resume. Drawing across one would connect the point where an
 * enumerator stopped to the point where they started again — a straight line down a road
 * they never walked, which on a coverage map is not a cosmetic error.
 *
 * Relies on the query's `ORDER BY segment_index, seq`, so the grouping preserves order.
 */
public fun List<TrackGeometryPoint>.toSegments(): List<List<LatLng>> = this
    .groupBy { it.segmentIndex }
    .toSortedMap()
    .values
    .map { segment -> segment.map { LatLng(it.latitude, it.longitude) } }

/** All four columns or none: three of four is a rectangle nobody measured. */
private fun TrackEntity.toBoundingBox(): BoundingBox? {
    val southWest = latLngOrNull(bboxMinLatitude, bboxMinLongitude)
    val northEast = latLngOrNull(bboxMaxLatitude, bboxMaxLongitude)

    return if (southWest != null && northEast != null) {
        BoundingBox(
            minLatitude = southWest.latitude,
            minLongitude = southWest.longitude,
            maxLatitude = northEast.latitude,
            maxLongitude = northEast.longitude,
        )
    } else {
        null
    }
}

private fun TrackPointEntity.toFilteredPosition(): LatLng? =
    latLngOrNull(filteredLatitude, filteredLongitude)
