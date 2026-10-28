package et.alpha.meret.core.data.location

import et.alpha.meret.core.data.tracking.TrackingController
import et.alpha.meret.core.data.tracking.TrackingState
import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.PreviewLocationSource
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.geo.DevicePosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** Where the phone is, for the map's location puck. */
public interface DevicePositionRepository {
    /**
     * The platform's cached fix first (stale, or null when there is none or no permission),
     * then every live fix the engine delivers. Fixes with an unusable position or accuracy
     * are skipped; they still reach the track as recorded, this only decides what is drawn.
     *
     * Live fixes come from the recording's engine while a track records unpaused, and from
     * the map-only [PreviewLocationSource] otherwise (idle, or paused). The preview request
     * is registered only while this flow is collected and removed the moment a recording
     * starts, so the two never run at once. This never starts or stops the engine: the
     * recording owns it.
     */
    public fun observePosition(): Flow<DevicePosition?>
}

public class DefaultDevicePositionRepository
    @Inject
    constructor(
        private val engine: LocationEngine,
        private val preview: PreviewLocationSource,
        private val tracking: TrackingController,
    ) : DevicePositionRepository {
        @OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
        override fun observePosition(): Flow<DevicePosition?> = flow {
            emit(cachedFix()?.toDevicePosition(isLive = false))
            emitAll(
                tracking.state
                    .map { it.isEngineRunning() }
                    .distinctUntilChanged()
                    .flatMapLatest { running -> if (running) engine.fixes else previewFixes() }
                    .filter { it.isDrawable() }
                    .map { it.toDevicePosition(isLive = true) },
            )
        }

        private fun previewFixes(): Flow<RawFix> = preview.previewFixes().catch { error ->
            // No location permission: the cached fix, if any, stays on screen, grey.
            if (error !is SecurityException) throw error
        }

        private suspend fun cachedFix(): RawFix? = try {
            engine.lastKnown()?.takeIf { it.isDrawable() }
        } catch (_: SecurityException) {
            // No location permission yet: no puck, and the onboarding flow asks for it.
            null
        }
    }

/** Paused stops the engine, so a paused recording gets the preview like an idle one. */
private fun TrackingState.isEngineRunning(): Boolean = this is TrackingState.Recording && !isPaused

private fun RawFix.isDrawable(): Boolean =
    position.isValid && accuracyMetres.isFinite() && accuracyMetres >= 0.0

private fun RawFix.toDevicePosition(isLive: Boolean): DevicePosition = DevicePosition(
    position = position,
    accuracyMetres = accuracyMetres,
    isLive = isLive,
)
