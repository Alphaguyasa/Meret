package et.alpha.meret.domain.measure

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.flow.Flow

/**
 * The ring being measured, kept outside the ViewModel so it survives the process being killed
 * mid-plot. Empty when nothing is in progress.
 */
public interface MeasureRingDraft {
    public val vertices: Flow<List<LatLng>>

    /** Replaces the ring with [transform] of it, atomically. An empty result forgets the ring. */
    public suspend fun update(transform: (List<LatLng>) -> List<LatLng>): AppResult<Unit>
}
