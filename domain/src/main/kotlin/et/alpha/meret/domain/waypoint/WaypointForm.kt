package et.alpha.meret.domain.waypoint

import et.alpha.meret.core.model.form.FormSchema

/**
 * The form a new waypoint is filled in with: a parsed template and which one it is, so the
 * saved answers can name the template and version they were given under.
 */
public data class WaypointForm(
    public val templateId: String,
    public val version: Int,
    /** The template's own name. The sheet shows a string resource instead for bundled ones. */
    public val name: String,
    public val schema: FormSchema,
)

/** Where the waypoint sheet's form comes from. Never the network. */
public interface WaypointFormSource {
    /**
     * The form for a new waypoint, or null when the project has none (or its template cannot
     * be read, in which case the waypoint is saved without one rather than not at all).
     */
    public suspend fun currentForm(): WaypointForm?
}
