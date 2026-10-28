package et.alpha.meret.feature.settings.diagnostics

import et.alpha.meret.core.datastore.OnboardingStore
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.OnboardingStepPicker
import et.alpha.meret.domain.onboarding.PermissionSnapshot
import et.alpha.meret.feature.settings.onboarding.PermissionSnapshotSource
import javax.inject.Inject

/**
 * The permission side of the diagnostics screen: what is granted now, and running the
 * onboarding flow again.
 *
 * A skip in onboarding is permanent, so a phone that keeps losing tracks may have a step
 * nobody will be asked about again. [canRecheck] says whether the flow, skips forgotten,
 * would show anything; on a phone whose maker needs the autostart step that is always, since
 * no API reports whether autostart is on.
 */
class DiagnosticsPermissions
    @Inject
    constructor(
        private val snapshots: PermissionSnapshotSource,
        private val onboardingStore: OnboardingStore,
    ) {
        private val picker = OnboardingStepPicker()

        // Only the onboarding dialog's result can tell a permanent denial apart.
        fun read(): PermissionSnapshot = snapshots.read(locationPermanentlyDenied = false)

        fun canRecheck(snapshot: PermissionSnapshot): Boolean =
            picker.next(snapshot) != OnboardingStep.Done

        /** Forgets every skip, so the flow offers each missing step again. */
        suspend fun forgetSkips() {
            onboardingStore.clearSkips()
        }
    }
