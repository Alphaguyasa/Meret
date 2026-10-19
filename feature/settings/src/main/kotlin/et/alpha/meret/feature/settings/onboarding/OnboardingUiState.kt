package et.alpha.meret.feature.settings.onboarding

import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.domain.onboarding.OnboardingStep

/** The permission onboarding screen. One state per screen, per CLAUDE.md. */
sealed interface OnboardingUiState {
    /** Before the first snapshot and the stored skips have been read. */
    data object Loading : OnboardingUiState

    /**
     * A system-permission step: location, precise location, notifications, background
     * location or battery optimisation. Never [OnboardingStep.OemAutostart] or
     * [OnboardingStep.Done], which have their own states.
     */
    data class Permission(
        val step: OnboardingStep,
    ) : OnboardingUiState

    /**
     * The OEM battery-manager screen. [manualSteps] is null while a deep link may still
     * open; it holds the steps to show once none did, or straight away for a phone with
     * no known screen.
     */
    data class OemAutostart(
        val guidance: OemGuidance,
        val manualSteps: OemGuidance.ManualSteps?,
    ) : OnboardingUiState

    /** Nothing left to ask. The screen hands over to the map. */
    data object Done : OnboardingUiState
}
