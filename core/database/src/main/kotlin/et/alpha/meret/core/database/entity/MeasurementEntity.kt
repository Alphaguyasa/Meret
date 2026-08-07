package et.alpha.meret.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.SyncState

/**
 * A saved area or distance measurement.
 *
 * [areaSquareMeters] and [perimeterMeters] are populated for an area measurement,
 * [distanceMeters] for a distance one. The unused columns stay null rather than zero so a
 * query can tell "not applicable" from "measured as nothing".
 */
@Entity(
    tableName = "measurement",
    indices = [
        Index(value = ["project_id"], name = "index_measurement_project"),
        Index(value = ["sync_state"], name = "index_measurement_sync_state"),
    ],
)
public data class MeasurementEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    public val id: String,
    @ColumnInfo(name = "project_id")
    public val projectId: String,
    @ColumnInfo(name = "name")
    public val name: String,
    @ColumnInfo(name = "kind")
    public val kind: MeasurementKind,
    @ColumnInfo(name = "vertices_geojson")
    public val verticesGeoJson: String,
    @ColumnInfo(name = "area_m2")
    public val areaSquareMeters: Double? = null,
    @ColumnInfo(name = "perimeter_m")
    public val perimeterMeters: Double? = null,
    @ColumnInfo(name = "distance_m")
    public val distanceMeters: Double? = null,
    @ColumnInfo(name = "method")
    public val method: MeasurementMethod,
    @ColumnInfo(name = "recorded_at")
    public val recordedAt: Long,
    @ColumnInfo(name = "updated_at")
    public val updatedAt: Long,
    @ColumnInfo(name = "sync_state")
    public val syncState: SyncState = SyncState.PENDING,
)
