package et.alpha.meret.core.data.waypoint

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.dao.WaypointDao
import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.database.mapper.toDomain
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.Waypoint
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.WaypointMarker
import et.alpha.meret.domain.waypoint.NewWaypoint
import et.alpha.meret.domain.waypoint.WaypointStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Waypoints in Room.
 *
 * Like a track, a waypoint saved before sign-in exists goes under
 * [RoomNewTrackStore.LOCAL_PROJECT_ID]; sync must move it to a real project before upload.
 * The row is written `pending`, which is all Phase 9's sync needs to find it: the outbox
 * payload is built there, from the row, when the wire format exists.
 */
public interface WaypointRepository : WaypointStore {
    /**
     * The waypoints the map draws, newest first, at most [MAX_MARKERS]: enough for months of
     * work, and a bound on what a low-RAM phone holds for the map.
     */
    public fun observeMarkers(): Flow<List<WaypointMarker>>

    /** Every saved waypoint, newest first: the Waypoints tab. */
    public fun observeWaypoints(): Flow<List<Waypoint>>

    public companion object {
        public const val MAX_MARKERS: Int = 5_000
    }
}

public class RoomWaypointRepository
    @Inject
    internal constructor(
        private val waypointDao: WaypointDao,
        private val ids: UuidV7Generator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : WaypointRepository {
        override fun observeMarkers(): Flow<List<WaypointMarker>> = waypointDao
            .observeWaypointMarkers(
                RoomNewTrackStore.LOCAL_PROJECT_ID,
                WaypointRepository.MAX_MARKERS,
            ).map { rows ->
                rows.map {
                    WaypointMarker(
                        it.id,
                        LatLng(it.latitude, it.longitude),
                        it.category,
                    )
                }
            }.flowOn(dispatchers.default)

        override fun observeWaypoints(): Flow<List<Waypoint>> = waypointDao
            .observeWaypoints(RoomNewTrackStore.LOCAL_PROJECT_ID)
            .map { rows -> rows.map { it.toDomain() } }
            .flowOn(dispatchers.default)

        override suspend fun save(waypoint: NewWaypoint): AppResult<String> =
            withContext(dispatchers.io) {
                try {
                    val now = time.nowMillis()
                    val row =
                        WaypointEntity(
                            id = ids.generate(),
                            projectId = RoomNewTrackStore.LOCAL_PROJECT_ID,
                            trackId = waypoint.trackId,
                            latitude = waypoint.position.latitude,
                            longitude = waypoint.position.longitude,
                            accuracyMeters = waypoint.accuracyMetres,
                            altitudeMeters = waypoint.altitudeMetres,
                            recordedAt = now,
                            category = waypoint.category?.key,
                            title = waypoint.title,
                            notes = waypoint.notes,
                            formTemplateId = waypoint.formTemplateId,
                            formVersion = waypoint.formVersion,
                            formData = waypoint.formData,
                            syncState = SyncState.PENDING,
                            updatedAt = now,
                        )
                    waypointDao.upsertWaypoint(row)
                    AppResult.Success(row.id)
                } catch (e: SQLException) {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
                }
            }
    }
