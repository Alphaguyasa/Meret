package et.alpha.meret.core.data.tracking

import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.dao.TrackPointDao
import et.alpha.meret.core.database.mapper.toEntity
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.location.filter.FixPipeline
import et.alpha.meret.core.location.filter.ProcessedFix
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Turns a stream of raw fixes into `track_point` rows, in batches.
 *
 * `ARCHITECTURE.md` §Concurrency: fixes are posted to a [Channel] and consumed by a single
 * coroutine that runs the filter chain and writes a batch every
 * [TrackingConfig.writeBatchSize] points or [TrackingConfig.writeBatchWindowMillis],
 * whichever comes first — never one transaction per fix. Pause and stop flush immediately.
 *
 * Lives in `:core:data` because it is the one layer allowed to see Room, and it is the only
 * writer of a track's points: the tracking service owns the engine and hands its fixes here.
 */
public class TrackRecorder
    @Inject
    internal constructor(
        internal val trackDao: TrackDao,
        internal val trackPointDao: TrackPointDao,
        internal val dispatchers: DispatcherProvider,
        internal val timeProvider: TimeProvider,
        internal val uuidGenerator: UuidV7Generator,
    ) {
        /**
         * Starts recording [fixes] into [trackId], in [scope].
         *
         * Subscribes to [fixes] before returning, so a hot engine flow started afterwards loses
         * nothing. The track row must already exist; resuming one that has points (a service
         * restarted after process death) continues its `seq` numbering and totals.
         */
        public fun start(
            scope: CoroutineScope,
            trackId: String,
            config: TrackingConfig,
            fixes: Flow<RawFix>,
        ): TrackRecording = TrackRecording(
            scope = scope,
            trackId = trackId,
            config = config,
            fixes = fixes,
            recorder = this,
        )
    }

/**
 * One track being recorded. Obtained from [TrackRecorder.start].
 *
 * ## Nothing is dropped
 *
 * Every fix that reaches the recorder while it is recording becomes a row. Fixes the
 * validity gate rejects are written with `is_outlier = 1` (CLAUDE.md invariant 2), and the
 * last partial batch is written on [pause] and [stop]. The channel is unbounded so the
 * engine-side collector never suspends: a suspended collector would make the engine's own
 * buffer overflow, and that drops fixes before they ever reach here.
 *
 * While paused, incoming fixes are not recorded. Pausing is the user saying "this part is
 * not the survey"; the service also stops the engine then, so this is a guard, not a filter.
 *
 * ## Failure
 *
 * A failed Room write propagates and ends the recording, cancelling [scope]'s child jobs.
 * The rows already written stay written; the in-memory batch is the most a failure can cost.
 */
public class TrackRecording internal constructor(
    scope: CoroutineScope,
    private val trackId: String,
    private val config: TrackingConfig,
    fixes: Flow<RawFix>,
    recorder: TrackRecorder,
) {
    private val trackDao = recorder.trackDao
    private val trackPointDao = recorder.trackPointDao
    private val timeProvider = recorder.timeProvider
    private val dispatchers = recorder.dispatchers

    private val events = Channel<Event>(Channel.UNLIMITED)
    private val pipeline = FixPipeline(trackId, config, recorder.uuidGenerator)
    private val pending = ArrayList<ProcessedFix>(config.writeBatchSize)

    private var writtenDistanceMetres = 0.0
    private var writtenMovingMillis = 0L
    private var isPaused = false

    private val received = MutableStateFlow(0)

    /**
     * Fixes taken off the channel in this recording: outliers included, and those ignored
     * while paused. For diagnostics, and for tests to know the channel has drained.
     */
    public val fixesReceived: StateFlow<Int> = received.asStateFlow()

    private val consumer: Job = scope.launch(dispatchers.default) { consume() }

    // UNDISPATCHED: subscribe to the engine's hot flow before `start` returns.
    private val collector: Job =
        scope.launch(dispatchers.default, start = CoroutineStart.UNDISPATCHED) {
            fixes.collect { events.send(Event.Fix(it)) }
        }

    private val ticker: Job =
        scope.launch(dispatchers.default) {
            val window = config.writeBatchWindowMillis.coerceAtLeast(1L)
            while (true) {
                delay(window)
                events.send(Event.Tick)
            }
        }

    /** Writes the pending batch, then ignores fixes until [resume]. Returns once written. */
    public suspend fun pause() {
        command { Event.Pause(it) }
    }

    /** Resumes recording in a new segment: the device may have moved while paused. */
    public suspend fun resume() {
        command { Event.Resume(it) }
    }

    /**
     * Stops collecting, writes every fix already received, and ends the recording.
     *
     * Stop the engine first: fixes it emits after the collector is cancelled are not
     * recorded.
     */
    public suspend fun stop() {
        collector.cancelAndJoin()
        ticker.cancelAndJoin()
        command { Event.Stop(it) }
        consumer.join()
    }

    private suspend fun command(event: (CompletableDeferred<Unit>) -> Event) {
        val done = CompletableDeferred<Unit>()
        events.send(event(done))
        done.await()
    }

    private suspend fun consume() {
        resumeExistingTrack()
        for (event in events) {
            when (event) {
                is Event.Fix -> {
                    record(event.fix)
                }

                Event.Tick -> {
                    flush()
                }

                is Event.Pause -> {
                    flush()
                    isPaused = true
                    event.done.complete(Unit)
                }

                is Event.Resume -> {
                    if (isPaused) {
                        pipeline.startNewSegment()
                        isPaused = false
                    }
                    event.done.complete(Unit)
                }

                is Event.Stop -> {
                    flush()
                    events.close()
                    event.done.complete(Unit)
                }
            }
        }
    }

    /**
     * Continues a track that already has rows. Starting at `seq = 0` would collide with them
     * on `(track_id, seq)` and the whole batch would be silently ignored.
     */
    private suspend fun resumeExistingTrack() {
        val maxSeq = trackPointDao.getMaxSeq(trackId) ?: return
        val track = trackDao.getTrack(trackId)
        writtenDistanceMetres = track?.distanceMeters ?: 0.0
        writtenMovingMillis = track?.movingDurationMillis ?: 0L
        pipeline.resumeFrom(
            nextSeq = maxSeq + 1,
            distanceMetres = writtenDistanceMetres,
            movingMillis = writtenMovingMillis,
            segmentIndex = trackPointDao.getMaxSegmentIndex(trackId) ?: 0,
        )
    }

    private suspend fun record(fix: RawFix) {
        received.value += 1
        if (isPaused) return
        pending += pipeline.process(fix)
        if (pending.size >= config.writeBatchSize) flush()
    }

    /**
     * Writes the pending batch with the totals it moved. [ProcessedFix] carries absolute
     * totals and `appendBatch` takes deltas, so the delta is measured against what the last
     * successful write left on the track.
     */
    private suspend fun flush() {
        if (pending.isEmpty()) return
        val last = pending.last()
        trackPointDao.appendBatch(
            trackId = trackId,
            points = pending.map { it.point.toEntity() },
            distanceDeltaMeters = last.totalDistanceMetres - writtenDistanceMetres,
            movingDurationDeltaMillis = last.movingDurationMillis - writtenMovingMillis,
            updatedAt = timeProvider.nowMillis(),
        )
        writtenDistanceMetres = last.totalDistanceMetres
        writtenMovingMillis = last.movingDurationMillis
        pending.clear()
    }

    private sealed interface Event {
        data class Fix(
            val fix: RawFix,
        ) : Event

        data object Tick : Event

        data class Pause(
            val done: CompletableDeferred<Unit>,
        ) : Event

        data class Resume(
            val done: CompletableDeferred<Unit>,
        ) : Event

        data class Stop(
            val done: CompletableDeferred<Unit>,
        ) : Event
    }
}
