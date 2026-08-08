package et.alpha.meret.core.location.engine

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.Executor

/**
 * `FusedLocationProviderClient`, the good path.
 *
 * Preferred whenever Play Services is present and current, because it is the only engine
 * with real batching — and per `LOCATION_ENGINE.md`, batched updates via
 * `setMaxUpdateDelayMillis` are "the single biggest win" on battery, ahead of everything
 * else the app can do.
 *
 * Signatures here were checked against `play-services-location` 21.4.0 rather than recalled:
 * `LocationRequest.Builder(int priority, long intervalMillis)`, and the
 * `requestLocationUpdates(LocationRequest, Executor, LocationCallback)` overload so the
 * callback never lands on the main thread.
 */
public class FusedLocationEngine(
    private val client: FusedLocationProviderClient,
    private val callbackExecutor: Executor,
) : LocationEngine {
    public constructor(
        context: Context,
        callbackExecutor: Executor,
    ) : this(LocationServices.getFusedLocationProviderClient(context), callbackExecutor)

    private val broadcaster = FixBroadcaster()

    /** Serialises start/stop so a profile switch cannot race a stop into a leaked callback. */
    private val lifecycle = Mutex()
    private var registered = false

    override val fixes: Flow<RawFix> = broadcaster.fixes

    override val capabilities: EngineCapabilities =
        EngineCapabilities(
            kind = EngineKind.FUSED,
            supportsBatching = true,
            supportsGnssStatus = true,
            reportsSpeed = true,
        )

    /** Counts fixes lost to a stalled consumer. See [FixBroadcaster]. */
    public val droppedFixCount: StateFlow<Int>
        get() = broadcaster.droppedFixCount

    private val callback =
        object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                // A batched flush arrives as several locations at once, oldest first.
                // getLocations(), not getLastLocation(): taking only the newest would throw
                // away most of the track on any profile with a batch window.
                result.locations.forEach { broadcaster.offer(it.toRawFix()) }
            }
        }

    /**
     * The caller owns the permission gate, so this does not re-check it — hence the
     * suppression. A revoked permission surfaces as the [SecurityException] the interface
     * documents, which the tracking service turns into a visible prompt rather than an empty
     * track.
     */
    @SuppressLint("MissingPermission")
    override suspend fun start(config: TrackingConfig) {
        lifecycle.withLock {
            if (registered) client.removeLocationUpdates(callback)
            client.requestLocationUpdates(config.toLocationRequest(), callbackExecutor, callback)
            registered = true
        }
    }

    override suspend fun stop() {
        lifecycle.withLock {
            if (!registered) return
            client.removeLocationUpdates(callback)
            registered = false
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): RawFix? = client.getLastLocation().awaitOrNull()?.toRawFix()

    private companion object {
        /**
         * Do not stall the first fix waiting for it to get better. A field worker pressing
         * Record wants the map to move now; the filter chain will discard the first fix if it
         * turns out to be poor, and `setWaitForAccurateLocation(true)` can hold the very
         * first update back by tens of seconds.
         */
        const val WAIT_FOR_ACCURATE_LOCATION = false

        fun TrackingConfig.toLocationRequest(): LocationRequest = LocationRequest
            .Builder(Priority.PRIORITY_HIGH_ACCURACY, profile.intervalMillis)
            // Accept a fix arriving early — half the interval — rather than discarding
            // it. Fixes are cheap once the radio is already awake.
            .setMinUpdateIntervalMillis(profile.intervalMillis / 2)
            .setMinUpdateDistanceMeters(profile.minDistanceMetres.toFloat())
            .setMaxUpdateDelayMillis(profile.batchWindowMillis)
            .setWaitForAccurateLocation(WAIT_FOR_ACCURATE_LOCATION)
            .build()
    }
}

/**
 * Converts a platform [Location] into a [RawFix].
 *
 * Every optional field is gated on its `has*` accessor. Reading `speed` on a fix that has
 * none returns `0.0f`, not null — and a `0.0` speed is a *claim* about the world that
 * [et.alpha.meret.core.location.filter.AutoPauseDetector] would act on, so the difference
 * between "not moving" and "did not say" has to survive the conversion.
 *
 * `isMock` uses [Location.isFromMockProvider] rather than API 31's `isMock` because `minSdk`
 * is 26. The two report the same thing; only the name changed, so the deprecation is
 * suppressed rather than worked around with a version check that would branch to identical
 * behaviour.
 */
@Suppress("DEPRECATION")
internal fun Location.toRawFix(
    provider: LocationProvider = LocationProvider.FUSED,
    satellitesUsed: Int? = null,
): RawFix = RawFix(
    position = LatLng(latitude, longitude),
    // A fix with no accuracy at all is reported as a non-positive value, which the
    // validity gate rejects. That is the intended outcome: unknown accuracy is unusable,
    // not perfect.
    accuracyMetres = if (hasAccuracy()) accuracy.toDouble() else 0.0,
    elapsedRealtimeNanos = elapsedRealtimeNanos,
    recordedAtMillis = time,
    provider = provider,
    altitudeMetres = if (hasAltitude()) altitude else null,
    verticalAccuracyMetres =
        if (hasVerticalAccuracy()) verticalAccuracyMeters.toDouble() else null,
    bearingDegrees = if (hasBearing()) bearing.toDouble() else null,
    speedMetresPerSecond = if (hasSpeed()) speed.toDouble() else null,
    speedAccuracyMetresPerSecond =
        if (hasSpeedAccuracy()) speedAccuracyMetersPerSecond.toDouble() else null,
    satellitesUsed = satellitesUsed,
    isMock = isFromMockProvider,
)
