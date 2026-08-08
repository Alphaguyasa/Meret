package et.alpha.meret.core.location

import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.location.engine.GpxSource
import et.alpha.meret.core.location.filter.FixPipeline
import et.alpha.meret.core.location.filter.ProcessedFix
import et.alpha.meret.core.model.TrackingConfig
import java.io.InputStream

/**
 * The GPX fixtures, and the ground truth that goes with them.
 *
 * The numbers below are printed by `tools/fixtures/gen-traces.js`, which generates these files
 * from a seeded PRNG. Regenerating produces identical bytes, so these constants stay valid —
 * and if a fixture is ever changed on purpose, the generator prints the new truth rather than
 * leaving somebody to reverse-engineer it from the file.
 *
 * Note what the raw sums say about why any of this exists: the clean 1 km walk sums to
 * [CLEAN_WALK_RAW_METRES] if you simply add up consecutive raw fixes — twice the real distance
 * — and the stationary trace, from a device that never moved, sums to
 * [STATIONARY_RAW_METRES].
 */
internal object Traces {
    const val CLEAN_WALK_1KM = "traces/clean_walk_1km.gpx"
    const val WALK_WITH_OUTLIERS = "traces/walk_1km_with_outliers.gpx"
    const val STATIONARY_30MIN = "traces/stationary_30min.gpx"
    const val WALK_WITH_GAP = "traces/walk_with_5min_gap.gpx"

    /** Noise-free path length of the clean walk, metres. The figure the chain must recover. */
    const val CLEAN_WALK_TRUTH_METRES = 999.60

    /** Naive sum over raw fixes. Jitter roughly doubles the apparent distance. */
    const val CLEAN_WALK_RAW_METRES = 2011.40

    const val CLEAN_WALK_POINTS = 715

    /** Indices displaced by 200 m in `walk_1km_with_outliers.gpx`. */
    val OUTLIER_INDICES = listOf(97, 198, 301, 404, 512, 631)

    const val STATIONARY_POINTS = 600

    /** Naive sum over the raw fixes of a device that never moved. */
    const val STATIONARY_RAW_METRES = 1652.23

    const val GAP_POINTS_BEFORE = 120
    const val GAP_POINTS_AFTER = 120

    /** Each half of the gap trace walks this far. */
    const val GAP_SEGMENT_TRUTH_METRES = 166.60

    /** How far the device has moved across the gap. Must never be booked as walked. */
    const val GAP_JUMP_METRES = 420.0

    const val GAP_SECONDS = 300

    fun open(name: String): InputStream =
        requireNotNull(Traces::class.java.classLoader?.getResourceAsStream(name)) {
            "Missing test fixture: $name"
        }

    /** Reopens the fixture on every call, so a looping replay can read it again. */
    fun source(name: String): GpxSource = GpxSource { open(name) }
}

/**
 * A [TimeProvider] with a clock that does not move.
 *
 * Fixed rather than incrementing because nothing under test reads the clock for timing — fix
 * timestamps come from the fixture. It exists only to feed [UuidV7Generator], and a frozen
 * clock there proves the generator's in-millisecond counter keeps ids ordered, which is the
 * property `seq` ordering would otherwise quietly depend on.
 */
internal class FixedTimeProvider(
    private val millis: Long = 1_773_000_000_000L,
) : TimeProvider {
    override fun nowMillis(): Long = millis

    override fun elapsedRealtimeNanos(): Long = millis * 1_000_000L
}

/** Runs a whole trace through a fresh [FixPipeline] and returns every processed fix. */
internal fun runPipeline(
    fixes: List<RawFix>,
    config: TrackingConfig = TrackingConfig(),
    trackId: String = "track-under-test",
): List<ProcessedFix> {
    val pipeline =
        FixPipeline(
            trackId = trackId,
            config = config,
            uuidGenerator = UuidV7Generator(FixedTimeProvider()),
        )
    return fixes.map { pipeline.process(it) }
}
