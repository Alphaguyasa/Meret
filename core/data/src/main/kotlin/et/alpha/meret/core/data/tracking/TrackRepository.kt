package et.alpha.meret.core.data.tracking

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.mapper.toDomain
import et.alpha.meret.core.datastore.TrackingConfigStore
import et.alpha.meret.core.location.filter.TrackSummariser
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackDetail
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** The recorded tracks, as the track list shows them. */
public interface TrackRepository {
    /** Every track on this device that has not been discarded, newest first. */
    public fun observeTracks(): Flow<List<Track>>

    /**
     * One track with its segments and elevation profile, re-read whenever a batch lands;
     * null when there is no such track or it has been discarded. Computed off the main
     * thread from the stored points, which are never changed.
     */
    public fun observeTrackDetail(trackId: String): Flow<TrackDetail?>

    /**
     * The user's delete. The track leaves the list, but its row and every point are kept,
     * marked discarded, so a mistaken delete never loses a record and sync can tell the
     * server. Purging discarded tracks is a later decision.
     *
     * The track being recorded is refused with [AppError.Conflict]: the recording would go on
     * writing into a track nobody can see. An open track nobody is recording (left by a
     * reboot) can be discarded. No such track is [AppError.Storage.Kind.NotFound].
     */
    public suspend fun discardTrack(trackId: String): AppResult<Unit>
}

public class DefaultTrackRepository
    @Inject
    constructor(
        private val trackDao: TrackDao,
        private val tracking: TrackingController,
        private val configStore: TrackingConfigStore,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : TrackRepository {
        override fun observeTracks(): Flow<List<Track>> =
            trackDao.observeListedTracks().map { tracks -> tracks.map(TrackEntity::toDomain) }

        override fun observeTrackDetail(trackId: String): Flow<TrackDetail?> =
            combine(trackDao.observeTrackWithPoints(trackId), configStore.config) { row, config ->
                row
                    ?.takeIf { it.track.status != TrackStatus.DISCARDED }
                    ?.toDomain()
                    ?.let { (track, points) ->
                        TrackDetail(track, TrackSummariser.summarise(points, config))
                    }
            }.flowOn(dispatchers.default)

        override suspend fun discardTrack(trackId: String): AppResult<Unit> {
            val recording = tracking.state.value as? TrackingState.Recording
            if (recording?.trackId == trackId) {
                return AppResult.Failure(AppError.Conflict(ENTITY_TYPE, trackId))
            }
            return withContext(dispatchers.io) {
                try {
                    val changed = trackDao.discardTrack(trackId, updatedAt = time.nowMillis())
                    if (changed > 0) {
                        AppResult.Success(Unit)
                    } else {
                        AppResult.Failure(AppError.Storage(AppError.Storage.Kind.NotFound))
                    }
                } catch (e: SQLException) {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
                }
            }
        }

        private companion object {
            const val ENTITY_TYPE = "track"
        }
    }
