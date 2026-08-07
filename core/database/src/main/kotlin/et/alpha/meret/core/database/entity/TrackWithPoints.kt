package et.alpha.meret.core.database.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * A track and every fix recorded for it, loaded in one transaction so the denormalised
 * counters on [track] can never disagree with the [points] beside them.
 *
 * [points] arrives in whatever order SQLite finds convenient — `@Relation` has no `ORDER
 * BY`. In practice the query rides the `(track_id, seq)` index and comes back sorted, but
 * that is an implementation detail of the planner and not something to draw a line from.
 * The mapper sorts explicitly; nothing should consume this type without doing the same.
 */
public data class TrackWithPoints(
    @Embedded
    public val track: TrackEntity,
    @Relation(parentColumn = "id", entityColumn = "track_id")
    public val points: List<TrackPointEntity>,
)
