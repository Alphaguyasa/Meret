package et.alpha.meret.core.location.gnss

import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Handler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How good the *sky* is, as five buckets.
 *
 * Deliberately a different thing from `:core:designsystem`'s `GpsQuality`, which buckets
 * reported horizontal accuracy. The two answer the two halves of the question
 * `LOCATION_ENGINE.md` says users need answered — "whether a weak fix is the app's fault or
 * the sky's":
 *
 * - `GpsQuality` says how good the fix is. It is what the map chip shows.
 * - [GnssSignalQuality] says what the receiver had to work with. Four satellites at 18 dB-Hz
 *   under a tree canopy explains a poor fix; four satellites at 40 dB-Hz with a poor fix
 *   means something else is wrong.
 *
 * So this is not a duplicate of those thresholds and must not be collapsed into them.
 */
public enum class GnssSignalQuality {
    /** No satellites used in a fix. Indoors, or the receiver has not acquired yet. */
    NONE,

    /** Too few satellites for a trustworthy 3D fix. */
    POOR,

    /** A bare 3D fix. Usable, with visible wander. */
    FAIR,

    GOOD,

    EXCELLENT,
}

/**
 * A point-in-time reading of the GNSS receiver.
 *
 * [meanCarrierToNoiseDbHz] averages only the satellites actually *used in the fix*.
 * Averaging every visible satellite would fold in the ones at 12 dB-Hz that the receiver
 * already decided to ignore, and the number would then say more about how many satellites
 * happen to be over the horizon than about the quality of the fix.
 */
public data class GnssSnapshot(
    public val satellitesVisible: Int = 0,
    public val satellitesUsedInFix: Int = 0,
    public val meanCarrierToNoiseDbHz: Double? = null,
    public val quality: GnssSignalQuality = GnssSignalQuality.NONE,
    /** True between the receiver starting and stopping. False means the radio is off. */
    public val isReceiving: Boolean = false,
    /** Time to first fix in milliseconds, once there has been one. */
    public val timeToFirstFixMillis: Int? = null,
) {
    public companion object {
        /**
         * Buckets a reading.
         *
         * Satellite count leads, because it is what sets the geometry: a fix needs four
         * satellites for three dimensions plus the clock, so anything below that is not a 3D
         * fix at all however strong the signals are.
         *
         * Carrier-to-noise then downgrades one step when the used satellites are weak.
         * `WEAK_CN0_DB_HZ` is the level below which a signal is being tracked but is close to
         * the receiver's noise floor — plenty of satellites at that strength still produce a
         * wandering fix, which is the canopy case, and reporting it as GOOD on count alone
         * would tell a field worker to trust a track they should not.
         */
        public fun of(
            satellitesVisible: Int,
            satellitesUsedInFix: Int,
            meanCarrierToNoiseDbHz: Double?,
            isReceiving: Boolean,
            timeToFirstFixMillis: Int? = null,
        ): GnssSnapshot {
            val base =
                when {
                    satellitesUsedInFix <= 0 -> GnssSignalQuality.NONE
                    satellitesUsedInFix < MIN_SATELLITES_FOR_3D -> GnssSignalQuality.POOR
                    satellitesUsedInFix < GOOD_SATELLITE_COUNT -> GnssSignalQuality.FAIR
                    satellitesUsedInFix < EXCELLENT_SATELLITE_COUNT -> GnssSignalQuality.GOOD
                    else -> GnssSignalQuality.EXCELLENT
                }

            val weak =
                meanCarrierToNoiseDbHz != null && meanCarrierToNoiseDbHz < WEAK_CN0_DB_HZ

            return GnssSnapshot(
                satellitesVisible = satellitesVisible,
                satellitesUsedInFix = satellitesUsedInFix,
                meanCarrierToNoiseDbHz = meanCarrierToNoiseDbHz,
                quality = if (weak) base.downgraded() else base,
                isReceiving = isReceiving,
                timeToFirstFixMillis = timeToFirstFixMillis,
            )
        }

        private fun GnssSignalQuality.downgraded(): GnssSignalQuality =
            GnssSignalQuality.entries[(ordinal - 1).coerceAtLeast(0)]

        /** Three satellites give a 2D fix at best; the fourth solves the receiver clock. */
        public const val MIN_SATELLITES_FOR_3D: Int = 4
        public const val GOOD_SATELLITE_COUNT: Int = 7
        public const val EXCELLENT_SATELLITE_COUNT: Int = 10

        /** dB-Hz. Below this a satellite is tracked but near the noise floor. */
        public const val WEAK_CN0_DB_HZ: Double = 25.0
    }
}

/**
 * Watches the GNSS receiver and publishes [GnssSnapshot]s.
 *
 * Independent of whether a track is recording. The map's GPS chip and the diagnostics screen
 * both want this while the user is merely looking at the map, and the answer to "why is my
 * accuracy bad" is most useful *before* someone starts recording rather than after.
 *
 * Uses `registerGnssStatusCallback(Callback, Handler)`, the API 24 overload; the `Executor`
 * form is API 30 and `minSdk` is 26. See `LocationCallbackThread`.
 */
public class GnssStatusMonitor(
    private val locationManager: LocationManager,
    private val callbackHandler: Handler,
) {
    private val mutableStatus = MutableStateFlow(GnssSnapshot())
    private var registered = false
    private var timeToFirstFixMillis: Int? = null

    public val status: StateFlow<GnssSnapshot> = mutableStatus.asStateFlow()

    private val callback =
        object : GnssStatus.Callback() {
            override fun onStarted() {
                mutableStatus.value = mutableStatus.value.copy(isReceiving = true)
            }

            override fun onStopped() {
                // Zero the counts, not just the flag. Leaving "11 satellites" on screen next
                // to a stopped receiver is the kind of stale reading that gets screenshotted
                // into a bug report and sends someone looking in the wrong place.
                timeToFirstFixMillis = null
                mutableStatus.value = GnssSnapshot(isReceiving = false)
            }

            override fun onFirstFix(ttffMillis: Int) {
                timeToFirstFixMillis = ttffMillis
                mutableStatus.value =
                    mutableStatus.value.copy(timeToFirstFixMillis = ttffMillis)
            }

            override fun onSatelliteStatusChanged(status: GnssStatus) {
                mutableStatus.value = status.toSnapshot(timeToFirstFixMillis)
            }
        }

    /**
     * Begins monitoring. Returns false when the platform refuses — no GNSS hardware, or the
     * receiver is unavailable — which the diagnostics screen reports rather than retrying.
     *
     * Requires `ACCESS_FINE_LOCATION`; the caller owns the permission gate, as with the
     * engines.
     */
    @SuppressLint("MissingPermission")
    public fun start(): Boolean {
        if (registered) return true
        registered = locationManager.registerGnssStatusCallback(callback, callbackHandler)
        return registered
    }

    public fun stop() {
        if (!registered) return
        locationManager.unregisterGnssStatusCallback(callback)
        registered = false
        timeToFirstFixMillis = null
        mutableStatus.value = GnssSnapshot(isReceiving = false)
    }
}

/**
 * Reduces a [GnssStatus] to a [GnssSnapshot].
 *
 * `getCn0DbHz` is documented as always available, but firmware on the low-end devices this
 * app targets is not always truthful about that, and a satellite reporting 0.0 dB-Hz while
 * being used in the fix is a firmware quirk rather than a satellite at the noise floor.
 * Those are excluded from the mean instead of dragging it to zero and downgrading a perfectly
 * good fix.
 */
internal fun GnssStatus.toSnapshot(timeToFirstFixMillis: Int?): GnssSnapshot {
    var usedCount = 0
    var cn0Total = 0.0
    var cn0Count = 0

    for (index in 0 until satelliteCount) {
        if (!usedInFix(index)) continue
        usedCount++

        val cn0 = getCn0DbHz(index)
        if (cn0 > 0f) {
            cn0Total += cn0
            cn0Count++
        }
    }

    return GnssSnapshot.of(
        satellitesVisible = satelliteCount,
        satellitesUsedInFix = usedCount,
        meanCarrierToNoiseDbHz = if (cn0Count > 0) cn0Total / cn0Count else null,
        isReceiving = true,
        timeToFirstFixMillis = timeToFirstFixMillis,
    )
}
