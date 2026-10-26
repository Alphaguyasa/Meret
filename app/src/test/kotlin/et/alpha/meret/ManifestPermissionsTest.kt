package et.alpha.meret

import android.app.Application
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The merged manifest asks for the permissions the app needs and nothing a library slipped
 * in. A new library permission fails here, so adding one is a decision, not an accident.
 *
 * A plain [Application] stands in for the Hilt one: only the manifest is under test.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ManifestPermissionsTest {
    private val requested: List<String> by lazy {
        val context = RuntimeEnvironment.getApplication()
        val info =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS,
            )
        // androidx.core declares one signature permission in the app's own namespace.
        info.requestedPermissions.orEmpty().filterNot { it.startsWith(context.packageName + ".") }
    }

    @Test
    fun `only the expected permissions are requested`() {
        assertThat(requested).containsExactlyElementsIn(EXPECTED)
    }

    @Test
    fun `MapLibre's Wi-Fi state permission is removed`() {
        assertThat(requested).doesNotContain("android.permission.ACCESS_WIFI_STATE")
    }

    private companion object {
        val EXPECTED =
            listOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
                "android.permission.ACCESS_BACKGROUND_LOCATION",
                "android.permission.INTERNET",
                "android.permission.ACCESS_NETWORK_STATE",
                "android.permission.POST_NOTIFICATIONS",
                "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
                "android.permission.FOREGROUND_SERVICE",
                "android.permission.FOREGROUND_SERVICE_LOCATION",
                "android.permission.WAKE_LOCK",
                "android.permission.RECEIVE_BOOT_COMPLETED",
            )
    }
}
