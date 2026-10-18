package et.alpha.meret.core.location.compat

import et.alpha.meret.core.location.R

/**
 * What a field worker has to do so this phone's battery manager stops killing tracking.
 *
 * `LOCATION_ENGINE.md` §OEM survival sketches `DeepLink(intent, instructions)`. It is a list
 * of [SettingsTarget]s with a [DeepLink.fallback] instead: the activities are undocumented,
 * move between firmware versions, and a family usually has two or three historical names for
 * the same screen. Plain package/class pairs also keep this table testable on the JVM;
 * [DeviceCompat] builds the `Intent`s at launch time.
 */
public sealed interface OemGuidance {
    /**
     * Try [targets] in order. [instructions] is shown before launching, because the OEM
     * screen gives no hint which switch matters. If none opens, show [fallback].
     */
    public data class DeepLink(
        public val targets: List<SettingsTarget>,
        /** String resource. */
        public val instructions: Int,
        public val fallback: ManualSteps,
    ) : OemGuidance

    /** Steps the worker follows by hand, in order. String resources, in `en` and `am`. */
    public data class ManualSteps(
        public val steps: List<Int>,
    ) : OemGuidance

    /** Stock Android. The battery-optimisation exemption alone is enough. */
    public data object NotRequired : OemGuidance
}

/** An explicit activity on the OEM's own settings or security app. */
public data class SettingsTarget(
    public val packageName: String,
    public val className: String,
)

/** The guidance for this phone. See [oemFamilyFor] for how the strings are matched. */
public fun oemGuidanceFor(
    manufacturer: String,
    brand: String = "",
): OemGuidance = oemGuidanceFor(oemFamilyFor(manufacturer, brand))

public fun oemGuidanceFor(family: OemFamily): OemGuidance = when (family) {
    OemFamily.STOCK -> {
        OemGuidance.NotRequired
    }

    OemFamily.OTHER -> {
        OemGuidance.ManualSteps(listOf(R.string.oem_step_generic_battery))
    }

    else -> {
        OemGuidance.DeepLink(
            targets = TARGETS.getValue(family),
            instructions = R.string.oem_deeplink_instructions,
            fallback = OemGuidance.ManualSteps(MANUAL_STEPS.getValue(family)),
        )
    }
}

// Component names collected from dontkillmyapp.com and the firmware they were seen on. Not
// one of them is a public API, which is why every launch has a manual fallback.
private val TARGETS: Map<OemFamily, List<SettingsTarget>> =
    mapOf(
        OemFamily.TRANSSION to
            listOf(
                SettingsTarget(
                    "com.transsion.phonemaster",
                    "com.cyin.himgr.autostart.AutoStartActivity",
                ),
            ),
        OemFamily.XIAOMI to
            listOf(
                SettingsTarget(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
        OemFamily.OPPO to
            listOf(
                SettingsTarget(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
                SettingsTarget(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                ),
                SettingsTarget(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity",
                ),
            ),
        OemFamily.VIVO to
            listOf(
                SettingsTarget(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
                SettingsTarget(
                    "com.iqoo.secure",
                    "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager",
                ),
            ),
        OemFamily.HUAWEI to
            listOf(
                SettingsTarget(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
                SettingsTarget(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
                SettingsTarget(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
                ),
            ),
        OemFamily.SAMSUNG to
            listOf(
                SettingsTarget(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
                SettingsTarget(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity",
                ),
            ),
        OemFamily.ONEPLUS to
            listOf(
                SettingsTarget(
                    "com.oneplus.security",
                    "com.oneplus.security.chainlaunch.view.ChainLaunchAppListActivity",
                ),
            ),
    )

// Recents-lock is listed only for skins that honour it (HiOS, MIUI, ColorOS, OnePlus);
// elsewhere it does nothing and would cost the worker a step for no gain.
private val MANUAL_STEPS: Map<OemFamily, List<Int>> =
    mapOf(
        OemFamily.TRANSSION to
            listOf(
                R.string.oem_step_transsion_autostart,
                R.string.oem_step_transsion_battery,
                R.string.oem_step_lock_recents,
            ),
        OemFamily.XIAOMI to
            listOf(
                R.string.oem_step_xiaomi_autostart,
                R.string.oem_step_xiaomi_battery,
                R.string.oem_step_lock_recents,
            ),
        OemFamily.OPPO to
            listOf(
                R.string.oem_step_oppo_background,
                R.string.oem_step_lock_recents,
            ),
        OemFamily.VIVO to
            listOf(
                R.string.oem_step_vivo_autostart,
                R.string.oem_step_vivo_battery,
            ),
        OemFamily.HUAWEI to listOf(R.string.oem_step_huawei_app_launch),
        OemFamily.SAMSUNG to listOf(R.string.oem_step_samsung_never_sleeping),
        OemFamily.ONEPLUS to
            listOf(
                R.string.oem_step_oneplus_battery,
                R.string.oem_step_lock_recents,
            ),
    )
