package et.alpha.meret.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.database.MeretDatabase
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object DatabaseModule {
    @Provides
    @Singleton
    public fun provideMeretDatabase(
        @ApplicationContext context: Context,
    ): MeretDatabase = Room
        .databaseBuilder(context, MeretDatabase::class.java, MeretDatabase.NAME)
        // No allowMainThreadQueries and no destructive fallback, ever. Room's own
        // executors keep every query off the main thread (CLAUDE.md invariant 5).
        .build()

    @Provides
    public fun provideProjectDao(database: MeretDatabase): ProjectDao = database.projectDao()

    @Provides
    public fun provideTrackDao(database: MeretDatabase): TrackDao = database.trackDao()

    @Provides
    public fun provideTrackPointDao(database: MeretDatabase): TrackPointDao =
        database.trackPointDao()

    @Provides
    public fun provideWaypointDao(database: MeretDatabase): WaypointDao = database.waypointDao()

    @Provides
    public fun provideWaypointPhotoDao(database: MeretDatabase): WaypointPhotoDao =
        database.waypointPhotoDao()

    @Provides
    public fun provideMeasurementDao(database: MeretDatabase): MeasurementDao =
        database.measurementDao()

    @Provides
    public fun provideAssignmentDao(database: MeretDatabase): AssignmentDao =
        database.assignmentDao()

    @Provides
    public fun provideFormTemplateDao(database: MeretDatabase): FormTemplateDao =
        database.formTemplateDao()

    @Provides
    public fun provideTilePackDao(database: MeretDatabase): TilePackDao = database.tilePackDao()

    @Provides
    public fun provideOutboxDao(database: MeretDatabase): OutboxDao = database.outboxDao()

    @Provides
    public fun provideSyncConflictDao(database: MeretDatabase): SyncConflictDao =
        database.syncConflictDao()
}
