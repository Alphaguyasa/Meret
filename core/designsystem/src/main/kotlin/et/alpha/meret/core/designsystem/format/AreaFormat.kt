package et.alpha.meret.core.designsystem.format

import et.alpha.meret.core.designsystem.R
import java.util.Locale

// Areas as the measuring screen and the saved list show them. As with distances, the unit
// label is translated and the digits are Western in every language. A perimeter is a
// distance: formatDistance gives it in metres or kilometres.

/** Hectares in one timad when no size is set. The real size differs by region. */
public const val DEFAULT_HECTARES_PER_TIMAD: Double = 0.25

public enum class AreaUnit {
    SQUARE_METRES,
    HECTARES,
    TIMAD,
}

/** The string resource holding this unit's translated label. */
public val AreaUnit.labelRes: Int
    get() =
        when (this) {
            AreaUnit.SQUARE_METRES -> R.string.unit_square_metres
            AreaUnit.HECTARES -> R.string.unit_hectares
            AreaUnit.TIMAD -> R.string.unit_timad
        }

/** An area's number, and the unit it is in. */
public data class FormattedArea(
    public val value: String,
    public val unit: AreaUnit,
)

/** Whole square metres below 1,000 m², then hectares to two places. */
public fun formatArea(squareMetres: Double): FormattedArea {
    val area = squareMetres.positiveOrZero()
    return if (area < HECTARES_FROM_SQUARE_METRES) {
        FormattedArea(area.toInt().toString(), AreaUnit.SQUARE_METRES)
    } else {
        FormattedArea(twoPlaces(area / SQUARE_METRES_PER_HECTARE), AreaUnit.HECTARES)
    }
}

/**
 * The area in timad, to two places. A [hectaresPerTimad] that is zero, negative or not a
 * number cannot size a timad, so [DEFAULT_HECTARES_PER_TIMAD] is used instead.
 */
public fun formatTimad(
    squareMetres: Double,
    hectaresPerTimad: Double = DEFAULT_HECTARES_PER_TIMAD,
): FormattedArea {
    val hectares = squareMetres.positiveOrZero() / SQUARE_METRES_PER_HECTARE
    val size = hectaresPerTimad.takeIf { it.isFinite() && it > 0.0 } ?: DEFAULT_HECTARES_PER_TIMAD
    return FormattedArea(twoPlaces(hectares / size), AreaUnit.TIMAD)
}

private fun Double.positiveOrZero(): Double = if (isFinite() && this > 0.0) this else 0.0

private fun twoPlaces(value: Double): String = String.format(Locale.ROOT, "%.2f", value)

private const val HECTARES_FROM_SQUARE_METRES = 1_000.0
private const val SQUARE_METRES_PER_HECTARE = 10_000.0
