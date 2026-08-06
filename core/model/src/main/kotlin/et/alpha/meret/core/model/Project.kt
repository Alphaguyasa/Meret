package et.alpha.meret.core.model

/**
 * A body of field work: the unit that scopes tracks, waypoints, assignments and the tile
 * pack an enumerator is expected to carry.
 *
 * Server-authoritative and pull-only. [updatedAt] is the server's clock, not the
 * device's, and is the delta-pull cursor.
 */
public data class Project(
    public val id: String,
    public val orgId: String,
    public val name: String,
    public val description: String? = null,
    public val defaultFormTemplateId: String? = null,
    public val tilePackId: String? = null,
    public val startsAt: Long? = null,
    public val endsAt: Long? = null,
    public val updatedAt: Long,
    public val isArchived: Boolean = false,
)
