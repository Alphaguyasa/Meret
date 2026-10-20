package et.alpha.meret.feature.settings.diagnostics

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.StatFs
import androidx.core.content.pm.PackageInfoCompat

/** The app and phone facts the diagnostics report needs. Versions and sizes only. */
data class DeviceInfo(
    val appVersionName: String,
    val appVersionCode: Long,
    val manufacturer: String,
    val model: String,
    val freeStorageBytes: Long,
)

/** Reads [DeviceInfo]. Touches the file system, so call it off the main thread. */
fun interface DeviceInfoSource {
    fun read(): DeviceInfo
}

/** Free space is measured where Room and the tile packs live: the app's internal storage. */
class AndroidDeviceInfoSource(
    private val context: Context,
) : DeviceInfoSource {
    override fun read(): DeviceInfo {
        val packageInfo = packageInfo()
        return DeviceInfo(
            appVersionName = packageInfo.versionName.orEmpty(),
            appVersionCode = PackageInfoCompat.getLongVersionCode(packageInfo),
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            freeStorageBytes = StatFs(context.filesDir.path).availableBytes,
        )
    }

    private fun packageInfo(): PackageInfo {
        val packageManager = context.packageManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION") // The only overload before API 33.
            packageManager.getPackageInfo(context.packageName, 0)
        }
    }
}
