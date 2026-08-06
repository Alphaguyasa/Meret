package et.alpha.meret.core.common.filter

import et.alpha.meret.core.model.geo.LatLng

/**
 * The result of feeding one fix through [KalmanLocationFilter].
 *
 * [wasReset] is true when this fix started a fresh filter run — the first fix, one after
 * an explicit [KalmanLocationFilter.reset], or one arriving after a gap long enough that
 * the previous estimate carried no information. Callers use it to close a track segment.
 */
public data class FilteredFix(
    public val position: LatLng,
    public val varianceSquareMetres: Double,
    public val wasReset: Boolean,
)

/**
 * A scalar Kalman filter over position, run independently on latitude and longitude.
 *
 * Deliberately *not* a constant-velocity model with a velocity state. A field enumerator
 * walks, stops to fill in a form, turns around, and walks back; a velocity state would
 * spend most of its time overshooting those transitions, and the overshoot lands in the
 * measured area of someone's farm plot. A position random walk with process noise tuned
 * for walking pace smooths GPS jitter without inventing motion.
 *
 * Per fix:
 * ```
 * variance += processNoise * dt
 * K         = variance / (variance + accuracy²)
 * estimate += K * (measured − estimate)
 * variance *= (1 − K)
 * ```
 * `K` is dimensionless, so operating on degrees while the variances are in square metres
 * is consistent — the gain is a blend factor, not a length.
 *
 * The filter never sees rejected fixes: the validity gate runs first, and outliers are
 * persisted rather than fed in. Feeding it a 200 m jump would drag the estimate for
 * several seconds afterwards even though the jump itself is discarded.
 *
 * Not thread-safe. One instance per track, owned by the single coroutine that runs the
 * filter chain.
 */
public class KalmanLocationFilter(
    private val processNoiseSquareMetresPerSecond: Double = DEFAULT_PROCESS_NOISE,
    private val gapResetSeconds: Double = DEFAULT_GAP_RESET_SECONDS,
) {
    private var latitude: Double = 0.0
    private var longitude: Double = 0.0
    private var variance: Double = UNINITIALISED
    private var lastTimestampNanos: Long = 0L

    /** Current estimate variance in square metres, or `null` before the first fix. */
    public val varianceSquareMetres: Double?
        get() = variance.takeIf { it >= 0.0 }

    public val isInitialised: Boolean
        get() = variance >= 0.0

    /**
     * Discards the estimate. Call on a segment boundary — a pause/resume, or a new track.
     * The next fix is taken at face value rather than blended with a stale position.
     */
    public fun reset() {
        variance = UNINITIALISED
        lastTimestampNanos = 0L
    }

    /**
     * Feeds one accepted fix through the filter and returns the smoothed position.
     *
     * [timestampNanos] must be `elapsedRealtimeNanos`, not wall clock. A wall clock that
     * jumps backwards mid-track would produce a negative `dt` and a variance that shrinks
     * when it should grow; on field devices that jump is routine. Out-of-order timestamps
     * are treated as `dt = 0` rather than trusted.
     *
     * [accuracyMetres] is the fix's horizontal accuracy. Non-positive values — some
     * devices report 0 — are clamped, since an accuracy of zero claims a perfect
     * measurement and would pin the estimate to it.
     */
    public fun process(
        position: LatLng,
        accuracyMetres: Double,
        timestampNanos: Long,
    ): FilteredFix {
        val accuracy = accuracyMetres.coerceAtLeast(MIN_ACCURACY_METRES)
        val elapsedSeconds = (timestampNanos - lastTimestampNanos).toDouble() / NANOS_PER_SECOND

        val restarting = !isInitialised || elapsedSeconds > gapResetSeconds
        if (restarting) {
            latitude = position.latitude
            longitude = position.longitude
            variance = accuracy * accuracy
            lastTimestampNanos = timestampNanos
            return FilteredFix(
                position = LatLng(latitude, longitude),
                varianceSquareMetres = variance,
                wasReset = true,
            )
        }

        variance += processNoiseSquareMetresPerSecond * elapsedSeconds.coerceAtLeast(0.0)

        val gain = variance / (variance + accuracy * accuracy)
        latitude += gain * (position.latitude - latitude)
        longitude += gain * (position.longitude - longitude)
        variance *= (1.0 - gain)
        lastTimestampNanos = maxOf(lastTimestampNanos, timestampNanos)

        return FilteredFix(
            position = LatLng(latitude, longitude),
            varianceSquareMetres = variance,
            wasReset = false,
        )
    }

    public companion object {
        /**
         * Square metres per second. 3.0 is tuned for walking; a vehicle profile wants
         * more, and a stationary observation point wants less.
         */
        public const val DEFAULT_PROCESS_NOISE: Double = 3.0

        /**
         * A gap longer than this restarts the filter. After a minute without a fix the
         * old estimate says nothing useful about where the device is now, and blending
         * against it draws a straight line through wherever it actually went.
         */
        public const val DEFAULT_GAP_RESET_SECONDS: Double = 60.0

        private const val MIN_ACCURACY_METRES = 1.0
        private const val NANOS_PER_SECOND = 1_000_000_000.0
        private const val UNINITIALISED = -1.0
    }
}
