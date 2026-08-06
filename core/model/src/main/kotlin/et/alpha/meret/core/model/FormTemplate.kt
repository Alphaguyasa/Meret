package et.alpha.meret.core.model

/**
 * A versioned waypoint form definition. Pull-only.
 *
 * [schemaJson] is interpreted by the renderer at runtime; the app ships no compiled
 * knowledge of any particular project's fields. A waypoint records the [version] it was
 * captured against so an old submission still renders after the template moves on.
 */
public data class FormTemplate(
    public val id: String,
    public val projectId: String,
    public val version: Int,
    public val name: String,
    public val schemaJson: String,
    public val updatedAt: Long,
)
