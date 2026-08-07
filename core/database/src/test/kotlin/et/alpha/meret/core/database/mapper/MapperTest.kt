package et.alpha.meret.core.database.mapper

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.entity.TrackGeometryPoint
import et.alpha.meret.core.database.entity.TrackWithPoints
import et.alpha.meret.core.model.Assignment
import et.alpha.meret.core.model.FormTemplate
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.Measurement
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.OutboxEntityType
import et.alpha.meret.core.model.OutboxEntry
import et.alpha.meret.core.model.OutboxOperation
import et.alpha.meret.core.model.Project
import et.alpha.meret.core.model.SyncConflict
import et.alpha.meret.core.model.TilePack
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackPoint
import et.alpha.meret.core.model.TrackPurpose
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.Waypoint
import et.alpha.meret.core.model.WaypointPhoto
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

/**
 * The storage boundary loses nothing.
 *
 * Round-tripping is the whole assertion: any field a mapper forgets to carry comes back
 * different, which is the failure mode these are guarding against — the columns and the
 * model drift apart silently otherwise, and only in the field.
 */
class MapperTest {
    @Test
    fun `a fully populated track round-trips`() {
        val track =
            Track(
                id = "t1",
                projectId = "p1",
                deviceId = "d1",
                userId = "u1",
                name = "Kebele 03 morning",
                startedAt = 1_000L,
                endedAt = 9_000L,
                status = TrackStatus.COMPLETED,
                pointCount = 412,
                distanceMeters = 3_140.5,
                movingDurationMillis = 7_200_000L,
                bounds = BoundingBox(8.9, 38.6, 9.1, 38.9),
                simplifiedGeoJson = """{"type":"LineString"}""",
                purpose = TrackPurpose.SURVEY,
                notes = "steep",
                updatedAt = 9_000L,
            )

        assertThat(track.toEntity().toDomain()).isEqualTo(track)
    }

    @Test
    fun `a track with no fixes yet has no bounds on either side`() {
        val track =
            Track(
                id = "t1",
                projectId = "p1",
                deviceId = "d1",
                userId = "u1",
                startedAt = 1_000L,
                status = TrackStatus.ACTIVE,
                updatedAt = 1_000L,
            )

        val entity = track.toEntity()

        assertThat(entity.bboxMinLatitude).isNull()
        assertThat(entity.bboxMaxLongitude).isNull()
        assertThat(entity.toDomain()).isEqualTo(track)
    }

    @Test
    fun `a half written bounding box degrades to none rather than to the equator`() {
        val entity =
            Track(
                id = "t1",
                projectId = "p1",
                deviceId = "d1",
                userId = "u1",
                startedAt = 1_000L,
                status = TrackStatus.ACTIVE,
                bounds = BoundingBox(8.9, 38.6, 9.1, 38.9),
                updatedAt = 1_000L,
            ).toEntity().copy(bboxMaxLatitude = null)

        assertThat(entity.toDomain().bounds).isNull()
    }

    @Test
    fun `a fix round-trips with both its raw and filtered position`() {
        val point =
            TrackPoint(
                id = "tp1",
                trackId = "t1",
                segmentIndex = 2,
                seq = 41,
                position = LatLng(9.0301, 38.7401),
                altitudeMeters = 2_355.0,
                accuracyMeters = 6.5,
                verticalAccuracyMeters = 3.0,
                bearingDegrees = 187.5,
                speedMetersPerSecond = 1.2,
                speedAccuracyMetersPerSecond = 0.4,
                provider = LocationProvider.GPS,
                satellitesUsed = 11,
                recordedAt = 1_700_000_000_000L,
                elapsedRealtimeNanos = 987_654_321_000L,
                isMock = true,
                filteredPosition = LatLng(9.0302, 38.7402),
                filterVariance = 0.75,
                isOutlier = true,
            )

        assertThat(point.toEntity().toDomain()).isEqualTo(point)
    }

    @Test
    fun `an unfiltered fix has no filtered position on either side`() {
        val point =
            TrackPoint(
                id = "tp1",
                trackId = "t1",
                seq = 1,
                position = LatLng(9.03, 38.74),
                accuracyMeters = 6.0,
                provider = LocationProvider.REPLAY,
                recordedAt = 1_000L,
                elapsedRealtimeNanos = 1_000_000_000L,
            )

        val entity = point.toEntity()

        assertThat(entity.filteredLatitude).isNull()
        assertThat(entity.toDomain().filteredPosition).isNull()
    }

    @Test
    fun `a track with points comes back sorted by segment then sequence`() {
        val track =
            Track(
                id = "t1",
                projectId = "p1",
                deviceId = "d1",
                userId = "u1",
                startedAt = 1_000L,
                status = TrackStatus.COMPLETED,
                updatedAt = 1_000L,
            )
        val shuffled =
            listOf(
                point(segmentIndex = 1, seq = 4),
                point(segmentIndex = 0, seq = 2),
                point(segmentIndex = 1, seq = 3),
                point(segmentIndex = 0, seq = 1),
            )

        val (mappedTrack, points) =
            TrackWithPoints(track.toEntity(), shuffled.map(TrackPoint::toEntity)).toDomain()

        assertThat(mappedTrack).isEqualTo(track)
        assertThat(points.map { it.segmentIndex to it.seq })
            .containsExactly(0 to 1, 0 to 2, 1 to 3, 1 to 4)
            .inOrder()
    }

    @Test
    fun `geometry splits into one polyline per segment`() {
        val geometry =
            listOf(
                TrackGeometryPoint(segmentIndex = 0, seq = 1, latitude = 9.01, longitude = 38.01),
                TrackGeometryPoint(segmentIndex = 0, seq = 2, latitude = 9.02, longitude = 38.02),
                TrackGeometryPoint(segmentIndex = 1, seq = 3, latitude = 9.03, longitude = 38.03),
            )

        val segments = geometry.toSegments()

        assertThat(segments).hasSize(2)
        assertThat(segments[0]).containsExactly(LatLng(9.01, 38.01), LatLng(9.02, 38.02)).inOrder()
        assertThat(segments[1]).containsExactly(LatLng(9.03, 38.03))
    }

    @Test
    fun `a track with no fixes has no segments to draw`() {
        assertThat(emptyList<TrackGeometryPoint>().toSegments()).isEmpty()
    }

    @Test
    fun `a waypoint and its photo round-trip`() {
        val waypoint =
            Waypoint(
                id = "w1",
                projectId = "p1",
                trackId = "t1",
                position = LatLng(9.03, 38.74),
                accuracyMeters = 5.0,
                altitudeMeters = 2_355.0,
                recordedAt = 1_000L,
                category = "farm",
                title = "የቤተሰብ እርሻ",
                notes = "teff",
                formTemplateId = "f1",
                formVersion = 3,
                formData = """{"crop":"ጤፍ"}""",
                baseVersion = 7,
                updatedAt = 2_000L,
            )
        val photo =
            WaypointPhoto(
                id = "ph1",
                waypointId = "w1",
                localPath = "/data/photos/ph1.jpg",
                remotePath = "photos/org/p1/w1/ph1.jpg",
                width = 1_600,
                height = 1_200,
                bytes = 240_000L,
                sha256 = "a".repeat(64),
                capturedAt = 1_000L,
                position = LatLng(9.03, 38.74),
                uploadAttempts = 2,
            )

        assertThat(waypoint.toEntity().toDomain()).isEqualTo(waypoint)
        assertThat(photo.toEntity().toDomain()).isEqualTo(photo)
    }

    @Test
    fun `a photo taken before the first fix has no position rather than half of one`() {
        val photo =
            WaypointPhoto(
                id = "ph1",
                waypointId = "w1",
                localPath = "/data/photos/ph1.jpg",
                width = 1_600,
                height = 1_200,
                bytes = 240_000L,
                sha256 = "a".repeat(64),
                capturedAt = 1_000L,
            )

        val entity = photo.toEntity()

        assertThat(entity.latitude).isNull()
        assertThat(entity.toDomain().position).isNull()
        assertThat(entity.copy(latitude = 9.03).toDomain().position).isNull()
    }

    @Test
    fun `the catalog types round-trip`() {
        val project =
            Project(
                id = "p1",
                orgId = "o1",
                name = "Oromia household survey",
                description = "2018 EC",
                defaultFormTemplateId = "f1",
                tilePackId = "pack-1",
                startsAt = 1_000L,
                endsAt = 9_000L,
                updatedAt = 5_000L,
                isArchived = true,
            )
        val template = FormTemplate("f1", "p1", 3, "Household", """{"fields":[]}""", 1_000L)
        val assignment =
            Assignment(
                id = "a1",
                projectId = "p1",
                assignedUserId = "u1",
                name = "Kebele 03",
                polygonGeoJson = """{"type":"Polygon"}""",
                dueAt = 9_000L,
                coveragePercent = 62.5,
            )
        val tilePack =
            TilePack(
                id = "pack-1",
                name = "Ethiopia z0-14",
                regionCode = "ET",
                path = "/data/tiles/ethiopia.mbtiles",
                bytes = 420_000_000L,
                minZoom = 0,
                maxZoom = 14,
                bounds = BoundingBox(3.4, 32.9, 15.0, 48.0),
                source = TilePackSource.SIDELOADED,
                installedAt = 1_000L,
                styleId = "style-1",
            )
        val measurement =
            Measurement(
                id = "m1",
                projectId = "p1",
                name = "North plot",
                kind = MeasurementKind.AREA,
                verticesGeoJson = """{"type":"Polygon"}""",
                areaSquareMeters = 2_500.0,
                perimeterMeters = 200.0,
                method = MeasurementMethod.WALKED,
                recordedAt = 1_000L,
                updatedAt = 1_000L,
            )

        assertThat(project.toEntity().toDomain()).isEqualTo(project)
        assertThat(template.toEntity().toDomain()).isEqualTo(template)
        assertThat(assignment.toEntity().toDomain()).isEqualTo(assignment)
        assertThat(tilePack.toEntity().toDomain()).isEqualTo(tilePack)
        assertThat(measurement.toEntity().toDomain()).isEqualTo(measurement)
    }

    @Test
    fun `the sync types round-trip`() {
        val entry =
            OutboxEntry(
                id = "o1",
                entityType = OutboxEntityType.TRACK_POINTS,
                entityId = "t1",
                operation = OutboxOperation.DELETE,
                payload = """{"points":[]}""",
                payloadSha256 = "b".repeat(64),
                priority = 10,
                attempts = 3,
                lastError = "503",
                nextAttemptAt = 9_000L,
                createdAt = 1_000L,
            )
        val conflict =
            SyncConflict(
                id = "c1",
                entityType = OutboxEntityType.WAYPOINT,
                entityId = "w1",
                localPayload = """{"title":"mine"}""",
                remotePayload = """{"title":"theirs"}""",
                detectedAt = 1_000L,
            )

        assertThat(entry.toEntity().toDomain()).isEqualTo(entry)
        assertThat(conflict.toEntity().toDomain()).isEqualTo(conflict)
    }

    private fun point(
        segmentIndex: Int,
        seq: Int,
    ) = TrackPoint(
        id = "tp-$segmentIndex-$seq",
        trackId = "t1",
        segmentIndex = segmentIndex,
        seq = seq,
        position = LatLng(9.03, 38.74),
        accuracyMeters = 6.0,
        provider = LocationProvider.FUSED,
        recordedAt = 1_000L,
        elapsedRealtimeNanos = seq.toLong(),
    )
}
