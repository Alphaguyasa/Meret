package et.alpha.meret.core.location.engine

import android.content.Context
import android.location.LocationManager
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationCallbackThread
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.PreviewLocationSource

/**
 * Reports whether Play Services is usable. Extracted so the selector is testable without a
 * device — [GoogleApiAvailability] is a singleton that reads real system state.
 */
public fun interface PlayServicesAvailability {
    public fun isUsable(): Boolean
}

/**
 * Chooses between [FusedLocationEngine] and [PlatformLocationEngine].
 *
 * ## Fall back silently, but say so afterwards
 *
 * `LOCATION_ENGINE.md` asks for exactly this pair of behaviours, and the pair matters. A
 * field worker cannot act on "Play Services is out of date" — they have no network to update
 * it over and no permission to try — so a dialog would be an obstacle rather than
 * information. A supervisor looking at a track that is sparser than expected absolutely can
 * act on it, which is why [selected] records the choice for the diagnostics screen.
 *
 * ## What counts as usable
 *
 * Only [ConnectionResult.SUCCESS]. Every other result — `SERVICE_MISSING`,
 * `SERVICE_VERSION_UPDATE_REQUIRED`, `SERVICE_DISABLED`, `SERVICE_INVALID` — means the fused
 * provider either is not there or cannot be relied on, and a half-working fused provider is
 * worse than the platform one: it returns fixes for a while and then stops, which looks like
 * an OEM battery killer and gets debugged as one.
 */
public class LocationEngineSelector(
    private val availability: PlayServicesAvailability,
    private val fusedEngine: () -> LocationEngine,
    private val platformEngine: () -> LocationEngine,
    private val fusedPreview: () -> PreviewLocationSource,
    private val platformPreview: () -> PreviewLocationSource,
) {
    /**
     * The engine to use. Resolved once and cached: switching engines mid-track would change
     * the meaning of the data halfway through, and the two report different `provider` values
     * on every point, so a track could not say which one produced it.
     */
    public val engine: LocationEngine by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (availability.isUsable()) fusedEngine() else platformEngine()
    }

    /**
     * The map's live position while no track records, from the same provider as [engine]:
     * a puck fed by fused fixes over a track drawn from GPS fixes would sit visibly off it.
     */
    public val preview: PreviewLocationSource by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        if (selected == EngineKind.FUSED) fusedPreview() else platformPreview()
    }

    /** Which engine was chosen. For the diagnostics screen. */
    public val selected: EngineKind
        get() = engine.capabilities.kind

    public companion object {
        /**
         * The production wiring.
         *
         * `isGooglePlayServicesAvailable` is a local check against the installed package — no
         * network, no blocking — which is why calling it on the selector's first use is safe
         * even though the app must work with the network permanently off.
         */
        public fun create(
            context: Context,
            callbackThread: LocationCallbackThread,
        ): LocationEngineSelector = LocationEngineSelector(
            availability = {
                GoogleApiAvailability
                    .getInstance()
                    .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
            },
            fusedEngine = { FusedLocationEngine(context, callbackThread.executor) },
            platformEngine = {
                PlatformLocationEngine(
                    locationManager = context.locationManager(),
                    callbackLooper = callbackThread.looper,
                )
            },
            fusedPreview = { FusedPreviewLocationSource(context, callbackThread.executor) },
            platformPreview = {
                PlatformPreviewLocationSource(
                    locationManager = context.locationManager(),
                    callbackLooper = callbackThread.looper,
                )
            },
        )

        private fun Context.locationManager(): LocationManager =
            getSystemService(Context.LOCATION_SERVICE) as LocationManager
    }
}
