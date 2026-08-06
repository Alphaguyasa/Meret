package et.alpha.meret.core.model

public enum class AssignmentStatus(
    override val wireName: String,
) : WireNamed {
    OPEN("open"),
    IN_PROGRESS("in_progress"),
    DONE("done"),
}

/**
 * A polygon of ground allocated to one enumerator.
 *
 * Server-authoritative and pull-only, with one exception: [coveragePercent] is computed
 * on the device from walked tracks, because coverage has to be visible in the field with
 * the network off.
 */
public data class Assignment(
    public val id: String,
    public val projectId: String,
    public val assignedUserId: String,
    public val name: String,
    public val polygonGeoJson: String,
    public val dueAt: Long? = null,
    public val status: AssignmentStatus = AssignmentStatus.OPEN,
    public val coveragePercent: Double = 0.0,
)
