package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.TrackingConfig

/**
 * Why the gate turned a fix away.
 *
 * Carried on `ProcessedFix` for the diagnostics screen and for telemetry, but **not**
 * persisted: `track_point` has `is_outlier` and no reason column. Storing the reason would
 * be worth a column — "rejected for accuracy" and "rejected for implied speed" point at a
 * bad sky and a bad device respectively — but that is a schema change and a migration, so
 * it belongs to a phase that owns the schema rather than to this one.
 */
public enum class RejectionReason {
    /** Reported horizontal accuracy worse than [TrackingConfig.maxAccuracyMetres]. */
    ACCURACY,

    /**
     * `elapsedRealtimeNanos` did not advance. A repeat of the same monotonic instant is a
     * duplicate delivery, not a new observation — both engines can produce them when a
     * batch is flushed twice.
     */
    NON_MONOTONIC_TIME,

    /** Implied speed from the last accepted fix exceeds the plausible maximum. */
    IMPLIED_SPEED,

    /** Flagged as mock while the project forbids mock locations. */
    MOCK_NOT_ALLOWED,
}

/**
 * The gate's answer for one fix.
 *
 * A rejected fix is still written to `track_point`, with `is_outlier = 1`. Rejections are
 * evidence: a track that is 40% outliers says something true about either the device or the
 * sky, and discarding them would leave a clean-looking track with an unexplained hole in
 * it.
 */
public sealed interface FixVerdict {
    public data object Accepted : FixVerdict

    public data class Rejected(
        public val reason: RejectionReason,
    ) : FixVerdict
}

/**
 * Stage 1 of the filter chain: decides whether a fix is believable.
 *
 * Stateful — implied speed and time monotonicity are both relative to the last **accepted**
 * fix, not the last fix seen. Chaining from rejected fixes would let one wild outlier
 * become the reference point that rejects the next twenty good fixes behind it.
 *
 * ## What this stage deliberately does not reject
 *
 * `LOCATION_ENGINE.md` lists "exact duplicate coordinates with a different timestamp and
 * zero speed" among the things to handle, and the handling is *not* rejection: such a fix
 * is a device sitting still, which is a true observation and belongs in the track. It is
 * [DistanceAccumulator]'s step gate that stops it from accruing distance. Rejecting it here
 * would mark a stationary enumerator's entire lunch break as outliers.
 *
 * Not thread-safe. One instance per track, owned by the pipeline's coroutine.
 */
public class ValidityGate(
    private val config: TrackingConfig,
) {
    private var lastAccepted: RawFix? = null

    /** The last fix this gate accepted, or `null` before the first one. */
    public val lastAcceptedFix: RawFix?
        get() = lastAccepted

    /** Forgets the reference fix. Call on a segment boundary, as for the Kalman filter. */
    public fun reset() {
        lastAccepted = null
    }

    public fun evaluate(fix: RawFix): FixVerdict {
        val rejection = firstFailure(fix)
        if (rejection != null) return FixVerdict.Rejected(rejection)

        lastAccepted = fix
        return FixVerdict.Accepted
    }

    /**
     * The checks, cheapest first. `when` short-circuits, so a fix rejected on accuracy never
     * pays for a haversine.
     *
     * The two `null` branches mean different things and both are correct: the first is "no
     * reference fix yet, so nothing relative can be checked", the second is "every check
     * passed".
     */
    private fun firstFailure(fix: RawFix): RejectionReason? {
        val previous = lastAccepted

        return when {
            fix.isMock && !config.allowMockLocations -> {
                RejectionReason.MOCK_NOT_ALLOWED
            }

            // A non-positive accuracy means the device declined to say. Treat "unknown" as
            // unusable rather than as perfect: some Transsion firmwares report 0.0 on a fix
            // that is hundreds of metres out, and taking that at face value pins the Kalman
            // estimate to it.
            fix.accuracyMetres <= 0.0 || fix.accuracyMetres > config.maxAccuracyMetres -> {
                RejectionReason.ACCURACY
            }

            previous == null -> {
                null
            }

            fix.elapsedRealtimeNanos <= previous.elapsedRealtimeNanos -> {
                RejectionReason.NON_MONOTONIC_TIME
            }

            impliedSpeed(previous, fix) > config.maxImpliedSpeedMetresPerSecond -> {
                RejectionReason.IMPLIED_SPEED
            }

            else -> {
                null
            }
        }
    }

    /**
     * Metres per second implied by travelling between two fixes.
     *
     * Only called once the timestamps are known to be strictly increasing, so the divisor is
     * always positive.
     */
    private fun impliedSpeed(
        previous: RawFix,
        fix: RawFix,
    ): Double {
        val elapsedSeconds =
            (fix.elapsedRealtimeNanos - previous.elapsedRealtimeNanos).toDouble() /
                NANOS_PER_SECOND
        return GeoMath.haversineDistance(previous.position, fix.position) / elapsedSeconds
    }

    private companion object {
        const val NANOS_PER_SECOND = 1_000_000_000.0
    }
}
