package et.alpha.meret.domain.measure

import et.alpha.meret.core.common.geo.GeoMath
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.TrackNames
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.RingGeoJson

/**
 * Saves a plot measured by tapping its corners or walking its boundary.
 *
 * The ring must have at least [MIN_DISTINCT_VERTICES] distinct valid vertices and enclose at
 * least [MIN_AREA_SQUARE_METRES]; otherwise nothing is written and the save is refused with
 * [AppError.Invalid] naming [FIELD_VERTICES]. A smaller area is corners on one line or a slip
 * of the thumb, never a plot.
 */
public class SaveMeasurementUseCase(
    private val store: MeasurementStore,
) {
    /** [vertices] in order, closed or open. The new measurement's id once it is in Room. */
    public suspend operator fun invoke(
        vertices: List<LatLng>,
        method: MeasurementMethod,
        name: String = "",
    ): AppResult<String> {
        val ring =
            if (vertices.size > 1 && vertices.first() == vertices.last()) {
                vertices.dropLast(1)
            } else {
                vertices
            }
        val usable = ring.all { it.isValid } && ring.toSet().size >= MIN_DISTINCT_VERTICES
        val area = if (usable) GeoMath.ellipsoidalPolygonArea(ring) else 0.0
        return if (area >= MIN_AREA_SQUARE_METRES) {
            store.save(
                NewMeasurement(
                    name = TrackNames.normalise(name).orEmpty(),
                    verticesGeoJson = RingGeoJson.encode(ring),
                    areaSquareMetres = area,
                    perimeterMetres = GeoMath.polygonPerimeter(ring),
                    method = method,
                ),
            )
        } else {
            AppResult.Failure(AppError.Invalid(setOf(FIELD_VERTICES)))
        }
    }

    public companion object {
        public const val FIELD_VERTICES: String = "vertices"
        public const val MIN_DISTINCT_VERTICES: Int = 3
        public const val MIN_AREA_SQUARE_METRES: Double = 1.0
    }
}
