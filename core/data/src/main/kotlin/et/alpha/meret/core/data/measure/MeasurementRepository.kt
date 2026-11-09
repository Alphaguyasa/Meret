package et.alpha.meret.core.data.measure

import android.database.SQLException
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.dao.MeasurementDao
import et.alpha.meret.core.database.entity.MeasurementEntity
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.domain.measure.MeasurementStore
import et.alpha.meret.domain.measure.NewMeasurement
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Measurements in Room.
 *
 * Like a track or a waypoint, a measurement saved before sign-in exists goes under
 * [RoomNewTrackStore.LOCAL_PROJECT_ID] and is written `pending`; sync moves it to a real
 * project before upload.
 */
public class MeasurementRepository
    @Inject
    internal constructor(
        private val measurementDao: MeasurementDao,
        private val ids: UuidV7Generator,
        private val time: TimeProvider,
        private val dispatchers: DispatcherProvider,
    ) : MeasurementStore {
        override suspend fun save(measurement: NewMeasurement): AppResult<String> =
            withContext(dispatchers.io) {
                try {
                    val now = time.nowMillis()
                    val row =
                        MeasurementEntity(
                            id = ids.generate(),
                            projectId = RoomNewTrackStore.LOCAL_PROJECT_ID,
                            name = measurement.name,
                            kind = MeasurementKind.AREA,
                            verticesGeoJson = measurement.verticesGeoJson,
                            areaSquareMeters = measurement.areaSquareMetres,
                            perimeterMeters = measurement.perimeterMetres,
                            method = measurement.method,
                            recordedAt = now,
                            updatedAt = now,
                            syncState = SyncState.PENDING,
                        )
                    measurementDao.upsertMeasurement(row)
                    AppResult.Success(row.id)
                } catch (e: SQLException) {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
                }
            }
    }
