package et.alpha.meret.feature.settings.onboarding

import androidx.annotation.StringRes
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.SkippableStep
import et.alpha.meret.feature.settings.R

/**
 * What one permission screen says, and which step its "Not now" skips. Null [skips] means
 * the screen has no "Not now": location is the one step the app cannot work without.
 *
 * Kept out of the composable so the per-step text is checked on the JVM.
 */
internal data class OnboardingCopy(
    @param:StringRes val title: Int,
    @param:StringRes val body: Int,
    @param:StringRes val action: Int,
    val skips: SkippableStep?,
)

/** Null for the OEM step and Done, which are not system permissions. */
internal fun onboardingCopyFor(step: OnboardingStep): OnboardingCopy? = when (step) {
    is OnboardingStep.Location -> {
        OnboardingCopy(
            title = R.string.onboarding_location_title,
            body =
                if (step.viaSettings) {
                    R.string.onboarding_location_body_settings
                } else {
                    R.string.onboarding_location_body
                },
            action = allowOrSettings(step.viaSettings),
            skips = null,
        )
    }

    is OnboardingStep.PreciseLocation -> {
        OnboardingCopy(
            title = R.string.onboarding_precise_title,
            body =
                if (step.viaSettings) {
                    R.string.onboarding_precise_body_settings
                } else {
                    R.string.onboarding_precise_body
                },
            action = allowOrSettings(step.viaSettings),
            skips = null,
        )
    }

    OnboardingStep.Notifications -> {
        OnboardingCopy(
            title = R.string.onboarding_notifications_title,
            body = R.string.onboarding_notifications_body,
            action = R.string.onboarding_action_allow,
            skips = SkippableStep.NOTIFICATIONS,
        )
    }

    // The rationale screen: "Continue" leads to the system's own prompt or settings page.
    is OnboardingStep.BackgroundLocation -> {
        OnboardingCopy(
            title = R.string.onboarding_background_title,
            body =
                if (step.viaSettings) {
                    R.string.onboarding_background_body_settings
                } else {
                    R.string.onboarding_background_body
                },
            action = R.string.onboarding_action_continue,
            skips = SkippableStep.BACKGROUND_LOCATION,
        )
    }

    OnboardingStep.BatteryOptimisation -> {
        OnboardingCopy(
            title = R.string.onboarding_battery_title,
            body = R.string.onboarding_battery_body,
            action = R.string.onboarding_action_allow,
            skips = SkippableStep.BATTERY_OPTIMISATION,
        )
    }

    OnboardingStep.OemAutostart, OnboardingStep.Done -> {
        null
    }
}

private fun allowOrSettings(viaSettings: Boolean): Int =
    if (viaSettings) R.string.onboarding_action_open_settings else R.string.onboarding_action_allow
