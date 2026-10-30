package et.alpha.meret.core.designsystem.format

import java.util.Locale

// Durations and distances as the tracking bar and the track list show them. Digits are
// Western in every language: the unit labels are translated, the numbers are not.

/** `H:MM:SS`, from zero up. A clock set back mid-track shows 0:00:00, not a negative time. */
public fun formatDuration(elapsedMillis: Long): String {
    val totalSeconds = elapsedMillis.coerceAtLeast(0L) / MILLIS_PER_SECOND
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = totalSeconds % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
}

/** A distance's number, and whether its unit is kilometres (else metres). */
public data class FormattedDistance(
    public val value: String,
    public val inKilometres: Boolean,
)

/** Whole metres below a kilometre, then kilometres to two places. */
public fun formatDistance(metres: Double): FormattedDistance = if (metres < METRES_PER_KILOMETRE) {
    FormattedDistance(metres.coerceAtLeast(0.0).toInt().toString(), inKilometres = false)
} else {
    FormattedDistance(
        String.format(Locale.ROOT, "%.2f", metres / METRES_PER_KILOMETRE),
        inKilometres = true,
    )
}

private const val MILLIS_PER_SECOND = 1_000L
private const val SECONDS_PER_MINUTE = 60L
private const val SECONDS_PER_HOUR = 3_600L
private const val METRES_PER_KILOMETRE = 1_000.0
