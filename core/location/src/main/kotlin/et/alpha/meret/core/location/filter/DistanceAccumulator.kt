package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng

/**
 * Stage 3 of the filter chain: turns a sequence of filtered positions into a distance.
 *
 * ## The step gate is the whole point
 *
 * GPS noise is roughly zero-mean in position but *strictly positive* in distance: every
 * jitter step adds. A device on a table with a 5 m-accuracy fix every 3 seconds wanders a
 * metre or two per fix, which is 1,700 fixes and several kilometres over an eight-hour
 * day. `LOCATION_ENGINE.md` is blunt about this — "this single rule is the difference
 * between credible and useless distance figures" — and it is the one thing a client will
 * check by walking a known route.
 *
 * So a step counts only when it clears `max(minStepMetres, accuracy * stepAccuracyFactor)`.
 * Below that, the fix is still recorded and still drawn; it just does not claim to be
 * movement. Sub-threshold steps are **not** carried forward and re-tested against the
 * next fix either: accumulating them would reintroduce exactly the drift the gate exists
 * to remove.
 *
 * The anchor advances only on an accepted step. A slow walk therefore accumulates
 * correctly — successive 1.5 m steps against a fixed anchor eventually clear the gate as
 * one 4 m step — while a stationary device never does, because its anchor-relative
 * distance oscillates around zero instead of growing.
 *
 * Distance is measured on **filtered** coordinates. Measuring raw ones would put the
 * jitter back in.
 *
 * Not thread-safe. One instance per track.
 */
public class DistanceAccumulator(
    private val config: TrackingConfig,
) {
    private var anchor: LatLng? = null

    /** Running total in metres, filtered geometry, step gate applied. */
    public var totalMetres: Double = 0.0
        private set

    /** The position the next step is measured from, or `null` before the first fix. */
    public val anchorPosition: LatLng?
        get() = anchor

    /**
     * Drops the anchor without touching [totalMetres].
     *
     * Call on a segment boundary. A track that resumes 300 m from where it paused must not
     * book those 300 m as walked — nobody observed that travel, and inventing it is how a
     * plot measurement ends up indefensible.
     */
    public fun startNewSegment() {
        anchor = null
    }

    /** Zeroes the total and the anchor. For a fresh track, or for re-running a filter pass. */
    public fun reset() {
        anchor = null
        totalMetres = 0.0
    }

    /**
     * Picks up an existing track's total, with no anchor.
     *
     * For a restarted service. No anchor means the first fix after the restart books no
     * distance, which is right — the distance covered while the process was dead was never
     * observed and must not be guessed at from the two fixes either side of the gap.
     */
    public fun resumeAt(metres: Double) {
        require(metres >= 0.0) { "resumed distance must not be negative, was $metres" }
        anchor = null
        totalMetres = metres
    }

    /**
     * Offers one filtered position. Returns the distance actually added, which is `0.0`
     * whenever the step failed the gate.
     */
    public fun accumulate(
        filteredPosition: LatLng,
        accuracyMetres: Double,
    ): Double {
        val previous = anchor
        if (previous == null) {
            anchor = filteredPosition
            return 0.0
        }

        val stepMetres = GeoMath.haversineDistance(previous, filteredPosition)
        val clearsGate = stepMetres >= config.stepGateMetres(accuracyMetres)

        // The anchor advances only on an accepted step. That is what lets successive
        // sub-threshold steps add up against a fixed reference instead of being discarded.
        if (clearsGate) {
            anchor = filteredPosition
            totalMetres += stepMetres
        }

        return if (clearsGate) stepMetres else 0.0
    }
}
