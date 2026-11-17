package et.alpha.meret.core.location.filter

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.model.geo.LatLng

/**
 * Turns the fixes of a walked boundary into the corners of a plot.
 *
 * Measuring by walking (phase 10) runs the ordinary tracking chain and feeds every
 * [ProcessedFix] here. A fix becomes a vertex only when all of these hold:
 *
 * - the validity gate accepted it (a rejected fix has no filtered position, and a 200 m
 *   outlier must never become a corner);
 * - its reported accuracy is no worse than [maxAccuracyMetres], which is stricter than the
 *   tracking gate: a 15 m fix is fine for a distance figure that smooths over it, but as a
 *   corner it moves the area by whole square metres per metre of error;
 * - its filtered position is at least [minSpacingMetres] from the last vertex, so standing
 *   still at a corner adds one vertex rather than a cloud of them.
 *
 * The vertex is the **filtered** position. The raw fix is not touched or dropped here: the
 * track keeps every raw point, as invariant 2 in the engineering handbook requires, and the
 * vertices are a derived value that can be worked out again from it.
 *
 * Starts from [initialVertices] so a walk can carry on after the process was killed: the
 * caller keeps the vertices in storage and hands them back.
 *
 * Not thread-safe. One instance per walk, driven by the coroutine that drives the pipeline.
 */
public class PerimeterVertexPicker(
    initialVertices: List<LatLng> = emptyList(),
    private val minSpacingMetres: Double = DEFAULT_MIN_SPACING_M,
    private val maxAccuracyMetres: Double = DEFAULT_MAX_ACCURACY_M,
) {
    init {
        require(minSpacingMetres > 0.0) { "minSpacingMetres > 0: $minSpacingMetres" }
        require(maxAccuracyMetres > 0.0) { "maxAccuracyMetres > 0: $maxAccuracyMetres" }
    }

    private val picked = initialVertices.toMutableList()

    /** Every vertex so far, in walking order. Includes any that close back on the start. */
    public val vertices: List<LatLng>
        get() = picked.toList()

    /**
     * Offers one processed fix. Returns the new vertex when the fix became one, else null.
     */
    public fun offer(fix: ProcessedFix): LatLng? {
        val position = fix.point.filteredPosition ?: return null
        val usable =
            fix.wasAccepted &&
                fix.point.accuracyMeters <= maxAccuracyMetres &&
                isFarEnough(position)
        return if (usable) position.also { picked += it } else null
    }

    /**
     * The plot's ring: [vertices] without the ones at the end that came back to within
     * [minSpacingMetres] of the first. Walking a boundary ends where it began, and those last
     * few vertices are the start again, not new corners. Not closed (the first vertex is not
     * repeated), the way `SaveMeasurementUseCase` takes it.
     */
    public fun ring(): List<LatLng> {
        val first = picked.firstOrNull() ?: return emptyList()
        var end = picked.size
        while (end > 1 && GeoMath.haversineDistance(first, picked[end - 1]) < minSpacingMetres) {
            end--
        }
        return picked.subList(0, end).toList()
    }

    /** Takes back the last vertex, for an «Undo» while walking. Returns it, or null. */
    public fun undo(): LatLng? = picked.removeLastOrNull()

    public fun clear() {
        picked.clear()
    }

    private fun isFarEnough(position: LatLng): Boolean {
        val last = picked.lastOrNull() ?: return true
        return GeoMath.haversineDistance(last, position) >= minSpacingMetres
    }

    public companion object {
        /** A vertex every few metres of movement: fine enough for a field's curves. */
        public const val DEFAULT_MIN_SPACING_M: Double = 3.0

        /** Stricter than the tracking gate's 25 m; see the class KDoc. */
        public const val DEFAULT_MAX_ACCURACY_M: Double = 10.0
    }
}
