package et.alpha.meret.core.location.engine

import android.annotation.SuppressLint
import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import et.alpha.meret.core.location.PreviewLocationSource
import et.alpha.meret.core.location.PreviewLocationSource.Companion.PREVIEW_INTERVAL_MILLIS
import et.alpha.meret.core.location.RawFix
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.Executor

/**
 * The map-only request on the fused provider, beside (never instead of) the recording's.
 *
 * Its own [LocationCallback] per collection, so removing it cannot touch the callback
 * [FusedLocationEngine] registered. The fused provider serves both requests from one radio.
 */
public class FusedPreviewLocationSource(
    private val client: FusedLocationProviderClient,
    private val callbackExecutor: Executor,
) : PreviewLocationSource {
    public constructor(
        context: Context,
        callbackExecutor: Executor,
    ) : this(LocationServices.getFusedLocationProviderClient(context), callbackExecutor)

    /** See [FusedLocationEngine.start] on why the permission check is the caller's. */
    @SuppressLint("MissingPermission")
    override fun previewFixes(): Flow<RawFix> = callbackFlow {
        val callback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.locations.forEach { trySend(it.toRawFix()) }
                }
            }
        client.requestLocationUpdates(previewRequest(), callbackExecutor, callback)
        awaitClose { client.removeLocationUpdates(callback) }
    }.conflate()

    private fun previewRequest(): LocationRequest = LocationRequest
        .Builder(Priority.PRIORITY_HIGH_ACCURACY, PREVIEW_INTERVAL_MILLIS)
        .setMinUpdateIntervalMillis(PREVIEW_INTERVAL_MILLIS / 2)
        // No batching: a puck must move as the phone does, not in 5 s jumps.
        .setMaxUpdateDelayMillis(0L)
        .setWaitForAccurateLocation(false)
        .build()
}
