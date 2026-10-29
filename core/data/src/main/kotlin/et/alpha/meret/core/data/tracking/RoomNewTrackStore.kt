package et.alpha.meret.core.data.tracking

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.datastore.DeviceIdentityStore
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.domain.tracking.NewTrackStore
import kotlinx.coroutines.withContext
import java.io.IOException
import javax.inject.Inject

/**
 * New track rows in Room.
 *
 * There is no sign-in and no project list yet (BUILD_PLAN phase 9), so a track records under
 * [LOCAL_PROJECT_ID] and [LOCAL_USER_ID]. `track.project_id` has no foreign key for exactly
 * this case (see [TrackEntity]). Sync must give such rows the signed-in user and a real
 * project before it uploads them.
 */
public class RoomNewTrackStore
    @Inject
    internal constructor(
        private val trackDao: TrackDao,
        private val identity: DeviceIdentityStore,
        private val ids: UuidV7Generator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : NewTrackStore {
        override suspend fun create(): AppResult<String> = withContext(dispatchers.io) {
            try {
                val deviceId = identity.deviceId(ids::generate)
                val now = time.nowMillis()
                val track =
                    TrackEntity(
                        id = ids.generate(),
                        projectId = LOCAL_PROJECT_ID,
                        deviceId = deviceId,
                        userId = LOCAL_USER_ID,
                        startedAt = now,
                        status = TrackStatus.ACTIVE,
                        updatedAt = now,
                    )
                trackDao.upsertTrack(track)
                AppResult.Success(track.id)
            } catch (e: SQLException) {
                AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
            } catch (e: IOException) {
                // The preferences file holding the device id.
                AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
            }
        }

        override suspend fun discardIfEmpty(trackId: String) {
            withContext(dispatchers.io) {
                val track = trackDao.getTrack(trackId) ?: return@withContext
                if (track.pointCount == 0) trackDao.deleteTrack(trackId)
            }
        }

        public companion object {
            /** The project of every track recorded before sign-in exists. */
            public const val LOCAL_PROJECT_ID: String = "local"

            /** The user of every track recorded before sign-in exists. */
            public const val LOCAL_USER_ID: String = "local"
        }
    }
