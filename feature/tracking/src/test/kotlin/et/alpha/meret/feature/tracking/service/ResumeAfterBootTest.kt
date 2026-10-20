package et.alpha.meret.feature.tracking.service

import android.app.Application
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * After a reboot: one prompt to resume an open track, nothing without one, and never a
 * service started on its own.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestTrackingApplication::class)
class ResumeAfterBootTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val controller = FakeTrackingController()
    private val notifications = shadowOf(context.getSystemService(NotificationManager::class.java))

    @Test
    fun `an open track after boot posts one resume prompt and starts nothing`() =
        runBlocking<Unit> {
            controller.interrupted = TRACK_ID

            val posted = ResumeAfterBoot(context, controller).run()

            assertThat(posted).isTrue()
            val prompt = notifications.allNotifications.single()
            assertThat(prompt.channelId).isEqualTo(ResumeAfterBoot.CHANNEL_ID)
            val buttons = prompt.actions.map { it.title.toString() }
            assertThat(buttons).containsExactly("Resume recording")
            assertThat(shadowOf(context as Application).nextStartedService).isNull()
            assertThat(controller.calls).isEmpty()
        }

    @Test
    fun `no open track after boot posts nothing`() = runBlocking<Unit> {
        val posted = ResumeAfterBoot(context, controller).run()

        assertThat(posted).isFalse()
        assertThat(notifications.allNotifications).isEmpty()
    }

    /** The prompt's button is what starts the service, as a restore of the same session. */
    @Test
    fun `the restore action maps to a restore`() {
        val command = TrackingCommand.from(Intent(TrackingCommand.ACTION_RESTORE))

        assertThat(command).isEqualTo(TrackingCommand.Restore)
    }

    @Test
    fun `the receiver ignores anything but boot completed`() {
        BootReceiver().onReceive(context, Intent(Intent.ACTION_LOCKED_BOOT_COMPLETED))

        assertThat(notifications.allNotifications).isEmpty()
    }

    @Test
    fun `the receiver is registered for boot completed`() {
        val receivers =
            context.packageManager.queryBroadcastReceivers(
                Intent(Intent.ACTION_BOOT_COMPLETED).setPackage(context.packageName),
                0,
            )

        assertThat(receivers.map { it.activityInfo.name }).contains(BootReceiver::class.java.name)
    }

    private companion object {
        const val TRACK_ID = "track-1"
    }
}
