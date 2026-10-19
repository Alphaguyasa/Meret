package et.alpha.meret.feature.settings.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.datastore.OnboardingStore
import et.alpha.meret.core.location.compat.OemGuidance
import et.alpha.meret.core.location.compat.OemLaunchResult
import et.alpha.meret.domain.onboarding.OnboardingStep
import et.alpha.meret.domain.onboarding.OnboardingStepPicker
import et.alpha.meret.domain.onboarding.SkippableStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Drives the permission onboarding flow: which step to show, given what the device grants
 * now and what the worker has skipped.
 *
 * The order itself lives in [OnboardingStepPicker]. This class only feeds it: a fresh
 * [PermissionSnapshotSource] read on every [onResume], the skips persisted in
 * [OnboardingStore], and the two facts no API reports, a permanent location denial and a
 * return from the OEM screen.
 */
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val store: OnboardingStore,
        private val snapshots: PermissionSnapshotSource,
        private val oemGuidance: OemGuidance,
    ) : ViewModel() {
        private val picker = OnboardingStepPicker()
        private val screen = MutableStateFlow(ScreenState())

        val uiState: StateFlow<OnboardingUiState> =
            combine(store.skippedSteps, screen) { stored, state ->
                val snapshot = snapshots.read(state.locationPermanentlyDenied)
                picker.next(snapshot, stored.toSkippableSteps() + state.skipped).toUiState(state)
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = OnboardingUiState.Loading,
            )

        /**
         * Call on every `ON_RESUME`. The worker may have granted or revoked anything in system
         * settings while away. Coming back from the OEM screen counts as that step done: no
         * API reports whether autostart is on.
         */
        fun onResume() {
            if (screen.value.oemScreenOpened) {
                screen.update { it.copy(oemScreenOpened = false) }
                skip(SkippableStep.OEM_AUTOSTART)
            }
            screen.update { it.copy(reads = it.reads + 1) }
        }

        /**
         * After the location dialog returns. [permanentlyDenied] is "denied, and
         * `shouldShowRequestPermissionRationale` is false": the dialog will not appear again
         * and the screen must send the worker to the app's settings page instead.
         */
        fun onLocationResult(permanentlyDenied: Boolean) {
            screen.update {
                it.copy(locationPermanentlyDenied = permanentlyDenied, reads = it.reads + 1)
            }
        }

        /** After any other dialog returns. The snapshot says what was granted. */
        fun onPermissionResult() {
            screen.update { it.copy(reads = it.reads + 1) }
        }

        /**
         * The worker skipped [step], or finished it where no API can tell (the OEM manual
         * steps). Applied at once and persisted, so the next launch does not ask again.
         */
        fun skip(step: SkippableStep) {
            screen.update { it.copy(skipped = it.skipped + step) }
            viewModelScope.launch { store.skip(step.name) }
        }

        /** What `DeviceCompat.open` did when the worker pressed the OEM button. */
        fun onOemLaunch(result: OemLaunchResult) {
            when (result) {
                is OemLaunchResult.Opened -> {
                    screen.update { it.copy(oemScreenOpened = true) }
                }

                is OemLaunchResult.ShowManualSteps -> {
                    screen.update { it.copy(oemManualSteps = result.steps) }
                }
            }
        }

        private fun OnboardingStep.toUiState(state: ScreenState): OnboardingUiState = when (this) {
            OnboardingStep.Done -> {
                OnboardingUiState.Done
            }

            OnboardingStep.OemAutostart -> {
                OnboardingUiState.OemAutostart(
                    guidance = oemGuidance,
                    manualSteps =
                        state.oemManualSteps ?: oemGuidance as? OemGuidance.ManualSteps,
                )
            }

            else -> {
                OnboardingUiState.Permission(this)
            }
        }

        /**
         * In-memory state of this flow. [reads] only forces a fresh snapshot. [skipped] mirrors
         * what [skip] persists, so the next step shows without waiting for the disk write.
         */
        private data class ScreenState(
            val reads: Int = 0,
            val locationPermanentlyDenied: Boolean = false,
            val oemScreenOpened: Boolean = false,
            val oemManualSteps: OemGuidance.ManualSteps? = null,
            val skipped: Set<SkippableStep> = emptySet(),
        )

        private companion object {
            const val STOP_TIMEOUT_MS = 5_000L

            /** Names written by an older or newer version that no longer match are ignored. */
            fun Set<String>.toSkippableSteps(): Set<SkippableStep> =
                mapNotNullTo(mutableSetOf()) { name ->
                    SkippableStep.entries.firstOrNull { it.name == name }
                }
        }
    }
