package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.dispatcher.StandardDispatcherProvider
import et.alpha.meret.core.common.telemetry.NoOpTelemetry
import et.alpha.meret.core.common.telemetry.Telemetry
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.data.tracking.DefaultEarlierTracksRepository
import et.alpha.meret.core.data.tracking.DefaultTrackRepository
import et.alpha.meret.core.data.tracking.DefaultTrackingController
import et.alpha.meret.core.data.tracking.EarlierTracksRepository
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.data.tracking.TrackRepository
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.domain.tracking.NewTrackStore
import et.alpha.meret.domain.tracking.RecordingCommands
import et.alpha.meret.domain.tracking.StartTrackingUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public abstract class TrackingModule {
    /**
     * One controller per process. It holds the running recording; a second instance would
     * open a second recording on the same engine.
     */
    @Binds
    @Singleton
    public abstract fun bindTrackingController(
        controller: DefaultTrackingController,
    ): TrackingController

    @Binds
    public abstract fun bindNewTrackStore(store: RoomNewTrackStore): NewTrackStore

    @Binds
    public abstract fun bindTrackRepository(repository: DefaultTrackRepository): TrackRepository

    /** Singleton: it caches built lines across visits to the map. */
    @Binds
    @Singleton
    public abstract fun bindEarlierTracksRepository(
        repository: DefaultEarlierTracksRepository,
    ): EarlierTracksRepository

    public companion object {
        /** `:domain` has no `@Inject`. [RecordingCommands] is bound by `:feature:tracking`. */
        @Provides
        public fun provideStartTrackingUseCase(
            tracks: NewTrackStore,
            commands: RecordingCommands,
        ): StartTrackingUseCase = StartTrackingUseCase(tracks, commands)
    }
}

/**
 * Bindings for the pure-Kotlin types in `:core:common`, which has no Hilt of its own.
 * `TimeProvider` is bound in `:core:location`, which owns the `SystemClock` implementation.
 */
@Module
@InstallIn(SingletonComponent::class)
public object CommonModule {
    @Provides
    @Singleton
    public fun provideDispatcherProvider(): DispatcherProvider = StandardDispatcherProvider()

    /** Singleton: the generator's monotonic counter only orders ids it generated itself. */
    @Provides
    @Singleton
    public fun provideUuidV7Generator(timeProvider: TimeProvider): UuidV7Generator =
        UuidV7Generator(timeProvider)

    /**
     * Nothing leaves the phone. A deployment that wants crash or gap reports replaces this
     * binding with its own reporter, which must keep to the no-personal-data rules on
     * [Telemetry].
     */
    @Provides
    public fun provideTelemetry(): Telemetry = NoOpTelemetry
}
