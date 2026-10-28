package et.alpha.meret.core.location.di

import android.content.Context
import android.location.LocationManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.location.LocationCallbackThread
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.PreviewLocationSource
import et.alpha.meret.core.location.engine.LocationEngineSelector
import et.alpha.meret.core.location.gnss.GnssStatusMonitor
import et.alpha.meret.core.location.time.AndroidTimeProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object LocationModule {
    /**
     * The app's [TimeProvider].
     *
     * Bound here rather than in `:core:common` because the correct implementation needs
     * `SystemClock`, and `:core:common` is pure Kotlin with no Android dependency. See
     * [AndroidTimeProvider] for why the JVM one is wrong on a device.
     */
    @Provides
    @Singleton
    public fun provideTimeProvider(): TimeProvider = AndroidTimeProvider()

    @Provides
    @Singleton
    public fun provideLocationManager(
        @ApplicationContext context: Context,
    ): LocationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    /**
     * One callback thread for the whole location subsystem.
     *
     * Never closed. It lives as long as the process, and a `HandlerThread` parked on an empty
     * queue costs a stack — closing it on the last untrack would mean recreating it on the
     * next, and a stale `Looper` handed to `requestLocationUpdates` is a silent no-delivery
     * bug rather than a crash.
     */
    @Provides
    @Singleton
    public fun provideLocationCallbackThread(): LocationCallbackThread = LocationCallbackThread()

    @Provides
    @Singleton
    public fun provideLocationEngineSelector(
        @ApplicationContext context: Context,
        callbackThread: LocationCallbackThread,
    ): LocationEngineSelector = LocationEngineSelector.create(context, callbackThread)

    /**
     * The engine in force.
     *
     * Resolved from the selector rather than chosen here, so "which engine is running" has
     * exactly one answer and the diagnostics screen reads it from the same place the app does.
     */
    @Provides
    @Singleton
    public fun provideLocationEngine(selector: LocationEngineSelector): LocationEngine =
        selector.engine

    /** The map's own live position while idle. Same provider as the engine; see the selector. */
    @Provides
    @Singleton
    public fun providePreviewLocationSource(
        selector: LocationEngineSelector,
    ): PreviewLocationSource = selector.preview

    @Provides
    @Singleton
    public fun provideGnssStatusMonitor(
        locationManager: LocationManager,
        callbackThread: LocationCallbackThread,
    ): GnssStatusMonitor = GnssStatusMonitor(
        locationManager = locationManager,
        callbackHandler = callbackThread.handler,
    )
}
