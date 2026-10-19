package et.alpha.meret.feature.settings.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.PowerManager
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.domain.onboarding.PermissionSnapshot

/** Reads what the device grants right now. Tests substitute a fake. */
fun interface PermissionSnapshotSource {
    /**
     * [locationPermanentlyDenied] comes from the caller: only the last permission result,
     * read with `shouldShowRequestPermissionRationale`, can tell a permanent denial from a
     * permission that was never asked for.
     */
    fun read(locationPermanentlyDenied: Boolean): PermissionSnapshot
}

/**
 * The production [PermissionSnapshotSource]. Every call asks the system again; nothing is
 * cached, because the worker can change any of it in system settings between two resumes.
 */
class AndroidPermissionSnapshotSource(
    private val context: Context,
    private val oemGuidance: OemGuidance,
) : PermissionSnapshotSource {
    override fun read(locationPermanentlyDenied: Boolean): PermissionSnapshot = PermissionSnapshot(
        sdkInt = Build.VERSION.SDK_INT,
        fineLocation = granted(Manifest.permission.ACCESS_FINE_LOCATION),
        coarseLocation = granted(Manifest.permission.ACCESS_COARSE_LOCATION),
        locationPermanentlyDenied = locationPermanentlyDenied,
        notifications =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                granted(Manifest.permission.POST_NOTIFICATIONS),
        backgroundLocation =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                granted(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        ignoringBatteryOptimisations =
            context
                .getSystemService(PowerManager::class.java)
                ?.isIgnoringBatteryOptimizations(context.packageName) == true,
        oemStepRequired = oemGuidance !is OemGuidance.NotRequired,
    )

    private fun granted(permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
}
