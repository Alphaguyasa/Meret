package et.alpha.meret.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import et.alpha.meret.core.database.entity.MeasurementEntity
import et.alpha.meret.core.model.SyncState
import kotlinx.coroutines.flow.Flow

@Dao
public interface MeasurementDao {
    @Query("SELECT * FROM measurement WHERE project_id = :projectId ORDER BY recorded_at DESC")
    public fun observeMeasurements(projectId: String): Flow<List<MeasurementEntity>>

    @Query("SELECT * FROM measurement WHERE id = :measurementId")
    public fun observeMeasurement(measurementId: String): Flow<MeasurementEntity?>

    @Query("SELECT * FROM measurement WHERE id = :measurementId")
    public suspend fun getMeasurement(measurementId: String): MeasurementEntity?

    @Query("SELECT * FROM measurement WHERE sync_state = :syncState ORDER BY recorded_at ASC")
    public suspend fun getMeasurementsBySyncState(syncState: SyncState): List<MeasurementEntity>

    @Upsert
    public suspend fun upsertMeasurement(measurement: MeasurementEntity)

    @Query("UPDATE measurement SET sync_state = :syncState WHERE id = :measurementId")
    public suspend fun updateSyncState(
        measurementId: String,
        syncState: SyncState,
    )

    @Query("DELETE FROM measurement WHERE id = :measurementId")
    public suspend fun deleteMeasurement(measurementId: String)
}
