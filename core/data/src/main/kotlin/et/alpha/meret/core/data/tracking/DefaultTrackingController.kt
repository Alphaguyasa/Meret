package et.alpha.meret.core.data.tracking

import android.Manifest
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.result.map
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.mapper.toDomain
import et.alpha.meret.core.datastore.TrackingConfigStore
import et.alpha.meret.core.datastore.TrackingSessionStore
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.TrackStatus
import et.alpha.meret.core.model.TrackingConfig
import et.alpha.meret.core.model.TrackingSession
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.diagnostics.TrackingHealthTracker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject

/**
 * The production [TrackingController].
 *
 * ## Order of writes
 *
 * Each command is ordered so that a process killed between any two steps resumes, rather than
 * loses, the track:
 *
 * - **start** persists the session before the engine runs, so the first fix already has a
 *   session to come back to.
 * - **stop** stops the engine first (fixes after the recorder's collector is cancelled would be
 *   lost, see [TrackRecording.stop]), writes the last batch, completes the row, and clears the
 *   session last. Killed before the clear, [restore] finds a completed track and clears it.
 *
 * ## Health
 *
 * A [TrackingHealthTracker] watches the same fixes as the recorder, and a ticker polls it once
 * per sampling interval so a gap shows up while it is still open, not only when the next fix
 * ends it. Both run only between [start] (or [restore]) and [stop]; the engine's own
 * transitions mark recording time.
 *
 * ## Scope
 *
 * Recordings run in this object's own scope, not the caller's. The service's scope ends with
 * the service; the recording must end with [stop], which is a different moment when Android
 * destroys and recreates the service around a running process.
 */
public class DefaultTrackingController
    @Inject
    internal constructor(
        private val engine: LocationEngine,
        private val recorder: TrackRecorder,
        private val sessionStore: TrackingSessionStore,
        private val configStore: TrackingConfigStore,
        private val trackDao: TrackDao,
    ) : TrackingController {
        private val timeProvider = recorder.timeProvider
        private val scope = CoroutineScope(SupervisorJob() + recorder.dispatchers.default)
        private val mutex = Mutex()
        private val mutableState = MutableStateFlow<TrackingState>(TrackingState.Idle)

        private val healthTracker = TrackingHealthTracker()
        private val mutableHealth = MutableStateFlow(healthTracker.current)
        private var healthJob: Job? = null

        private var recording: TrackRecording? = null
        private var config: TrackingConfig = TrackingConfig()

        override val state: StateFlow<TrackingState> = mutableState.asStateFlow()

        override val activeTrack: Flow<Track?> =
            trackDao.observeActiveTrack().map { it?.toDomain() }

        override val health: StateFlow<TrackingHealth> = mutableHealth.asStateFlow()

        override suspend fun start(trackId: String): AppResult<Unit> =
            mutex.withLock { startLocked(trackId) }

        override suspend fun restore(): AppResult<TrackingState> =
            mutex.withLock { restoreLocked() }

        override suspend fun interruptedTrackId(): String? = mutex.withLock {
            if (mutableState.value != TrackingState.Idle) return@withLock null
            val trackId = sessionStore.session.first()?.trackId ?: return@withLock null
            trackId.takeIf { trackDao.getTrack(it)?.status?.isOpen() == true }
        }

        override suspend fun pause() {
            mutex.withLock {
                val current = mutableState.value
                if (current is TrackingState.Recording && !current.isPaused) {
                    pauseLocked(current.trackId)
                }
            }
        }

        override suspend fun resume(): AppResult<Unit> = mutex.withLock {
            val current = mutableState.value
            if (current is TrackingState.Recording && current.isPaused) {
                checkNotNull(recording).resume()
                sessionStore.setPaused(false)
                val now = timeProvider.nowMillis()
                trackDao.updateStatus(current.trackId, TrackStatus.ACTIVE, now)
                startEngine(current.trackId)
            } else {
                AppResult.Success(Unit)
            }
        }

        override suspend fun stop() {
            mutex.withLock {
                val current = mutableState.value
                if (current is TrackingState.Recording) stopLocked(current.trackId)
            }
        }

        private suspend fun startLocked(trackId: String): AppResult<Unit> {
            val current = mutableState.value
            val track = if (current is TrackingState.Idle) trackDao.getTrack(trackId) else null
            return when {
                current is TrackingState.Recording && current.trackId == trackId -> {
                    AppResult.Success(Unit)
                }

                current is TrackingState.Recording -> {
                    AppResult.Failure(AppError.Conflict(TRACK_ENTITY, current.trackId))
                }

                track == null -> {
                    AppResult.Failure(AppError.Storage(AppError.Storage.Kind.NotFound))
                }

                else -> {
                    sessionStore.start(
                        TrackingSession(
                            trackId = trackId,
                            startedAtMillis = track.startedAt,
                            startedAtElapsedRealtimeNanos = timeProvider.elapsedRealtimeNanos(),
                        ),
                    )
                    trackDao.updateStatus(trackId, TrackStatus.ACTIVE, timeProvider.nowMillis())
                    beginRecording(trackId)
                    startEngine(trackId)
                }
            }
        }

        private suspend fun restoreLocked(): AppResult<TrackingState> {
            val current = mutableState.value
            val session = if (current is TrackingState.Idle) sessionStore.session.first() else null
            val track = session?.let { trackDao.getTrack(it.trackId) }
            return when {
                current is TrackingState.Recording -> {
                    AppResult.Success(current)
                }

                session == null -> {
                    AppResult.Success(TrackingState.Idle)
                }

                track == null || !track.status.isOpen() -> {
                    // A session with nothing to resume: a finished track, or a row lost with
                    // the database. Keeping it would turn every later start into a resume.
                    sessionStore.clear()
                    AppResult.Success(TrackingState.Idle)
                }

                else -> {
                    resumeSession(session).map { mutableState.value }
                }
            }
        }

        private suspend fun resumeSession(session: TrackingSession): AppResult<Unit> {
            beginRecording(session.trackId)
            return if (session.isPaused) {
                checkNotNull(recording).pause()
                mutableState.value = TrackingState.Recording(session.trackId, isPaused = true)
                AppResult.Success(Unit)
            } else {
                startEngine(session.trackId)
            }
        }

        private suspend fun pauseLocked(trackId: String) {
            engine.stop()
            checkNotNull(recording).pause()
            mutableHealth.value = healthTracker.pause(timeProvider.elapsedRealtimeNanos())
            sessionStore.setPaused(true)
            trackDao.updateStatus(trackId, TrackStatus.PAUSED, timeProvider.nowMillis())
            mutableState.value = TrackingState.Recording(trackId, isPaused = true)
        }

        private suspend fun stopLocked(trackId: String) {
            engine.stop()
            recording?.stop()
            recording = null
            healthJob?.cancel()
            healthJob = null
            mutableHealth.value = healthTracker.end(timeProvider.elapsedRealtimeNanos())
            val now = timeProvider.nowMillis()
            trackDao.endTrack(trackId, TrackStatus.COMPLETED, endedAt = now, updatedAt = now)
            sessionStore.clear()
            mutableState.value = TrackingState.Idle
        }

        /** Subscribes the recorder to the engine's flow. The engine is started separately. */
        private suspend fun beginRecording(trackId: String) {
            config = configStore.config.first()
            recording = recorder.start(scope, trackId, config, engine.fixes)
            watchHealth(config.profile.intervalMillis)
        }

        /** Feeds the health tracker every fix, and polls it for a gap still open. */
        private fun watchHealth(samplingIntervalMillis: Long) {
            healthJob?.cancel()
            mutableHealth.value = healthTracker.begin(samplingIntervalMillis)
            // UNDISPATCHED, like the recorder: subscribed to the hot flow before this returns.
            healthJob =
                scope.launch(start = CoroutineStart.UNDISPATCHED) {
                    launch(start = CoroutineStart.UNDISPATCHED) {
                        engine.fixes.collect { fix ->
                            mutableHealth.value =
                                healthTracker.onFix(
                                    atElapsedNanos = fix.elapsedRealtimeNanos,
                                    accuracyMetres = fix.accuracyMetres,
                                    satellitesUsed = fix.satellitesUsed,
                                )
                        }
                    }
                    while (isActive) {
                        delay(samplingIntervalMillis)
                        mutableHealth.value =
                            healthTracker.tick(timeProvider.elapsedRealtimeNanos())
                    }
                }
        }

        /**
         * Starts the engine, or, if location permission has been revoked, leaves the track
         * paused. Paused rather than stopped: the user can grant the permission and resume
         * into the same track, and nothing already recorded is touched.
         */
        private suspend fun startEngine(trackId: String): AppResult<Unit> = try {
            engine.start(config)
            mutableHealth.value = healthTracker.resume(timeProvider.elapsedRealtimeNanos())
            mutableState.value = TrackingState.Recording(trackId, isPaused = false)
            AppResult.Success(Unit)
        } catch (denied: SecurityException) {
            pauseLocked(trackId)
            AppResult.Failure(
                AppError.Permission(Manifest.permission.ACCESS_FINE_LOCATION, cause = denied),
            )
        }

        private fun TrackStatus.isOpen(): Boolean =
            this == TrackStatus.ACTIVE || this == TrackStatus.PAUSED

        private companion object {
            const val TRACK_ENTITY = "track"
        }
    }
