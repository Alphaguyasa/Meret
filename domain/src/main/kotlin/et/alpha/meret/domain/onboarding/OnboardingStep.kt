package et.alpha.meret.domain.onboarding

/**
 * What the device currently grants, read fresh every time the onboarding screen resumes.
 *
 * Nothing here is cached: the worker can revoke any of it from system settings between two
 * screens, and a stale "granted" would skip a step that tracking still needs.
 */
public data class PermissionSnapshot(
    /** `Build.VERSION.SDK_INT`. */
    public val sdkInt: Int,
    public val fineLocation: Boolean,
    public val coarseLocation: Boolean,
    /**
     * The system will no longer show the location dialog (denied twice, or "don't ask
     * again"). Only the app's settings page can grant it now.
     */
    public val locationPermanentlyDenied: Boolean = false,
    /** `POST_NOTIFICATIONS`. Ignored below API 33, where it does not exist. */
    public val notifications: Boolean = false,
    /** `ACCESS_BACKGROUND_LOCATION`. Ignored below API 29, where it does not exist. */
    public val backgroundLocation: Boolean = false,
    /** `PowerManager.isIgnoringBatteryOptimizations` for this package. */
    public val ignoringBatteryOptimisations: Boolean = false,
    /** False on stock Android, where `DeviceCompat` returns `NotRequired`. */
    public val oemStepRequired: Boolean = false,
)

/**
 * One screen of the permission flow.
 *
 * Location is the only step that cannot be skipped: without it there is no app. Every other
 * step makes tracking more reliable but is the worker's call, so a skipped step is not
 * asked again in the same flow (see [OnboardingStepPicker]).
 */
public sealed interface OnboardingStep {
    /**
     * Ask for fine and coarse location together. [viaSettings] when the dialog will no
     * longer appear, so the screen must send the worker to the app's settings page.
     */
    public data class Location(
        public val viaSettings: Boolean,
    ) : OnboardingStep

    /**
     * Android 12+ lets the worker grant "approximate" only. That is 1–3 km, useless for a
     * field boundary, so the screen explains why and asks for precise again.
     */
    public data class PreciseLocation(
        public val viaSettings: Boolean,
    ) : OnboardingStep

    /** Android 13+. Without it the recording notification, with Pause and Stop, is hidden. */
    public data object Notifications : OnboardingStep

    /**
     * "Allow all the time". Always preceded by a rationale screen. [viaSettings] from
     * Android 11, where the system no longer offers the option in a dialog.
     */
    public data class BackgroundLocation(
        public val viaSettings: Boolean,
    ) : OnboardingStep

    /** `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`. */
    public data object BatteryOptimisation : OnboardingStep

    /** The OEM autostart / background-activity screen from `DeviceCompat`. */
    public data object OemAutostart : OnboardingStep

    public data object Done : OnboardingStep
}

/** The steps a worker may skip. Location is not among them. */
public enum class SkippableStep {
    NOTIFICATIONS,
    BACKGROUND_LOCATION,
    BATTERY_OPTIMISATION,
    OEM_AUTOSTART,
}
