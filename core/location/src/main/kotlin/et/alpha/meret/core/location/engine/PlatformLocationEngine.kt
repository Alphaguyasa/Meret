package et.alpha.meret.core.location.engine

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * `LocationManager` on `GPS_PROVIDER`, for devices with no usable Play Services.
 *
 * Not a degraded curiosity. Cheap Android devices sold outside Google's distribution — which
 * describes a large share of what a field team in Ethiopia will actually be handed — ship
 * with Play Services absent, stripped, or years out of date. On those devices this is the
 * only engine that works, so it has to be correct rather than merely present.
 *
 * ## What is genuinely worse here
 *
 * No batching. `LocationManager` will not hold fixes and flush them together, so the radio
 * wakes the CPU on every fix and the battery targets in `LOCATION_ENGINE.md` are optimistic
 * on this path. That is reported honestly through [EngineCapabilities.supportsBatching]
 * rather than papered over: "Platform engine, no batching" on the diagnostics screen is what
 * explains an unexpected battery figure to a supervisor.
 *
 * `GPS_PROVIDER` alone, not `NETWORK_PROVIDER`: a network fix is a cell-tower or Wi-Fi
 * estimate, hundreds of metres wide, and a survey track made of those is worse than no track
 * because it looks like data.
 */
public class PlatformLocationEngine(
    private val locationManager: LocationManager,
    private val callbackLooper: Looper,
) : LocationEngine {
    private val broadcaster = FixBroadcaster()
    private val lifecycle = Mutex()
    private var registered = false

    override val fixes: Flow<RawFix> = broadcaster.fixes

    override val capabilities: EngineCapabilities =
        EngineCapabilities(
            kind = EngineKind.PLATFORM,
            supportsBatching = false,
            supportsGnssStatus = true,
            // GPS_PROVIDER fixes usually carry Doppler-derived speed, but not always and not
            // on the first fix. The chain treats a missing speed as "did not say".
            reportsSpeed = true,
        )

    /** Counts fixes lost to a stalled consumer. See [FixBroadcaster]. */
    public val droppedFixCount: StateFlow<Int>
        get() = broadcaster.droppedFixCount

    /** True when the user has GPS switched off entirely. Surfaced on the diagnostics screen. */
    public val isProviderEnabled: Boolean
        get() = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)

    /**
     * All four members are implemented, not just [LocationListener.onLocationChanged].
     *
     * They became default methods in API 30, but `minSdk` is 26, and on API 26–29 the
     * platform's interface declares them abstract. A listener compiled against API 37 that
     * omits them is a class with one method, and the first `onProviderDisabled` an older
     * device delivers — which is exactly what happens when the user switches GPS off
     * mid-track — throws `AbstractMethodError`. So a SAM conversion is not safe here.
     */
    private val listener =
        object : LocationListener {
            override fun onLocationChanged(location: Location) {
                broadcaster.offer(location.toRawFix(provider = LocationProvider.GPS))
            }

            @Deprecated("Deprecated in API 29, but abstract on API 26-29. See class KDoc.")
            override fun onStatusChanged(
                provider: String?,
                status: Int,
                extras: Bundle?,
            ) = Unit

            override fun onProviderEnabled(provider: String) = Unit

            override fun onProviderDisabled(provider: String) = Unit
        }

    /** See [FusedLocationEngine.start] on why the permission check is the caller's. */
    @SuppressLint("MissingPermission")
    override suspend fun start(config: TrackingConfig) {
        lifecycle.withLock {
            if (registered) locationManager.removeUpdates(listener)
            // The Looper overload, not the Executor one: the latter is API 30 and minSdk
            // is 26. See LocationCallbackThread.
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                config.profile.intervalMillis,
                config.profile.minDistanceMetres.toFloat(),
                listener,
                callbackLooper,
            )
            registered = true
        }
    }

    override suspend fun stop() {
        lifecycle.withLock {
            if (!registered) return
            locationManager.removeUpdates(listener)
            registered = false
        }
    }

    /**
     * The provider's cached fix.
     *
     * Wrapped because `getLastKnownLocation` throws [IllegalArgumentException] on a device
     * with no GPS provider at all — an emulator, or a tablet with no GNSS chip. That is a
     * "no opinion" answer, not a crash.
     */
    @SuppressLint("MissingPermission")
    override suspend fun lastKnown(): RawFix? = try {
        locationManager
            .getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?.toRawFix(provider = LocationProvider.GPS)
    } catch (_: IllegalArgumentException) {
        null
    }
}
