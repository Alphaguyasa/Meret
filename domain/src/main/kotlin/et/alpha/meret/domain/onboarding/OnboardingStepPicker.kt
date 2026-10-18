package et.alpha.meret.domain.onboarding

/**
 * Picks the next permission step, in the order `SESSION_PROMPTS.md` gives: location →
 * notifications → background location → battery optimisation → OEM autostart.
 *
 * Pure, so the one piece of the flow that varies by API level is tested per level on the
 * JVM. The screen calls it on every resume with a fresh [PermissionSnapshot]; a step the
 * worker completed in system settings is then simply not picked again.
 *
 * Order is deliberate. Android 11+ refuses to ask for background location before foreground
 * location is granted, and asks for it only through settings, so it comes after the
 * dialogs that can be answered in place. OEM autostart comes last because on Transsion and
 * Xiaomi it opens another app, and a worker who does not come back should already have
 * everything else.
 */
public class OnboardingStepPicker {
    /**
     * [skipped] holds the steps the worker skipped in this flow, and [SkippableStep.OEM_AUTOSTART]
     * once they come back from the OEM screen: no API reports whether autostart is on, so
     * returning from it is the only "done" there is.
     */
    public fun next(
        snapshot: PermissionSnapshot,
        skipped: Set<SkippableStep> = emptySet(),
    ): OnboardingStep = with(snapshot) {
        when {
            !fineLocation && !coarseLocation -> {
                OnboardingStep.Location(locationPermanentlyDenied)
            }

            // Below API 31 fine and coarse are granted together, so coarse-only means fine was
            // denied on its own; ask for location again rather than explain "approximate".
            !fineLocation && sdkInt < API_S -> {
                OnboardingStep.Location(locationPermanentlyDenied)
            }

            !fineLocation -> {
                OnboardingStep.PreciseLocation(locationPermanentlyDenied)
            }

            needs(SkippableStep.NOTIFICATIONS, skipped) -> {
                OnboardingStep.Notifications
            }

            needs(SkippableStep.BACKGROUND_LOCATION, skipped) -> {
                OnboardingStep.BackgroundLocation(viaSettings = sdkInt >= API_R)
            }

            needs(SkippableStep.BATTERY_OPTIMISATION, skipped) -> {
                OnboardingStep.BatteryOptimisation
            }

            needs(SkippableStep.OEM_AUTOSTART, skipped) -> {
                OnboardingStep.OemAutostart
            }

            else -> {
                OnboardingStep.Done
            }
        }
    }

    private fun PermissionSnapshot.needs(
        step: SkippableStep,
        skipped: Set<SkippableStep>,
    ): Boolean {
        if (step in skipped) return false
        return when (step) {
            SkippableStep.NOTIFICATIONS -> sdkInt >= API_TIRAMISU && !notifications
            SkippableStep.BACKGROUND_LOCATION -> sdkInt >= API_Q && !backgroundLocation
            SkippableStep.BATTERY_OPTIMISATION -> !ignoringBatteryOptimisations
            SkippableStep.OEM_AUTOSTART -> oemStepRequired
        }
    }

    private companion object {
        const val API_Q = 29
        const val API_R = 30
        const val API_S = 31
        const val API_TIRAMISU = 33
    }
}
