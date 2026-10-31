package et.alpha.meret.core.data.tracking

import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.geo.TrackLines
import et.alpha.meret.core.database.dao.TrackDao
import et.alpha.meret.core.database.dao.TrackPointDao
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.model.geo.BoundingBox
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.TrackLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/** The finished tracks the map draws under the live one. */
public interface EarlierTracksRepository {
    /**
     * The finished tracks whose bounds meet [area], newest first and at most [MAX_TRACKS],
     * as display lines: simplified, outliers dropped. Updates when a track is stopped,
     * reopened or discarded. The track being recorded is never in it: it is not finished.
     */
    public fun observeEarlierTracks(area: BoundingBox): Flow<List<TrackLine>>

    public companion object {
        /**
         * The most tracks drawn at once. Thirty simplified walks are a few thousand vertices,
         * which a low-end phone draws without dropping frames; more than that, at country
         * zoom, is a tangle nobody reads.
         */
        public const val MAX_TRACKS: Int = 30
    }
}

/**
 * Reads the bounds from the `track` table, so panning never touches `track_point` for a
 * track it has already drawn. Each track's line is built once, off the main thread, and kept
 * in memory until the track changes (a reopen adds points). Nothing is written back: the raw
 * fixes and the track row stay exactly as recorded.
 *
 * Bound as a singleton (`TrackingModule`), so that the cache outlives the map screen.
 */
public class DefaultEarlierTracksRepository
    @Inject
    constructor(
        private val trackDao: TrackDao,
        private val trackPointDao: TrackPointDao,
        private val dispatchers: DispatcherProvider,
    ) : EarlierTracksRepository {
        private val cache = TrackLineCache(MAX_CACHED_LINES)

        override fun observeEarlierTracks(area: BoundingBox): Flow<List<TrackLine>> = trackDao
            .observeFinishedTracksIn(
                minLatitude = area.minLatitude,
                minLongitude = area.minLongitude,
                maxLatitude = area.maxLatitude,
                maxLongitude = area.maxLongitude,
                limit = EarlierTracksRepository.MAX_TRACKS,
            ).map { rows -> rows.map { lineOf(it) } }
            // Every batch the recording writes touches `track` and re-runs the query; the
            // earlier tracks have not changed, so the map is not handed them again.
            .distinctUntilChanged()
            .flowOn(dispatchers.default)

        private suspend fun lineOf(row: TrackEntity): TrackLine {
            val version = TrackLineCache.Version(row.updatedAt, row.pointCount)
            cache.get(row.id, version)?.let { return it }
            val points =
                trackPointDao.getTrackGeometry(row.id).map {
                    TrackLines.SegmentPoint(it.segmentIndex, LatLng(it.latitude, it.longitude))
                }
            val line = TrackLines.build(points)
            cache.put(row.id, version, line)
            return line
        }

        private companion object {
            /** Two screens' worth: panning back and forth does not rebuild lines. */
            const val MAX_CACHED_LINES = 2 * EarlierTracksRepository.MAX_TRACKS
        }
    }

/** Least recently used first out. A line is only returned for the version it was built from. */
internal class TrackLineCache(
    private val capacity: Int,
) {
    data class Version(
        val updatedAt: Long,
        val pointCount: Int,
    )

    /** In access order, so the first key is the one used longest ago. */
    private val entries =
        LinkedHashMap<String, Pair<Version, TrackLine>>(capacity, LOAD_FACTOR, true)

    @Synchronized
    fun get(
        trackId: String,
        version: Version,
    ): TrackLine? = entries[trackId]?.takeIf { it.first == version }?.second

    @Synchronized
    fun put(
        trackId: String,
        version: Version,
        line: TrackLine,
    ) {
        entries[trackId] = version to line
        if (entries.size > capacity) {
            entries.remove(entries.keys.first())
        }
    }

    private companion object {
        const val LOAD_FACTOR = 0.75f
    }
}
