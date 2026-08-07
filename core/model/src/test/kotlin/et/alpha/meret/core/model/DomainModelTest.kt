package et.alpha.meret.core.model

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import org.junit.Test

/**
 * Behaviour on the domain models, plus a value-semantics sweep.
 *
 * The sweep is not ceremony: every one of these types is used as a Compose state key and
 * as a `distinctUntilChanged` discriminator on a Room Flow. A model that grew a member
 * excluded from `equals` — an array, or a hand-written class — would turn into a stream
 * of redundant recompositions rather than a compile error.
 */
class DomainModelTest {
    @Test
    fun `a track is recording while active or paused and not once it has ended`() {
        val track = track(TrackStatus.ACTIVE)

        assertThat(track.isRecording).isTrue()
        assertThat(track.copy(status = TrackStatus.PAUSED).isRecording).isTrue()
        assertThat(track.copy(status = TrackStatus.COMPLETED).isRecording).isFalse()
        assertThat(track.copy(status = TrackStatus.DISCARDED).isRecording).isFalse()
    }

    @Test
    fun `a new track starts empty and pending`() {
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

        assertThat(track.pointCount).isEqualTo(0)
        assertThat(track.distanceMeters).isEqualTo(0.0)
        assertThat(track.movingDurationMillis).isEqualTo(0L)
        assertThat(track.bounds).isNull()
        assertThat(track.endedAt).isNull()
        assertThat(track.syncState).isEqualTo(SyncState.PENDING)
    }

    @Test
    fun `a track point prefers its filtered position when one exists`() {
        val raw = LatLng(9.0, 38.0)
        val filtered = LatLng(9.0001, 38.0001)
        val point = trackPoint(raw)

        assertThat(point.effectivePosition).isEqualTo(raw)
        assertThat(point.copy(filteredPosition = filtered).effectivePosition).isEqualTo(filtered)
    }

    @Test
    fun `a rejected fix keeps its raw position and is flagged rather than dropped`() {
        val raw = LatLng(9.5, 38.5)

        val outlier = trackPoint(raw).copy(isOutlier = true, accuracyMeters = 240.0)

        assertThat(outlier.isOutlier).isTrue()
        assertThat(outlier.position).isEqualTo(raw)
        assertThat(outlier.effectivePosition).isEqualTo(raw)
    }

    @Test
    fun `a track point defaults to the first segment and to trusted, unfiltered data`() {
        val point = trackPoint(LatLng(9.0, 38.0))

        assertThat(point.segmentIndex).isEqualTo(0)
        assertThat(point.isMock).isFalse()
        assertThat(point.isOutlier).isFalse()
        assertThat(point.filteredPosition).isNull()
        assertThat(point.filterVariance).isNull()
    }

    @Test
    fun `models are value types`() {
        assertValueSemantics(project()) { it.copy(name = "changed") }
        assertValueSemantics(track(TrackStatus.ACTIVE)) { it.copy(pointCount = 99) }
        assertValueSemantics(trackPoint(LatLng(9.0, 38.0))) { it.copy(seq = 99) }
        assertValueSemantics(waypoint()) { it.copy(title = "changed") }
        assertValueSemantics(waypointPhoto()) { it.copy(bytes = 99L) }
        assertValueSemantics(measurement()) { it.copy(name = "changed") }
        assertValueSemantics(assignment()) { it.copy(coveragePercent = 99.0) }
        assertValueSemantics(formTemplate()) { it.copy(version = 99) }
        assertValueSemantics(tilePack()) { it.copy(bytes = 99L) }
        assertValueSemantics(outboxEntry()) { it.copy(attempts = 99) }
        assertValueSemantics(syncConflict()) { it.copy(detectedAt = 99L) }
    }

    @Test
    fun `an outbox row is due only once its backoff has elapsed`() {
        val entry = outboxEntry().copy(nextAttemptAt = 5_000L)

        assertThat(entry.isDue(now = 4_999L)).isFalse()
        assertThat(entry.isDue(now = 5_000L)).isTrue()
        assertThat(entry.isDue(now = 5_001L)).isTrue()
    }

    @Test
    fun `a conflict stays unresolved until someone picks a side`() {
        val conflict = syncConflict()

        assertThat(conflict.isResolved).isFalse()
        assertThat(conflict.resolvedAt).isNull()
        assertThat(
            conflict.copy(resolution = ConflictResolution.LOCAL, resolvedAt = 2_000L).isResolved,
        ).isTrue()
    }

    @Test
    fun `a fresh outbox row has made no attempts and carries no error`() {
        val entry = outboxEntry()

        assertThat(entry.attempts).isEqualTo(0)
        assertThat(entry.lastError).isNull()
        assertThat(entry.priority).isEqualTo(OutboxEntry.DEFAULT_PRIORITY)
    }

    @Test
    fun `defaults are the safe ones for anything that has not synced yet`() {
        assertThat(project().isArchived).isFalse()
        assertThat(waypoint().syncState).isEqualTo(SyncState.PENDING)
        assertThat(waypoint().baseVersion).isEqualTo(0)
        assertThat(waypointPhoto().uploadState).isEqualTo(PhotoUploadState.PENDING)
        assertThat(waypointPhoto().uploadAttempts).isEqualTo(0)
        assertThat(measurement().syncState).isEqualTo(SyncState.PENDING)
        assertThat(assignment().status).isEqualTo(AssignmentStatus.OPEN)
        assertThat(assignment().coveragePercent).isEqualTo(0.0)
    }

    private fun <T : Any> assertValueSemantics(
        instance: T,
        mutate: (T) -> T,
    ) {
        val different = mutate(instance)

        assertThat(instance).isEqualTo(instance)
        assertThat(instance.hashCode()).isEqualTo(instance.hashCode())
        assertThat(instance).isNotEqualTo(different)
        assertThat(instance).isNotEqualTo(Any())
        assertThat(instance.toString()).contains(instance::class.simpleName)
    }

    private fun project() = Project(
        id = "p1",
        orgId = "o1",
        name = "Oromia household survey",
        updatedAt = 1_000L,
    )

    private fun track(status: TrackStatus) = Track(
        id = "t1",
        projectId = "p1",
        deviceId = "d1",
        userId = "u1",
        startedAt = 1_000L,
        status = status,
        bounds = BoundingBox(8.9, 38.6, 9.1, 38.9),
        updatedAt = 2_000L,
    )

    private fun trackPoint(position: LatLng) = TrackPoint(
        id = "tp1",
        trackId = "t1",
        seq = 1,
        position = position,
        accuracyMeters = 6.0,
        provider = LocationProvider.FUSED,
        recordedAt = 1_000L,
        elapsedRealtimeNanos = 1_000_000_000L,
    )

    private fun waypoint() = Waypoint(
        id = "w1",
        projectId = "p1",
        position = LatLng(9.0, 38.0),
        accuracyMeters = 5.0,
        recordedAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun waypointPhoto() = WaypointPhoto(
        id = "ph1",
        waypointId = "w1",
        localPath = "/data/photos/ph1.jpg",
        width = 1_600,
        height = 1_200,
        bytes = 240_000L,
        sha256 = "a".repeat(64),
        capturedAt = 1_000L,
    )

    private fun measurement() = Measurement(
        id = "m1",
        projectId = "p1",
        name = "North plot",
        kind = MeasurementKind.AREA,
        verticesGeoJson = """{"type":"Polygon","coordinates":[]}""",
        areaSquareMeters = 2_500.0,
        perimeterMeters = 200.0,
        method = MeasurementMethod.WALKED,
        recordedAt = 1_000L,
        updatedAt = 1_000L,
    )

    private fun assignment() = Assignment(
        id = "a1",
        projectId = "p1",
        assignedUserId = "u1",
        name = "Kebele 03",
        polygonGeoJson = """{"type":"Polygon","coordinates":[]}""",
    )

    private fun formTemplate() = FormTemplate(
        id = "f1",
        projectId = "p1",
        version = 3,
        name = "Household",
        schemaJson = """{"fields":[]}""",
        updatedAt = 1_000L,
    )

    private fun tilePack() = TilePack(
        id = "tp1",
        name = "Ethiopia z0-14",
        regionCode = "ET",
        path = "/data/tiles/ethiopia.mbtiles",
        bytes = 420_000_000L,
        minZoom = 0,
        maxZoom = 14,
        bounds = BoundingBox(3.4, 32.9, 15.0, 48.0),
        source = TilePackSource.SIDELOADED,
        installedAt = 1_000L,
    )

    private fun outboxEntry() = OutboxEntry(
        id = "o1",
        entityType = OutboxEntityType.TRACK_POINTS,
        entityId = "t1",
        operation = OutboxOperation.UPSERT,
        payload = """{"points":[]}""",
        payloadSha256 = "b".repeat(64),
        nextAttemptAt = 1_000L,
        createdAt = 1_000L,
    )

    private fun syncConflict() = SyncConflict(
        id = "c1",
        entityType = OutboxEntityType.WAYPOINT,
        entityId = "w1",
        localPayload = """{"title":"mine"}""",
        remotePayload = """{"title":"theirs"}""",
        detectedAt = 1_000L,
    )
}
