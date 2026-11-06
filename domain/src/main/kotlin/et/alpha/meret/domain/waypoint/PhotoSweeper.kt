package et.alpha.meret.domain.waypoint

/**
 * Frees the space of photo files nothing will ever use: a stored photo whose id never reached
 * a written draft (the process died in that moment), a stored photo of a draft that was
 * replaced, an interrupted write, and camera captures that could not be stored.
 *
 * Only files at least a day old are considered, so nothing the sheet is working with right now
 * is touched, and a file that any photo row, saved waypoint or draft names is never deleted.
 */
public interface PhotoSweeper {
    /** Deletes the orphaned files; returns how many. Never throws, never the network. */
    public suspend fun sweep(): Int
}
