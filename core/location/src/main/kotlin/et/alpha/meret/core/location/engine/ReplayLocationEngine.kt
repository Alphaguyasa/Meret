package et.alpha.meret.core.location.engine

import et.alpha.meret.core.location.EngineCapabilities
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.location.gpx.GpxReader
import et.alpha.meret.core.location.gpx.GpxTrace
import et.alpha.meret.core.location.gpx.GpxTrackPoint
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import java.io.InputStream

/** How fast a fixture plays in wall-clock terms. */
public enum class ReplaySpeed {
    /** Wall-clock pace. What a demo wants. */
    REALTIME,

    /** Ten minutes of walking in one. What a manual test wants. */
    FAST_10X,

    /**
     * No delays at all. What a unit test wants — and note that the *fixes* are unaffected:
     * their timestamps still carry the fixture's original spacing, so a five-minute gap in
     * the file is still a five-minute gap to the filter chain even though the whole replay
     * finished in a millisecond.
     */
    INSTANT,
}

/** Supplies fixture bytes. A factory, not a stream: a looping replay must read it again. */
public fun interface GpxSource {
    public fun open(): InputStream
}

/**
 * Plays a GPX fixture as though it were a GPS receiver.
 *
 * `LOCATION_ENGINE.md` is emphatic that this is not a nice-to-have, and the reason is
 * leverage: every tracking feature — the filter chain, the distance figures, the segment
 * breaks, the notification, the map polyline — becomes testable at a desk, and demonstrable
 * on a video call, instead of requiring somebody to walk around outside with a debugger
 * attached.
 *
 * ## Timestamps are the whole trick
 *
 * `elapsedRealtimeNanos` is synthesised as `base + (fixture time − first fixture time)`.
 * That single decision is what makes the fixtures able to test the things worth testing: a
 * gap in the file becomes a gap the Kalman filter resets on, spacing in the file becomes the
 * `dt` the filter smooths with, and none of it depends on how fast the replay actually ran.
 * A replay at [ReplaySpeed.INSTANT] and the same replay at [ReplaySpeed.REALTIME] produce
 * identical fixes.
 *
 * ## Accuracy
 *
 * Taken from the `<accuracy>` extension when present, else derived from `<hdop>` by
 * [HDOP_TO_METRES], else [defaultAccuracyMetres]. The HDOP conversion is a rule of thumb —
 * HDOP is dimensionless, and how many metres it buys depends on the receiver's error
 * budget — which is exactly why our own fixtures state accuracy outright instead of leaning
 * on it.
 */
public class ReplayLocationEngine(
    private val scope: CoroutineScope,
    private val source: GpxSource,
    private val speed: ReplaySpeed = ReplaySpeed.REALTIME,
    private val baseElapsedRealtimeNanos: Long = DEFAULT_BASE_NANOS,
    private val baseWallClockMillis: Long = DEFAULT_BASE_WALL_CLOCK_MS,
    private val defaultAccuracyMetres: Double = DEFAULT_ACCURACY_M,
    private val loop: Boolean = false,
) : LocationEngine {
    private val broadcaster = FixBroadcaster()
    private var job: Job? = null

    @Volatile
    private var lastEmitted: RawFix? = null

    override val fixes: Flow<RawFix> = broadcaster.fixes

    override val capabilities: EngineCapabilities =
        EngineCapabilities(
            kind = EngineKind.REPLAY,
            supportsBatching = false,
            supportsGnssStatus = false,
            // GPX has no speed field, so every fix reports none and the chain derives what
            // it needs from position and time.
            reportsSpeed = false,
        )

    /** Counts fixes lost to a stalled consumer. See [FixBroadcaster]. */
    public val droppedFixCount: StateFlow<Int>
        get() = broadcaster.droppedFixCount

    override suspend fun start(config: TrackingConfig) {
        stop()
        val trace = source.open().use { GpxReader.read(it) }
        val timeline = timelineMillis(trace, config.profile.intervalMillis)
        job = scope.launch { play(trace, timeline) }
    }

    override suspend fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * The last fix played, or `null` before the replay starts.
     *
     * Deliberately not the fixture's first point. `lastKnown` means "the platform's cached
     * fix", and before a replay begins nothing is cached; returning the start of the route
     * would jump the map to a place the user has never been.
     */
    override suspend fun lastKnown(): RawFix? = lastEmitted

    private suspend fun play(
        trace: GpxTrace,
        timeline: List<Long>,
    ) {
        var passOffsetMillis = 0L
        // One interval of separation between passes, so a looping demo does not emit two
        // fixes at the same instant across the seam — the validity gate rejects the second.
        val passStrideMillis = timeline.last() + PASS_SEPARATION_MILLIS

        do {
            emitPass(trace, timeline, passOffsetMillis)
            passOffsetMillis += passStrideMillis
        } while (loop)
    }

    private suspend fun emitPass(
        trace: GpxTrace,
        timeline: List<Long>,
        passOffsetMillis: Long,
    ) {
        trace.points.forEachIndexed { index, point ->
            if (index > 0) delayFor(timeline[index] - timeline[index - 1])

            val fix = point.toRawFix(passOffsetMillis + timeline[index])
            lastEmitted = fix
            broadcaster.offer(fix)
        }
    }

    private suspend fun delayFor(gapMillis: Long) {
        val divisor =
            when (speed) {
                ReplaySpeed.REALTIME -> {
                    1.0
                }

                ReplaySpeed.FAST_10X -> {
                    FAST_FACTOR
                }

                ReplaySpeed.INSTANT -> {
                    // `yield()`, not `delay(0)`. A non-positive delay returns without
                    // suspending, so the emitter would run the entire trace inside one
                    // scheduler slice, fill FixBroadcaster's buffer, and drop the rest of the
                    // fixes before a collector was ever resumed. Yielding hands control over
                    // after each fix, which is what makes an instant replay lossless.
                    yield()
                    return
                }
            }
        delay((gapMillis / divisor).toLong())
    }

    private fun GpxTrackPoint.toRawFix(offsetMillis: Long): RawFix = RawFix(
        position = position,
        accuracyMetres = resolveAccuracy(),
        elapsedRealtimeNanos = baseElapsedRealtimeNanos + offsetMillis * NANOS_PER_MILLI,
        recordedAtMillis = baseWallClockMillis + offsetMillis,
        provider = LocationProvider.REPLAY,
        altitudeMetres = elevationMetres,
        isMock = false,
    )

    private fun GpxTrackPoint.resolveAccuracy(): Double = accuracyMetres
        ?: horizontalDilutionOfPrecision?.let { it * HDOP_TO_METRES }
        ?: defaultAccuracyMetres

    public companion object {
        /**
         * A plausible consumer-receiver error budget per unit of HDOP. Only reached for
         * third-party GPX files; our own fixtures state accuracy directly.
         */
        public const val HDOP_TO_METRES: Double = 5.0

        public const val DEFAULT_ACCURACY_M: Double = 5.0

        private const val FAST_FACTOR = 10.0

        /** An hour after boot: far enough from zero that an off-by-one reads as wrong. */
        private const val DEFAULT_BASE_NANOS = 3_600_000_000_000L

        /** 2026-01-01T00:00:00Z. */
        private const val DEFAULT_BASE_WALL_CLOCK_MS = 1_767_225_600_000L

        private const val NANOS_PER_MILLI = 1_000_000L
        private const val PASS_SEPARATION_MILLIS = 1_000L

        /**
         * Millisecond offsets from the start of the trace, one per point.
         *
         * Uses the fixture's own `<time>` elements when *every* point has one and they
         * strictly increase. Otherwise falls back to even spacing at the profile's interval.
         * All-or-nothing on purpose: interleaving real and synthesised times would produce a
         * timeline that jumps backwards, and the validity gate would then reject most of the
         * trace for non-monotonic time — a confusing failure a long way from its cause.
         */
        internal fun timelineMillis(
            trace: GpxTrace,
            syntheticIntervalMillis: Long,
        ): List<Long> {
            val synthetic = trace.points.indices.map { it * syntheticIntervalMillis }

            // `filterNotNull` rather than a null check plus a cast: if any time is missing the
            // sizes differ and the fixture is rejected wholesale, which is the intended
            // all-or-nothing behaviour and leaves nothing nullable to unwrap afterwards.
            val times = trace.points.mapNotNull { it.timeMillis }
            if (times.size != trace.points.size) return synthetic

            val first = times.first()
            val offsets = times.map { it - first }
            val strictlyIncreasing = offsets.zipWithNext().all { (a, b) -> b > a }
            return if (strictlyIncreasing) offsets else synthetic
        }
    }
}
