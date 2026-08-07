package et.alpha.meret.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import et.alpha.meret.core.database.converter.EnumConverters
import et.alpha.meret.core.database.dao.AssignmentDao
import et.alpha.meret.core.database.dao.FormTemplateDao
import et.alpha.meret.core.database.dao.MeasurementDao
import et.alpha.meret.core.database.dao.OutboxDao
import et.alpha.meret.core.database.dao.ProjectDao
import et.alpha.meret.core.database.dao.SyncConflictDao
import et.alpha.meret.core.database.dao.TilePackDao
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.dao.TrackPointDao
import et.alpha.meret.core.database.dao.WaypointDao
import et.alpha.meret.core.database.dao.WaypointPhotoDao
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

/**
 * The device's copy of everything. This is the only source the UI reads from — no screen
 * ever waits on a network call, per CLAUDE.md invariant 1.
 *
 * Every table in `DATA_MODEL.md` lands in version 1, including the ones later phases fill.
 * Adding a table is a migration and a migration test; declaring it now costs a few unused
 * `CREATE TABLE` statements and saves three of those.
 *
 * There is no `fallbackToDestructiveMigration` anywhere. At version 1 there is nothing to
 * fall back from, and once there is, the data on the device is a day of somebody's walking
 * that exists in no other place.
 */
@Database(
    entities = [
        ProjectEntity::class,
        TrackEntity::class,
        TrackPointEntity::class,
        WaypointEntity::class,
        WaypointPhotoEntity::class,
        MeasurementEntity::class,
        AssignmentEntity::class,
        FormTemplateEntity::class,
        TilePackEntity::class,
        OutboxEntity::class,
        SyncConflictEntity::class,
    ],
    version = MeretDatabase.VERSION,
    exportSchema = true,
)
@TypeConverters(EnumConverters::class)
public abstract class MeretDatabase : RoomDatabase() {
    public abstract fun projectDao(): ProjectDao

    public abstract fun trackDao(): TrackDao

    public abstract fun trackPointDao(): TrackPointDao

    public abstract fun waypointDao(): WaypointDao

    public abstract fun waypointPhotoDao(): WaypointPhotoDao

    public abstract fun measurementDao(): MeasurementDao

    public abstract fun assignmentDao(): AssignmentDao

    public abstract fun formTemplateDao(): FormTemplateDao

    public abstract fun tilePackDao(): TilePackDao

    public abstract fun outboxDao(): OutboxDao

    public abstract fun syncConflictDao(): SyncConflictDao

    public companion object {
        public const val VERSION: Int = 1

        public const val NAME: String = "meret.db"
    }
}
