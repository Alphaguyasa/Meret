package et.alpha.meret.feature.waypoint

import et.alpha.meret.domain.waypoint.PhotoStore
import et.alpha.meret.domain.waypoint.PhotoSweeper
import javax.inject.Inject

/**
 * The sheet's photo files: storing, reading back and deleting them ([PhotoStore]), and
 * sweeping the ones nothing names any more ([PhotoSweeper]). One dependency of
 * [WaypointCaptureViewModel] rather than two.
 */
class WaypointPhotoFiles
    @Inject
    constructor(
        store: PhotoStore,
        private val sweeper: PhotoSweeper,
    ) : PhotoStore by store {
        /** See [PhotoSweeper.sweep]. */
        suspend fun sweep(): Int = sweeper.sweep()
    }
