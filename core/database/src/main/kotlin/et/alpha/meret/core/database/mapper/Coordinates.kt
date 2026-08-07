package et.alpha.meret.core.database.mapper

import et.alpha.meret.core.model.geo.LatLng

/**
 * A coordinate is both halves or neither.
 *
 * Every nullable lat/lon pair in the schema is read through here, so a row left half-written
 * degrades to "no position" rather than to a point on the equator or the prime meridian —
 * which is the shape of bug that renders as a plausible-looking marker in the Gulf of Guinea
 * and gets believed.
 */
internal fun latLngOrNull(
    latitude: Double?,
    longitude: Double?,
): LatLng? = if (latitude != null && longitude != null) LatLng(latitude, longitude) else null
