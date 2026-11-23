package et.alpha.meret.core.data.measure

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.location.filter.PerimeterVertexPicker
import et.alpha.meret.core.location.filter.ProcessedFix
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.domain.measure.MeasureRingDraft
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * One boundary being walked: the processed fixes of the recording go in, the plot's corners
 * come out and are kept in the [MeasureRingDraft], so a process killed mid-walk carries on
 * from the last corner it stored.
 *
 * The picker in memory is the source of truth while walking, and every write stores its
 * whole vertex list rather than appending to what the draft holds. So a write that fails
 * (a full disk) loses nothing: the vertex stays in memory and the next successful write
 * stores it too.
 *
 * While a walk is in progress, change the ring through [undo] and [clear], not through the
 * draft directly, or the next vertex would write the old list back.
 *
 * Not thread-safe. Drive it from the coroutine that drives the recording's pipeline.
 */
public class PerimeterWalk internal constructor(
    private val draft: MeasureRingDraft,
    initialVertices: List<LatLng>,
) {
    private val picker = PerimeterVertexPicker(initialVertices = initialVertices)

    /** Every vertex so far, in walking order. */
    public val vertices: List<LatLng>
        get() = picker.vertices

    /**
     * Offers one processed fix. When it becomes a vertex, the draft is written; the result
     * is that write's. A fix that is not a vertex writes nothing and succeeds.
     */
    public suspend fun offer(fix: ProcessedFix): AppResult<Unit> {
        picker.offer(fix) ?: return AppResult.Success(Unit)
        return store()
    }

    /** Takes back the last vertex, in memory and in the draft. */
    public suspend fun undo(): AppResult<Unit> {
        picker.undo() ?: return AppResult.Success(Unit)
        return store()
    }

    /** Forgets every vertex, in memory and in the draft. */
    public suspend fun clear(): AppResult<Unit> {
        picker.clear()
        return store()
    }

    /** The plot to save: the vertices without the ones that walked back onto the start. */
    public fun ring(): List<LatLng> = picker.ring()

    private suspend fun store(): AppResult<Unit> {
        val snapshot = picker.vertices
        return draft.update { snapshot }
    }
}

/** Starts or resumes a [PerimeterWalk] from what the draft holds. */
public class PerimeterWalkFactory
    @Inject
    internal constructor(
        private val draft: MeasureRingDraft,
    ) {
        /** A walk seeded with the draft's vertices: empty for a new walk. */
        public suspend fun resume(): PerimeterWalk = PerimeterWalk(draft, draft.vertices.first())
    }
