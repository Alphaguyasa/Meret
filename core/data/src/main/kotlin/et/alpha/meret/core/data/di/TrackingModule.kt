package et.alpha.meret.core.data.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.dispatcher.StandardDispatcherProvider
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.data.tracking.DefaultTrackingController
import et.alpha.meret.core.data.tracking.TrackingController
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
}
