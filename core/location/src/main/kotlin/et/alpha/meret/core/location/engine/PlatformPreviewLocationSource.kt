package et.alpha.meret.core.location.engine

import android.annotation.SuppressLint
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import et.alpha.meret.core.location.PreviewLocationSource
import et.alpha.meret.core.location.PreviewLocationSource.Companion.PREVIEW_INTERVAL_MILLIS
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.LocationProvider
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate

/**
 * The map-only request on `GPS_PROVIDER`, for phones without usable Play Services.
 *
 * Its own [LocationListener] per collection, so `removeUpdates` here cannot remove the
 * listener [PlatformLocationEngine] registered for the recording.
 */
public class PlatformPreviewLocationSource(
    private val locationManager: LocationManager,
    private val callbackLooper: Looper,
) : PreviewLocationSource {
    /** See [FusedLocationEngine.start] on why the permission check is the caller's. */
    @SuppressLint("MissingPermission")
    override fun previewFixes(): Flow<RawFix> = callbackFlow {
        val listener = PreviewListener { trySend(it.toRawFix(provider = LocationProvider.GPS)) }
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                PREVIEW_INTERVAL_MILLIS,
                0f,
                listener,
                callbackLooper,
            )
        } catch (_: IllegalArgumentException) {
            // No GPS provider on this device: no live puck, as PlatformLocationEngine.lastKnown.
            close()
        }
        awaitClose { locationManager.removeUpdates(listener) }
    }.conflate()

    /**
     * All four members, not a SAM conversion: they are abstract on API 26–29. See
     * [PlatformLocationEngine]'s listener.
     */
    private class PreviewListener(
        private val onFix: (Location) -> Unit,
    ) : LocationListener {
        override fun onLocationChanged(location: Location) = onFix(location)

        @Deprecated("Deprecated in API 29, but abstract on API 26-29. See PlatformLocationEngine.")
        override fun onStatusChanged(
            provider: String?,
            status: Int,
            extras: Bundle?,
        ) = Unit

        override fun onProviderEnabled(provider: String) = Unit

        override fun onProviderDisabled(provider: String) = Unit
    }
}
