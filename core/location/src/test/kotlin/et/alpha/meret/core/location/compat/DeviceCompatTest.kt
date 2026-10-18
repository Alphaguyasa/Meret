package et.alpha.meret.core.location.compat

import android.content.ActivityNotFoundException
import android.content.Intent
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DeviceCompatTest {
    private val first = SettingsTarget("com.oem.security", "com.oem.security.AutoStartActivity")
    private val second = SettingsTarget("com.oem.legacy", "com.oem.legacy.StartupActivity")
    private val fallback = OemGuidance.ManualSteps(listOf(R.string.oem_step_generic_battery))
    private val link =
        OemGuidance.DeepLink(
            targets = listOf(first, second),
            instructions = R.string.oem_deeplink_instructions,
            fallback = fallback,
        )

    @Test
    fun `opens the first target that exists`() {
        val started = mutableListOf<Intent>()
        val compat = DeviceCompat { started += it }

        val result = compat.open(link)

        assertThat(result).isEqualTo(OemLaunchResult.Opened(first))
        assertThat(started).hasSize(1)
        assertThat(started.single().component?.packageName).isEqualTo(first.packageName)
        assertThat(started.single().component?.className).isEqualTo(first.className)
    }

    @Test
    fun `a missing activity moves on to the next target`() {
        val compat =
            DeviceCompat { intent ->
                if (intent.component?.packageName == first.packageName) {
                    throw ActivityNotFoundException()
                }
            }

        assertThat(compat.open(link)).isEqualTo(OemLaunchResult.Opened(second))
    }

    @Test
    fun `when no target exists the manual steps are shown`() {
        val compat = DeviceCompat { throw ActivityNotFoundException() }

        assertThat(compat.open(link)).isEqualTo(OemLaunchResult.ShowManualSteps(fallback))
    }

    /** Some firmware ships the activity but does not export it. */
    @Test
    fun `a non-exported activity falls back instead of crashing`() {
        val compat = DeviceCompat { throw SecurityException("Permission Denial") }

        assertThat(compat.open(link)).isEqualTo(OemLaunchResult.ShowManualSteps(fallback))
    }
}
