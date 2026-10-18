package et.alpha.meret.core.location.compat

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build

/** Starts an activity. Production passes `context::startActivity`; tests pass a fake. */
public fun interface ActivityStarter {
    public fun start(intent: Intent)
}

/** What happened when [DeviceCompat.open] tried a deep link. */
public sealed interface OemLaunchResult {
    /** The OEM screen opened. */
    public data class Opened(
        public val target: SettingsTarget,
    ) : OemLaunchResult

    /** No target opened on this firmware. Show these steps instead. */
    public data class ShowManualSteps(
        public val steps: OemGuidance.ManualSteps,
    ) : OemLaunchResult
}

/**
 * Opens the OEM's autostart / background-activity screen, or says to fall back to manual
 * steps.
 *
 * Call it from a user action (a button on the onboarding screen) only: launching settings
 * screens from the background is blocked on Android 10+ and would look like the app
 * misbehaving.
 */
public class DeviceCompat(
    private val starter: ActivityStarter,
) {
    /** The guidance for the phone this is running on. */
    public fun guidanceForThisDevice(): OemGuidance =
        oemGuidanceFor(Build.MANUFACTURER, Build.BRAND)

    /**
     * Tries each target in order. An OEM activity that does not exist on this build throws
     * [ActivityNotFoundException]; one that exists but is not exported throws
     * [SecurityException]. Both mean "try the next one", never a crash.
     */
    public fun open(link: OemGuidance.DeepLink): OemLaunchResult {
        for (target in link.targets) {
            val intent =
                Intent().apply {
                    component = ComponentName(target.packageName, target.className)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            val opened =
                try {
                    starter.start(intent)
                    true
                } catch (ignored: ActivityNotFoundException) {
                    false
                } catch (ignored: SecurityException) {
                    false
                }
            if (opened) return OemLaunchResult.Opened(target)
        }
        return OemLaunchResult.ShowManualSteps(link.fallback)
    }

    public companion object {
        public fun create(context: Context): DeviceCompat = DeviceCompat(context::startActivity)
    }
}
