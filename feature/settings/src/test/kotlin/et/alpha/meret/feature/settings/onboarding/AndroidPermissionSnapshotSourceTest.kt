package et.alpha.meret.feature.settings.onboarding

import android.Manifest
import android.app.Application
import android.os.PowerManager
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.location.compat.OemFamily
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.core.location.compat.oemGuidanceFor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

/** Against Robolectric's shadow permissions, on the SDK 34 build (`robolectric.properties`). */
@RunWith(RobolectricTestRunner::class)
class AndroidPermissionSnapshotSourceTest {
    private val application: Application = ApplicationProvider.getApplicationContext()

    @Test
    fun `a fresh install grants nothing`() {
        val snapshot = source().read(locationPermanentlyDenied = false)

        assertThat(snapshot.sdkInt).isEqualTo(SDK)
        assertThat(snapshot.fineLocation).isFalse()
        assertThat(snapshot.coarseLocation).isFalse()
        assertThat(snapshot.notifications).isFalse()
        assertThat(snapshot.backgroundLocation).isFalse()
        assertThat(snapshot.ignoringBatteryOptimisations).isFalse()
    }

    @Test
    fun `every grant is read`() {
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        )
        shadowOf(application.getSystemService(PowerManager::class.java))
            .setIgnoringBatteryOptimizations(application.packageName, true)

        val snapshot = source().read(locationPermanentlyDenied = false)

        assertThat(snapshot.fineLocation).isTrue()
        assertThat(snapshot.coarseLocation).isTrue()
        assertThat(snapshot.notifications).isTrue()
        assertThat(snapshot.backgroundLocation).isTrue()
        assertThat(snapshot.ignoringBatteryOptimisations).isTrue()
    }

    /** Android 12+ "approximate": the picker must see coarse without fine. */
    @Test
    fun `an approximate-only grant reads as coarse without fine`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)

        val snapshot = source().read(locationPermanentlyDenied = false)

        assertThat(snapshot.fineLocation).isFalse()
        assertThat(snapshot.coarseLocation).isTrue()
    }

    /** Nothing is cached: a revoke in system settings shows on the next read. */
    @Test
    fun `each read asks the system again`() {
        val source = source()
        shadowOf(application).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        assertThat(source.read(locationPermanentlyDenied = false).notifications).isTrue()

        shadowOf(application).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)

        assertThat(source.read(locationPermanentlyDenied = false).notifications).isFalse()
    }

    @Test
    fun `the permanent-denial flag is passed through`() {
        assertThat(source().read(locationPermanentlyDenied = true).locationPermanentlyDenied)
            .isTrue()
    }

    @Test
    fun `the OEM step is required on a Transsion phone and not on stock Android`() {
        val tecno = source(oemGuidanceFor(OemFamily.TRANSSION))
        val pixel = source(OemGuidance.NotRequired)

        assertThat(tecno.read(locationPermanentlyDenied = false).oemStepRequired).isTrue()
        assertThat(pixel.read(locationPermanentlyDenied = false).oemStepRequired).isFalse()
    }

    private fun source(guidance: OemGuidance = OemGuidance.NotRequired) =
        AndroidPermissionSnapshotSource(application, guidance)

    private companion object {
        const val SDK = 34
    }
}
