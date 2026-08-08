package et.alpha.meret.core.location.engine

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.EngineKind
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.location.Traces
import et.alpha.meret.core.location.gpx.GpxReader
import et.alpha.meret.core.location.gpx.GpxTrace
import et.alpha.meret.core.location.gpx.GpxTrackPoint
import et.alpha.meret.core.model.LocationProfile
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

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayLocationEngineTest {
    @Test
    fun `reports itself as a replay engine with no sky and no batching`() {
        val engine =
            ReplayLocationEngine(
                scope = CoroutineScope(StandardTestDispatcher()),
                source = Traces.source(Traces.CLEAN_WALK_1KM),
            )

        val capabilities = engine.capabilities
        assertThat(capabilities.kind).isEqualTo(EngineKind.REPLAY)
        assertThat(capabilities.supportsGnssStatus).isFalse()
        assertThat(capabilities.supportsBatching).isFalse()
        assertThat(capabilities.reportsSpeed).isFalse()
    }

    /**
     * `lastKnown` means "the platform's cached fix". Before a replay begins nothing is cached,
     * and returning the start of the route would jump the map to a place the user has never
     * been.
     */
    @Test
    fun `last known is null before the replay starts`() = runTest {
        val engine =
            ReplayLocationEngine(
                scope = CoroutineScope(StandardTestDispatcher(testScheduler)),
                source = Traces.source(Traces.CLEAN_WALK_1KM),
                speed = ReplaySpeed.INSTANT,
            )

        assertThat(engine.lastKnown()).isNull()
    }

    @Test
    fun `last known is the most recent fix once playing`() = runTest {
        val harness = harness(Traces.CLEAN_WALK_1KM)

        harness.play()

        assertThat(harness.engine.lastKnown()).isEqualTo(harness.collected.last())
    }

    @Test
    fun `stopping halts emission`() = runTest {
        val harness = harness(Traces.CLEAN_WALK_1KM, speed = ReplaySpeed.REALTIME)

        harness.engine.start(TrackingConfig())
        harness.engine.stop()
        advanceUntilIdle()

        // The replay coroutine is cancelled before the scheduler ever advances past the first
        // fix, so at most the opening fix escapes.
        assertThat(harness.collected.size).isAtMost(1)
        harness.close()
    }

    @Test
    fun `restarting replays from the beginning`() = runTest {
        val harness = harness(Traces.WALK_WITH_GAP)

        harness.play()
        val firstPass = harness.collected.toList()
        harness.collected.clear()
        harness.play()

        // The source is a factory, not a stream, precisely so it can be read again.
        assertThat(harness.collected).isEqualTo(firstPass)
    }

    @Test
    fun `a looping replay keeps elapsed nanos monotonic across the seam`() = runTest {
        val engineScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine =
            ReplayLocationEngine(
                scope = engineScope,
                source = Traces.source(Traces.WALK_WITH_GAP),
                speed = ReplaySpeed.INSTANT,
                loop = true,
            )

        val collected = mutableListOf<RawFix>()
        val collector =
            collectorScope.launch {
                engine.fixes.collect {
                    collected += it
                    // A loop never ends on its own; stop after enough for two passes.
                    if (collected.size >= TWO_PASSES) engine.stop()
                }
            }

        engine.start(TrackingConfig())
        advanceUntilIdle()
        collector.cancel()
        engineScope.cancel()

        assertThat(collected.size).isAtLeast(TWO_PASSES)
        // Backwards timestamps across the seam would have the validity gate reject the entire
        // second pass as non-monotonic.
        assertThat(collected.map { it.elapsedRealtimeNanos }).isInStrictOrder()
    }

    // ---------------------------------------------------------------- timeline construction

    @Test
    fun `a trace with complete times uses them`() {
        val timeline =
            ReplayLocationEngine.timelineMillis(
                trace(times = listOf(5_000L, 6_000L, 9_000L)),
                syntheticIntervalMillis = 1_000L,
            )

        // Offsets from the first point, not absolute times.
        assertThat(timeline).containsExactly(0L, 1_000L, 4_000L).inOrder()
    }

    @Test
    fun `a trace with no times falls back to the profile interval`() {
        val timeline =
            ReplayLocationEngine.timelineMillis(
                trace(times = listOf(null, null, null)),
                syntheticIntervalMillis = LocationProfile.STANDARD.intervalMillis,
            )

        assertThat(timeline).containsExactly(0L, 3_000L, 6_000L).inOrder()
    }

    /**
     * All-or-nothing on purpose. Interleaving real and synthesised times would produce a
     * timeline that jumps backwards, and the validity gate would then reject most of the trace
     * for non-monotonic time — a confusing failure a long way from its cause.
     */
    @Test
    fun `a trace with partial times falls back rather than interleaving`() {
        val timeline =
            ReplayLocationEngine.timelineMillis(
                trace(times = listOf(5_000L, null, 9_000L)),
                syntheticIntervalMillis = 1_000L,
            )

        assertThat(timeline).containsExactly(0L, 1_000L, 2_000L).inOrder()
    }

    @Test
    fun `a trace whose times go backwards falls back`() {
        val timeline =
            ReplayLocationEngine.timelineMillis(
                trace(times = listOf(5_000L, 4_000L, 9_000L)),
                syntheticIntervalMillis = 1_000L,
            )

        assertThat(timeline).containsExactly(0L, 1_000L, 2_000L).inOrder()
    }

    @Test
    fun `repeated identical times count as not strictly increasing`() {
        val timeline =
            ReplayLocationEngine.timelineMillis(
                trace(times = listOf(5_000L, 5_000L)),
                syntheticIntervalMillis = 1_000L,
            )

        assertThat(timeline).containsExactly(0L, 1_000L).inOrder()
    }

    // ------------------------------------------------------------------------- accuracy

    @Test
    fun `accuracy comes from the extension when present`() = runTest {
        val harness = harness(Traces.CLEAN_WALK_1KM)

        harness.play()

        assertThat(harness.collected.map { it.accuracyMetres }.toSet()).containsExactly(4.0)
    }

    @Test
    fun `accuracy falls back to hdop and then to the default`() = runTest {
        val gpx =
            """
            <gpx version="1.1"><trk><trkseg>
              <trkpt lat="8.98" lon="38.75"><time>2026-03-14T07:30:00Z</time><hdop>2.0</hdop></trkpt>
              <trkpt lat="8.981" lon="38.751"><time>2026-03-14T07:30:01Z</time></trkpt>
            </trkseg></trk></gpx>
            """.trimIndent()

        val engineScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        val engine =
            ReplayLocationEngine(
                scope = engineScope,
                source = { gpx.byteInputStream() },
                speed = ReplaySpeed.INSTANT,
                defaultAccuracyMetres = 7.5,
            )
        val collected = mutableListOf<RawFix>()
        val collector = collectorScope.launch { engine.fixes.collect { collected += it } }
        engine.start(TrackingConfig())
        advanceUntilIdle()
        collector.cancel()
        engineScope.cancel()

        assertThat(collected[0].accuracyMetres)
            .isEqualTo(2.0 * ReplayLocationEngine.HDOP_TO_METRES)
        assertThat(collected[1].accuracyMetres).isEqualTo(7.5)
    }

    // -------------------------------------------------------------------------- harness

    private class Harness(
        val engine: ReplayLocationEngine,
        val collected: MutableList<RawFix>,
        private val scope: TestScope,
        private val engineScope: CoroutineScope,
        private val collectorScope: CoroutineScope,
    ) {
        private val collector = collectorScope.launch { engine.fixes.collect { collected += it } }

        suspend fun play() {
            engine.start(TrackingConfig())
            scope.advanceUntilIdle()
        }

        fun close() {
            collector.cancel()
            engineScope.cancel()
            collectorScope.cancel()
        }
    }

    /** See `ReplayedTraceTest.replay` for why the two dispatchers differ. */
    private fun TestScope.harness(
        trace: String,
        speed: ReplaySpeed = ReplaySpeed.INSTANT,
    ): Harness {
        val engineScope = CoroutineScope(StandardTestDispatcher(testScheduler))
        val collectorScope = CoroutineScope(UnconfinedTestDispatcher(testScheduler))
        return Harness(
            engine =
                ReplayLocationEngine(
                    scope = engineScope,
                    source = Traces.source(trace),
                    speed = speed,
                ),
            collected = mutableListOf(),
            scope = this,
            engineScope = engineScope,
            collectorScope = collectorScope,
        )
    }

    private fun trace(times: List<Long?>): GpxTrace = GpxTrace(
        name = "synthetic",
        points =
            times.mapIndexed { index, time ->
                GpxTrackPoint(
                    position = LatLng(8.98 + index * 0.001, 38.75),
                    segmentIndex = 0,
                    timeMillis = time,
                )
            },
    )

    private companion object {
        /** Two passes of `walk_with_5min_gap.gpx`, which has 240 points. */
        const val TWO_PASSES = 480
    }
}
