package et.alpha.meret.core.model

import et.alpha.meret.core.model.geo.LatLng

/**
 * A geotagged observation, optionally attached to the track being recorded when it was
 * captured.
 *
 * [formData] is the JSON object produced by the renderer for [formTemplateId] at
 * [formVersion]. It is stored as text rather than exploded into columns because the
 * schema is per-project and pull-only — the app must accept a template it has never seen
 * before without a migration.
 *
 * [baseVersion] is the version the edit was made against. The server compares it and
 * returns the current row on mismatch rather than overwriting, which is what turns a
 * concurrent supervisor edit into a resolvable conflict instead of silent data loss.
 */
public data class Waypoint(
    public val id: String,
    public val projectId: String,
    public val trackId: String? = null,
    public val position: LatLng,
    public val accuracyMeters: Double,
    public val altitudeMeters: Double? = null,
    public val recordedAt: Long,
    public val category: String? = null,
    public val title: String? = null,
    public val notes: String? = null,
    public val formTemplateId: String? = null,
    public val formVersion: Int? = null,
    public val formData: String? = null,
    public val baseVersion: Int = 0,
    public val syncState: SyncState = SyncState.PENDING,
    public val updatedAt: Long,
)
