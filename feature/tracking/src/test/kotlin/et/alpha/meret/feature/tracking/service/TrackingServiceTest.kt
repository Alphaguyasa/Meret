package et.alpha.meret.feature.tracking.service

import android.app.Application
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.model.Track
import et.alpha.meret.core.model.geo.TrackLine
import et.alpha.meret.domain.diagnostics.TrackingHealth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowPowerManager

/**
 * The service's Android obligations, against a fake controller: foreground with a
 * notification on every start, the wakelock only while recording, sticky restart restoring
 * the session, and stopping itself when there is nothing to record. The controller's own
 * rules are covered in `:core:data`'s `DefaultTrackingControllerTest`.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTrackingApplication::class)
class TrackingServiceTest {
    private val controller: FakeTrackingController
        get() =
            ApplicationProvider
                .getApplicationContext<TestTrackingApplication>()
                .trackingController

    @Test
    fun `start runs in the foreground, starts the track and holds the wakelock`() {
        val service = startService(startIntent()).get()

        assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
        assertThat(controller.calls).containsExactly("start:$TRACK_ID")
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
    }

    @Test
    fun `stop releases the wakelock, ends the session and stops the service`() {
        val running = startService(startIntent())

        running.withIntent(actionIntent(TrackingCommand.ACTION_STOP)).startCommand(0, 2)
        idleMainLooper()

        val service = running.get()
        assertThat(controller.calls).containsExactly("start:$TRACK_ID", "stop").inOrder()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
        assertThat(shadowOf(service).isForegroundStopped).isTrue()
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `pause releases the wakelock and keeps the service running`() {
        val running = startService(startIntent())

        running.withIntent(actionIntent(TrackingCommand.ACTION_PAUSE)).startCommand(0, 2)
        idleMainLooper()

        assertThat(controller.calls).containsExactly("start:$TRACK_ID", "pause").inOrder()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isFalse()
        assertThat(shadowOf(running.get()).isStoppedBySelf).isFalse()
    }

    @Test
    fun `reopen after a stop records the same track again in the foreground`() {
        val running = startService(startIntent())
        running.withIntent(actionIntent(TrackingCommand.ACTION_STOP)).startCommand(0, 2)
        idleMainLooper()

        val reopened = startService(reopenIntent()).get()

        assertThat(controller.calls)
            .containsExactly("start:$TRACK_ID", "stop", "reopen:$TRACK_ID")
            .inOrder()
        assertThat(shadowOf(reopened).lastForegroundNotification).isNotNull()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
        assertThat(shadowOf(reopened).isStoppedBySelf).isFalse()
    }

    @Test
    fun `a reopen with no track id restores instead`() {
        assertThat(TrackingCommand.from(actionIntent(TrackingCommand.ACTION_REOPEN)))
            .isEqualTo(TrackingCommand.Restore)
        assertThat(TrackingCommand.from(reopenIntent()))
            .isEqualTo(TrackingCommand.Reopen(TRACK_ID))
    }

    /** START_STICKY after process death: no action, and the persisted session resumes. */
    @Test
    fun `a sticky restart restores the persisted session`() {
        controller.restoreTo = TrackingState.Recording(TRACK_ID, isPaused = false)

        val service = startService(Intent()).get()

        assertThat(controller.calls).containsExactly("restore")
        assertThat(controller.state.value)
            .isEqualTo(TrackingState.Recording(TRACK_ID, isPaused = false))
        assertThat(shadowOf(service).lastForegroundNotification).isNotNull()
        assertThat(ShadowPowerManager.getLatestWakeLock().isHeld).isTrue()
        assertThat(shadowOf(service).isStoppedBySelf).isFalse()
    }

    @Test
    fun `a restart with no session stops itself`() {
        val service = startService(Intent()).get()

        assertThat(controller.calls).containsExactly("restore")
        assertThat(shadowOf(service).isStoppedBySelf).isTrue()
    }

    @Test
    fun `the recording notification offers pause and stop, the paused one resume and stop`() {
        val notifications = TrackingNotifications(ApplicationProvider.getApplicationContext())
        notifications.ensureChannel()

        val recording =
            notifications.build(TrackingState.Recording(TRACK_ID, isPaused = false), null)
        val paused = notifications.build(TrackingState.Recording(TRACK_ID, isPaused = true), null)

        assertThat(recording.actions.map { it.title.toString() })
            .containsExactly("Pause", "Stop")
            .inOrder()
        assertThat(paused.actions.map { it.title.toString() })
            .containsExactly("Resume", "Stop")
            .inOrder()
    }

    private fun startService(intent: Intent): ServiceController<TrackingService> {
        val controller =
            Robolectric
                .buildService(TrackingService::class.java, intent)
                .create()
                .startCommand(0, 1)
        idleMainLooper()
        return controller
    }

    private fun startIntent(): Intent = actionIntent(TrackingCommand.ACTION_START)
        .putExtra(TrackingCommand.EXTRA_TRACK_ID, TRACK_ID)

    private fun reopenIntent(): Intent = actionIntent(TrackingCommand.ACTION_REOPEN)
        .putExtra(TrackingCommand.EXTRA_TRACK_ID, TRACK_ID)

    private fun actionIntent(action: String): Intent = Intent(action)

    private fun idleMainLooper() {
        shadowOf(Looper.getMainLooper()).idle()
    }

    private companion object {
        const val TRACK_ID = "track-1"
    }
}

/** Robolectric's application for these tests: hands the service a fake controller. */
class TestTrackingApplication :
    Application(),
    TrackingServiceHost {
    override val trackingController: FakeTrackingController = FakeTrackingController()
}

/** Records commands and moves [state] the way the real controller would. */
class FakeTrackingController : TrackingController {
    val calls = mutableListOf<String>()
    var restoreTo: TrackingState = TrackingState.Idle

    override val state = MutableStateFlow<TrackingState>(TrackingState.Idle)

    override val activeTrack: Flow<Track?> = MutableStateFlow(null)

    override val activeLine: Flow<TrackLine?> = MutableStateFlow(null)

    override val activeSegmentCount: Flow<Int?> = MutableStateFlow(null)

    override val health = MutableStateFlow(TrackingHealth())

    override suspend fun start(trackId: String): AppResult<Unit> {
        calls += "start:$trackId"
        state.value = TrackingState.Recording(trackId, isPaused = false)
        return AppResult.Success(Unit)
    }

    override suspend fun restore(): AppResult<TrackingState> {
        calls += "restore"
        state.value = restoreTo
        return AppResult.Success(restoreTo)
    }

    var interrupted: String? = null

    override suspend fun interruptedTrackId(): String? = interrupted

    override suspend fun pause() {
        calls += "pause"
        val current = state.value
        if (current is TrackingState.Recording) state.value = current.copy(isPaused = true)
    }

    override suspend fun resume(): AppResult<Unit> {
        calls += "resume"
        val current = state.value
        if (current is TrackingState.Recording) state.value = current.copy(isPaused = false)
        return AppResult.Success(Unit)
    }

    override suspend fun stop() {
        calls += "stop"
        state.value = TrackingState.Idle
    }

    override suspend fun reopen(trackId: String): AppResult<Unit> {
        calls += "reopen:$trackId"
        state.value = TrackingState.Recording(trackId, isPaused = false)
        return AppResult.Success(Unit)
    }
}
