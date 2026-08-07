package et.alpha.meret.core.database

import et.alpha.meret.core.database.entity.AssignmentEntity
import et.alpha.meret.core.database.entity.FormTemplateEntity
import et.alpha.meret.core.database.entity.MeasurementEntity
import et.alpha.meret.core.database.entity.OutboxEntity
import et.alpha.meret.core.database.entity.ProjectEntity
import et.alpha.meret.core.database.entity.SyncConflictEntity
import et.alpha.meret.core.database.entity.TilePackEntity
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackPointEntity
import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.database.entity.WaypointPhotoEntity
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.OutboxEntityType
import et.alpha.meret.core.model.OutboxOperation
import et.alpha.meret.core.model.TilePackSource
import et.alpha.meret.core.model.TrackStatus

/**
 * Fixtures anchored on Addis Ababa, so a coordinate that looks wrong in a failure message
 * looks wrong in the place this app is actually used.
 */
internal object Fixtures {
    const val PROJECT_ID = "project-1"
    const val TRACK_ID = "track-1"
    const val WAYPOINT_ID = "waypoint-1"

    const val BASE_LATITUDE = 9.0300
    const val BASE_LONGITUDE = 38.7400

    fun project(
        id: String = PROJECT_ID,
        name: String = "Oromia household survey",
        updatedAt: Long = 1_000L,
        isArchived: Boolean = false,
    ) = ProjectEntity(
        id = id,
        orgId = "org-1",
        name = name,
        updatedAt = updatedAt,
        isArchived = isArchived,
    )

    fun track(
        id: String = TRACK_ID,
        projectId: String = PROJECT_ID,
        status: TrackStatus = TrackStatus.ACTIVE,
        startedAt: Long = 1_000L,
    ) = TrackEntity(
        id = id,
        projectId = projectId,
        deviceId = "device-1",
        userId = "user-1",
        startedAt = startedAt,
        status = status,
        updatedAt = startedAt,
    )

    /**
     * A fix roughly [seq] × 10 m north-east of the anchor, so a run of them forms a line
     * whose length is predictable without pulling in the geometry module.
     */
    fun point(
        seq: Int,
        trackId: String = TRACK_ID,
        id: String = "point-$trackId-$seq",
        segmentIndex: Int = 0,
        latitude: Double = BASE_LATITUDE + seq * 0.0001,
        longitude: Double = BASE_LONGITUDE + seq * 0.0001,
        accuracyMeters: Double = 6.0,
        isOutlier: Boolean = false,
        filteredLatitude: Double? = null,
        filteredLongitude: Double? = null,
    ) = TrackPointEntity(
        id = id,
        trackId = trackId,
        segmentIndex = segmentIndex,
        seq = seq,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = accuracyMeters,
        provider = LocationProvider.FUSED,
        recordedAt = 1_000L + seq * 1_000L,
        elapsedRealtimeNanos = seq.toLong() * 1_000_000_000L,
        isOutlier = isOutlier,
        filteredLatitude = filteredLatitude,
        filteredLongitude = filteredLongitude,
    )

    fun points(
        range: IntRange,
        trackId: String = TRACK_ID,
    ): List<TrackPointEntity> = range.map { point(seq = it, trackId = trackId) }

    fun waypoint(
        id: String = WAYPOINT_ID,
        projectId: String = PROJECT_ID,
        trackId: String? = null,
        latitude: Double = BASE_LATITUDE,
        longitude: Double = BASE_LONGITUDE,
        recordedAt: Long = 1_000L,
    ) = WaypointEntity(
        id = id,
        projectId = projectId,
        trackId = trackId,
        latitude = latitude,
        longitude = longitude,
        accuracyMeters = 5.0,
        recordedAt = recordedAt,
        updatedAt = recordedAt,
    )

    fun photo(
        id: String = "photo-1",
        waypointId: String = WAYPOINT_ID,
        sha256: String = "a".repeat(64),
    ) = WaypointPhotoEntity(
        id = id,
        waypointId = waypointId,
        localPath = "/data/photos/$id.jpg",
        width = 1_600,
        height = 1_200,
        bytes = 240_000L,
        sha256 = sha256,
        capturedAt = 1_000L,
    )

    fun measurement(
        id: String = "measurement-1",
        projectId: String = PROJECT_ID,
    ) = MeasurementEntity(
        id = id,
        projectId = projectId,
        name = "North plot",
        kind = MeasurementKind.AREA,
        verticesGeoJson = """{"type":"Polygon","coordinates":[]}""",
        areaSquareMeters = 2_500.0,
        perimeterMeters = 200.0,
        method = MeasurementMethod.WALKED,
        recordedAt = 1_000L,
        updatedAt = 1_000L,
    )

    fun assignment(
        id: String = "assignment-1",
        projectId: String = PROJECT_ID,
    ) = AssignmentEntity(
        id = id,
        projectId = projectId,
        assignedUserId = "user-1",
        name = "Kebele 03",
        polygonGeoJson = """{"type":"Polygon","coordinates":[]}""",
    )

    fun formTemplate(
        id: String = "template-1",
        projectId: String = PROJECT_ID,
        version: Int = 1,
        updatedAt: Long = 1_000L,
    ) = FormTemplateEntity(
        id = id,
        projectId = projectId,
        version = version,
        name = "Household",
        schemaJson = """{"fields":[]}""",
        updatedAt = updatedAt,
    )

    fun tilePack(id: String = "pack-1") = TilePackEntity(
        id = id,
        name = "Ethiopia z0-14",
        regionCode = "ET",
        path = "/data/tiles/ethiopia.mbtiles",
        bytes = 420_000_000L,
        minZoom = 0,
        maxZoom = 14,
        bboxMinLatitude = 3.4,
        bboxMinLongitude = 32.9,
        bboxMaxLatitude = 15.0,
        bboxMaxLongitude = 48.0,
        source = TilePackSource.SIDELOADED,
        installedAt = 1_000L,
    )

    fun outboxEntry(
        id: String = "outbox-1",
        entityId: String = TRACK_ID,
        entityType: OutboxEntityType = OutboxEntityType.TRACK,
        priority: Int = 10,
        nextAttemptAt: Long = 0L,
        createdAt: Long = 1_000L,
    ) = OutboxEntity(
        id = id,
        entityType = entityType,
        entityId = entityId,
        operation = OutboxOperation.UPSERT,
        payload = """{"id":"$entityId"}""",
        payloadSha256 = "b".repeat(64),
        priority = priority,
        nextAttemptAt = nextAttemptAt,
        createdAt = createdAt,
    )

    fun conflict(
        id: String = "conflict-1",
        entityId: String = WAYPOINT_ID,
    ) = SyncConflictEntity(
        id = id,
        entityType = OutboxEntityType.WAYPOINT,
        entityId = entityId,
        localPayload = """{"title":"mine"}""",
        remotePayload = """{"title":"theirs"}""",
        detectedAt = 1_000L,
    )
}
