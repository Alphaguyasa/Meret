package et.alpha.meret.core.common.geo

import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** A map scale bar: a round ground distance, and how long to draw it on screen. */
public data class ScaleBar(
    /** Ground distance the bar stands for, in metres: 1, 2 or 5 times a power of ten. */
    val metres: Double,
    /** Length on screen, in the same unit as the `maxLength` it was fitted to. */
    val length: Double,
)

public object ScaleBars {
    /** Round lengths, as on a paper map: a reader halves or doubles them in their head. */
    private val STEPS = doubleArrayOf(1.0, 2.0, 5.0)
    private const val BASE = 10.0

    /**
     * The longest round bar that fits in [maxLength] screen units, where one unit spans
     * [metresPerUnit] metres of ground.
     *
     * Null when the input cannot give a bar (zero, negative, NaN or infinite), and when even
     * a 1 m bar would not fit: below a metre a scale bar tells a field worker nothing.
     */
    public fun fit(
        metresPerUnit: Double,
        maxLength: Double,
    ): ScaleBar? {
        val usable =
            metresPerUnit.isFinite() && metresPerUnit > 0 && maxLength.isFinite() && maxLength > 0
        val maxMetres = if (usable) metresPerUnit * maxLength else 0.0
        if (maxMetres < 1.0) return null
        var magnitude = BASE.pow(floor(log10(maxMetres)))
        // log10 of a value a hair under a power of ten can round up to it.
        if (magnitude > maxMetres) magnitude /= BASE
        val metres = STEPS.last { it * magnitude <= maxMetres } * magnitude
        return ScaleBar(metres = metres, length = metres / metresPerUnit)
    }
}
