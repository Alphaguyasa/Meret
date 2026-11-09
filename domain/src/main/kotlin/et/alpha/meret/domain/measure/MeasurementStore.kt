package et.alpha.meret.domain.measure

import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.MeasurementMethod

/** A measured plot, checked and ready to store. An unnamed one has an empty [name]. */
public data class NewMeasurement(
    public val name: String,
    /** The ring as `RingGeoJson` text. */
    public val verticesGeoJson: String,
    public val areaSquareMetres: Double,
    public val perimeterMetres: Double,
    public val method: MeasurementMethod,
)

/** Writes measurements. Room, through `:core:data`. */
public interface MeasurementStore {
    /**
     * Writes [measurement] as a new row waiting to be synced and returns its id (a UUIDv7).
     * Nothing here touches the network.
     */
    public suspend fun save(measurement: NewMeasurement): AppResult<String>
}
