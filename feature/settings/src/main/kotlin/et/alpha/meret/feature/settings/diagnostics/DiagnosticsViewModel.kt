package et.alpha.meret.feature.settings.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.domain.diagnostics.DiagnosticsReportBuilder
import et.alpha.meret.domain.diagnostics.DiagnosticsSnapshot
import et.alpha.meret.domain.diagnostics.TrackingHealth
import et.alpha.meret.domain.diagnostics.TrackingStatus
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Gathers LOCATION_ENGINE.md §Diagnostics into one [DiagnosticsSnapshot]: the engine, the
 * permissions, the tracking state and health, and the phone's free space.
 *
 * Re-read every [REFRESH_MS] while the screen is visible, and at once when the recording
 * changes, so a supervisor on the phone with a worker sees a gap open and close. Nothing
 * runs while the screen is gone (`WhileSubscribed`).
 *
 * [recheckPermissions] forgets the onboarding skips and asks the app to show the flow again,
 * offered only when the flow would have something to show (see [DiagnosticsPermissions]).
 */
@HiltViewModel
class DiagnosticsViewModel
    @Inject
    constructor(
        private val controller: TrackingController,
        private val engine: LocationEngine,
        private val permissions: DiagnosticsPermissions,
        private val deviceInfo: DeviceInfoSource,
        private val timeProvider: TimeProvider,
        dispatchers: DispatcherProvider,
    ) : ViewModel() {
        private val reportBuilder = DiagnosticsReportBuilder()
        private val onboardingRequests = Channel<Unit>(Channel.BUFFERED)

        /** One item per [recheckPermissions], once the skips are cleared: show onboarding. */
        val openOnboarding: Flow<Unit> = onboardingRequests.receiveAsFlow()

        private val refreshes: Flow<Unit> =
            flow {
                while (true) {
                    emit(Unit)
                    delay(REFRESH_MS)
                }
            }

        val uiState: StateFlow<DiagnosticsUiState> =
            combine(controller.state, controller.health, refreshes) { state, health, _ ->
                val snapshot = snapshot(state, health)
                DiagnosticsUiState.Ready(
                    snapshot = snapshot,
                    report = reportBuilder.build(snapshot),
                    canRecheckPermissions = permissions.canRecheck(snapshot.permissions),
                )
            }.flowOn(dispatchers.io)
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                    initialValue = DiagnosticsUiState.Loading,
                )

        /** Forgets every onboarding skip, then asks for the onboarding flow. */
        fun recheckPermissions() {
            viewModelScope.launch {
                permissions.forgetSkips()
                onboardingRequests.send(Unit)
            }
        }

        private fun snapshot(
            state: TrackingState,
            health: TrackingHealth,
        ): DiagnosticsSnapshot {
            val device = deviceInfo.read()
            return DiagnosticsSnapshot(
                generatedAtMillis = timeProvider.nowMillis(),
                nowElapsedNanos = timeProvider.elapsedRealtimeNanos(),
                appVersionName = device.appVersionName,
                appVersionCode = device.appVersionCode,
                deviceManufacturer = device.manufacturer,
                deviceModel = device.model,
                engine = engine.capabilities.kind.name,
                permissions = permissions.read(),
                trackingStatus = state.toStatus(),
                health = health,
                freeStorageBytes = device.freeStorageBytes,
            )
        }

        private fun TrackingState.toStatus(): TrackingStatus = when (this) {
            TrackingState.Idle -> {
                TrackingStatus.IDLE
            }

            is TrackingState.Recording -> {
                if (isPaused) TrackingStatus.PAUSED else TrackingStatus.RECORDING
            }
        }

        private companion object {
            const val REFRESH_MS = 5_000L
            const val STOP_TIMEOUT_MS = 5_000L
        }
    }
