package et.alpha.meret.core.location

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.location.engine.ReplayLocationEngine
import et.alpha.meret.core.location.engine.ReplaySpeed
import et.alpha.meret.core.location.filter.MotionState
import et.alpha.meret.core.location.filter.ProcessedFix
import et.alpha.meret.core.location.filter.RejectionReason
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.math.abs

/**
 * The phase 4 acceptance criteria, end to end.
 *
 * `BUILD_PLAN.md`: "`ReplayLocationEngine` plays a fixture GPX and produces a filtered track
 * whose total distance is within 2% of the fixture's known distance, and injected outliers are
 * rejected without displacing the line."
 *
 * These go through the real [ReplayLocationEngine] rather than reading the GPX and building
 * `RawFix`es directly, because the criterion is about the two working together — in particular
 * about the engine reconstructing `elapsedRealtimeNanos` from the fixture's timestamps, which
 * is what makes the gap and spacing tests mean anything.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReplayedTraceTest {
    @Test
    fun `clean walk recovers its known distance within two percent`() = runTest {
        val processed = replayThroughPipeline(Traces.CLEAN_WALK_1KM)

        assertThat(processed).hasSize(Traces.CLEAN_WALK_POINTS)
        assertThat(processed.none { it.rejectionReason != null }).isTrue()

        val measured = processed.last().totalDistanceMetres
        val errorFraction =
            abs(measured - Traces.CLEAN_WALK_TRUTH_METRES) / Traces.CLEAN_WALK_TRUTH_METRES

        assertThat(errorFraction).isLessThan(DISTANCE_TOLERANCE)
    }

    /**
     * The filtering has to earn its place. Summing raw consecutive fixes on this trace gives
     * roughly twice the real distance, so a chain that merely passed values through would fail
     * the 2% bar by 100 percentage points, and this asserts the gap between the two rather than
     * only the absolute figure.
     */
    @Test
    fun `filtering removes the jitter that doubles a raw distance sum`() = runTest {
        val fixes = replay(Traces.CLEAN_WALK_1KM)

        val rawSum = GeoMath.pathLength(fixes.map { it.position })
        val filtered = runPipeline(fixes).last().totalDistanceMetres

        assertThat(rawSum).isGreaterThan(Traces.CLEAN_WALK_TRUTH_METRES * 1.5)
        assertThat(filtered).isLessThan(rawSum / 1.5)
    }

    @Test
    fun `injected outliers are rejected and do not displace the line`() = runTest {
        val clean = replayThroughPipeline(Traces.CLEAN_WALK_1KM)
        val withOutliers = replayThroughPipeline(Traces.WALK_WITH_OUTLIERS)

        val rejectedIndices =
            withOutliers.withIndex().filter { it.value.rejectionReason != null }.map { it.index }

        // Exactly the injected points, and rejected for the reason that matters: a 200 m jump
        // in one second is an impossible speed. Their reported accuracy is unchanged from the
        // clean trace, so an accuracy-only gate would have let every one of them through.
        assertThat(rejectedIndices).isEqualTo(Traces.OUTLIER_INDICES)
        assertThat(
            withOutliers
                .filter { it.rejectionReason != null }
                .map { it.rejectionReason }
                .toSet(),
        ).containsExactly(RejectionReason.IMPLIED_SPEED)

        // "Without displacing the line": the surviving filtered positions must match the clean
        // run's, not merely be close in total. A filter that saw the outlier would bend toward
        // it for several seconds afterwards.
        val cleanFiltered = clean.mapNotNull { it.point.filteredPosition }
        val outlierFiltered = withOutliers.mapNotNull { it.point.filteredPosition }
        assertThat(outlierFiltered).hasSize(cleanFiltered.size - Traces.OUTLIER_INDICES.size)

        val worstDisplacement =
            outlierFiltered.indices.maxOf { index ->
                GeoMath.haversineDistance(
                    outlierFiltered[index],
                    nearestCleanPosition(cleanFiltered, outlierFiltered[index]),
                )
            }
        assertThat(worstDisplacement).isLessThan(MAX_LINE_DISPLACEMENT_METRES)

        val cleanDistance = clean.last().totalDistanceMetres
        val outlierDistance = withOutliers.last().totalDistanceMetres
        assertThat(abs(outlierDistance - cleanDistance) / cleanDistance)
            .isLessThan(DISTANCE_TOLERANCE)
    }

    /**
     * The step gate, on the trace it exists for. Raw fixes from this stationary device sum to
     * over 1.6 km; `LOCATION_ENGINE.md` calls the gate "the difference between credible and
     * useless distance figures", and this is the number that proves it.
     */
    @Test
    fun `a stationary half hour accumulates almost no distance`() = runTest {
        val processed = replayThroughPipeline(Traces.STATIONARY_30MIN)

        assertThat(processed).hasSize(Traces.STATIONARY_POINTS)
        assertThat(processed.last().totalDistanceMetres)
            .isLessThan(MAX_STATIONARY_DRIFT_METRES)
    }

    @Test
    fun `a stationary half hour is detected as paused and accrues no moving time`() = runTest {
        val processed = replayThroughPipeline(Traces.STATIONARY_30MIN)

        assertThat(processed.last().motionState).isEqualTo(MotionState.PAUSED)

        // Auto-pause needs a full 60 s window before it can fire, so a minute of moving time at
        // the start is expected and correct. What must not happen is half an hour of it.
        val movingMinutes = processed.last().movingDurationMillis / 60_000.0
        assertThat(movingMinutes).isLessThan(2.0)
    }

    @Test
    fun `a five minute gap breaks the segment and is not counted as distance`() = runTest {
        val processed = replayThroughPipeline(Traces.WALK_WITH_GAP)

        assertThat(processed).hasSize(Traces.GAP_POINTS_BEFORE + Traces.GAP_POINTS_AFTER)

        // The gap is inside a single <trkseg>, so the break can only have come from the
        // timestamps — which is the point of the fixture.
        val boundaries = processed.withIndex().filter { it.value.startedNewSegment }
        assertThat(boundaries.map { it.index }).containsExactly(Traces.GAP_POINTS_BEFORE)

        assertThat(processed.map { it.point.segmentIndex }.distinct()).containsExactly(0, 1)

        // Both halves walk 166.6 m. The 420 m the device travelled while unobserved must not
        // appear: measuring across the gap would put the total near 753 m.
        val expected = Traces.GAP_SEGMENT_TRUTH_METRES * 2
        val measured = processed.last().totalDistanceMetres
        assertThat(abs(measured - expected) / expected).isLessThan(DISTANCE_TOLERANCE)
        assertThat(measured).isLessThan(expected + Traces.GAP_JUMP_METRES / 2)
    }

    @Test
    fun `replay speed does not change the fixes produced`() = runTest {
        val instant = replay(Traces.WALK_WITH_GAP, ReplaySpeed.INSTANT)
        val fast = replay(Traces.WALK_WITH_GAP, ReplaySpeed.FAST_10X)

        // Identical, not merely similar. Timing comes from the fixture, never from how fast the
        // replay ran, which is what lets a unit test and a live demo exercise the same code.
        assertThat(fast).isEqualTo(instant)
    }

    @Test
    fun `every replayed fix is marked as replay data`() = runTest {
        val fixes = replay(Traces.WALK_WITH_GAP)

        // A replayed point must never be mistakable for field data on the diagnostics screen or
        // in an export.
        assertThat(fixes.map { it.provider }.toSet()).containsExactly(LocationProvider.REPLAY)
    }

    @Test
    fun `elapsed nanos reproduce the fixture's own gap`() = runTest {
        val fixes = replay(Traces.WALK_WITH_GAP)

        val gapNanos =
            fixes[Traces.GAP_POINTS_BEFORE].elapsedRealtimeNanos -
                fixes[Traces.GAP_POINTS_BEFORE - 1].elapsedRealtimeNanos

        assertThat(gapNanos).isEqualTo(Traces.GAP_SECONDS * 1_000_000_000L)
    }

    /**
     * Plays a fixture and returns every fix it produced.
     *
     * The two scopes are not interchangeable, and getting either wrong silently truncates the
     * trace — which shows up as a distance assertion failing by a plausible-looking amount
     * rather than as an error, so it is worth spelling out:
     *
     * - The **engine** runs on a `StandardTestDispatcher` sharing the test's scheduler, so
     *   `advanceUntilIdle` drives the replay to completion. `backgroundScope` does *not* work
     *   here: its coroutines are not advanced by `advanceUntilIdle`, so the replay never runs
     *   and every trace comes back empty.
     * - The **collector** is `UnconfinedTestDispatcher`, so each emission is delivered
     *   synchronously inside `offer` instead of being queued behind the emitter. With a
     *   queued collector the final fix is still buffered when `advanceUntilIdle` returns, and
     *   the trace comes back one point short.
     *
     * The subscriber is registered before [ReplayLocationEngine.start], because `fixes` is hot
     * and does not replay: a late subscriber misses the opening fixes outright.
     */
    private suspend fun TestScope.replay(
        trace: String,
        speed: ReplaySpeed = ReplaySpeed.INSTANT,
    ): List<RawFix> {
        val engineScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))

        val engine =
            ReplayLocationEngine(
                scope = engineScope,
                source = Traces.source(trace),
                speed = speed,
            )

        val collected = mutableListOf<RawFix>()
        val collector = collectorScope.launch { engine.fixes.collect { collected += it } }

        engine.start(TrackingConfig())
        advanceUntilIdle()
        collector.cancel()
        engineScope.cancel()

        // Nothing may be lost to buffering, or every figure below is measuring a shorter walk
        // than the fixture describes.
        assertThat(engine.droppedFixCount.value).isEqualTo(0)
        return collected
    }

    private suspend fun TestScope.replayThroughPipeline(trace: String): List<ProcessedFix> =
        runPipeline(replay(trace))

    private fun nearestCleanPosition(
        clean: List<LatLng>,
        target: LatLng,
    ): LatLng = clean.minBy { GeoMath.haversineDistance(it, target) }

    private companion object {
        /** The 2% from BUILD_PLAN.md phase 4. */
        const val DISTANCE_TOLERANCE = 0.02

        /**
         * How far a surviving filtered point may sit from the nearest point of the clean run.
         * Generous enough to allow the smoothing to differ slightly where a fix is missing from
         * the sequence, tight enough that a filter dragged toward a 200 m outlier fails.
         */
        const val MAX_LINE_DISPLACEMENT_METRES = 2.0

        /** Well inside the 50 m bar from SESSION_PROMPTS.md session 4. */
        const val MAX_STATIONARY_DRIFT_METRES = 50.0
    }
}
