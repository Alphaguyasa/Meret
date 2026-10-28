package et.alpha.meret.core.data.location

import et.alpha.meret.core.location.LocationEngine
import et.alpha.meret.core.location.RawFix
import et.alpha.meret.core.model.geo.DevicePosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filter
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
     * The engine runs only while a track records, so live fixes arrive only then. This never
     * starts or stops it: the recording owns the engine.
     */
    public fun observePosition(): Flow<DevicePosition?>
}

public class DefaultDevicePositionRepository
    @Inject
    constructor(
        private val engine: LocationEngine,
    ) : DevicePositionRepository {
        override fun observePosition(): Flow<DevicePosition?> = flow {
            emit(cachedFix()?.toDevicePosition(isLive = false))
            emitAll(
                engine.fixes
                    .filter { it.isDrawable() }
                    .map { it.toDevicePosition(isLive = true) },
            )
        }

        private suspend fun cachedFix(): RawFix? = try {
            engine.lastKnown()?.takeIf { it.isDrawable() }
        } catch (_: SecurityException) {
            // No location permission yet: no puck, and the onboarding flow asks for it.
            null
        }
    }

private fun RawFix.isDrawable(): Boolean =
    position.isValid && accuracyMetres.isFinite() && accuracyMetres >= 0.0

private fun RawFix.toDevicePosition(isLive: Boolean): DevicePosition = DevicePosition(
    position = position,
    accuracyMetres = accuracyMetres,
    isLive = isLive,
)
