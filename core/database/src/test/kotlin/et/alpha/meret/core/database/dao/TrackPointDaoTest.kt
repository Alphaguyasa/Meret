package et.alpha.meret.core.database.dao

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.database.DatabaseTest
import et.alpha.meret.core.database.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

/**
 * The write path the location engine uses, and the guarantees the rest of the app leans on:
 * a batch is atomic, a replay is a no-op, and a raw fix is never touched.
 */
class TrackPointDaoTest : DatabaseTest() {
    private val trackDao: TrackDao get() = database.trackDao()
    private val pointDao: TrackPointDao get() = database.trackPointDao()

    @Before
    fun insertTrack() = runTest {
        trackDao.upsertTrack(Fixtures.track())
    }

    @Test
    fun `a batch of twenty points and the counters they imply land together`() = runTest {
        val inserted =
            pointDao.appendBatch(
                trackId = Fixtures.TRACK_ID,
                points = Fixtures.points(1..20),
                distanceDeltaMeters = 190.0,
                movingDurationDeltaMillis = 20_000L,
                updatedAt = 5_000L,
            )

        assertThat(inserted).isEqualTo(20)
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(20)

        val track = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(track.pointCount).isEqualTo(20)
        assertThat(track.distanceMeters).isWithin(TOLERANCE).of(190.0)
        assertThat(track.movingDurationMillis).isEqualTo(20_000L)
        assertThat(track.updatedAt).isEqualTo(5_000L)
    }

    @Test
    fun `replaying a batch inserts nothing and moves no counter`() = runTest {
        val batch = Fixtures.points(1..20)
        pointDao.appendBatch(Fixtures.TRACK_ID, batch, 190.0, 20_000L, 5_000L)

        val replayed = pointDao.appendBatch(Fixtures.TRACK_ID, batch, 190.0, 20_000L, 9_000L)

        assertThat(replayed).isEqualTo(0)
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(20)
        val track = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(track.pointCount).isEqualTo(20)
        assertThat(track.distanceMeters).isWithin(TOLERANCE).of(190.0)
        // A replay must not even bump the clock — nothing about the track changed.
        assertThat(track.updatedAt).isEqualTo(5_000L)
    }

    @Test
    fun `a batch that half overlaps counts only what it actually added`() = runTest {
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..10), 90.0, 10_000L, 5_000L)

        val inserted =
            pointDao.appendBatch(
                trackId = Fixtures.TRACK_ID,
                points = Fixtures.points(6..15),
                distanceDeltaMeters = 45.0,
                movingDurationDeltaMillis = 5_000L,
                updatedAt = 9_000L,
            )

        assertThat(inserted).isEqualTo(5)
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(15)
        assertThat(requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID)).pointCount).isEqualTo(15)
    }

    @Test
    fun `a point colliding on seq alone is rejected even under a fresh id`() = runTest {
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..3), 20.0, 3_000L, 5_000L)

        val duplicateSeq = Fixtures.point(seq = 2, id = "some-other-id")
        val inserted =
            pointDao.appendBatch(
                Fixtures.TRACK_ID,
                listOf(duplicateSeq),
                0.0,
                0L,
                9_000L,
            )

        assertThat(inserted).isEqualTo(0)
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(3)
    }

    @Test
    fun `an empty batch is a no-op`() = runTest {
        val inserted = pointDao.appendBatch(Fixtures.TRACK_ID, emptyList(), 10.0, 1_000L, 9_000L)

        assertThat(inserted).isEqualTo(0)
        assertThat(requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID)).updatedAt).isEqualTo(1_000L)
    }

    @Test
    fun `bounds start null, open on the first batch and widen across later ones`() = runTest {
        assertThat(requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID)).bboxMinLatitude).isNull()

        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..5), 40.0, 5_000L, 5_000L)
        val afterFirst = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(afterFirst.bboxMinLatitude).isWithin(TOLERANCE).of(9.0301)
        assertThat(afterFirst.bboxMaxLatitude).isWithin(TOLERANCE).of(9.0305)

        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(6..10), 40.0, 5_000L, 9_000L)
        val afterSecond = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(afterSecond.bboxMinLatitude).isWithin(TOLERANCE).of(9.0301)
        assertThat(afterSecond.bboxMaxLatitude).isWithin(TOLERANCE).of(9.0310)
    }

    @Test
    fun `an outlier is stored but does not stretch the bounds`() = runTest {
        val wildFix = Fixtures.point(seq = 6, latitude = 12.0, longitude = 41.0, isOutlier = true)

        pointDao.appendBatch(
            trackId = Fixtures.TRACK_ID,
            points = Fixtures.points(1..5) + wildFix,
            distanceDeltaMeters = 40.0,
            movingDurationDeltaMillis = 6_000L,
            updatedAt = 5_000L,
        )

        // Kept for audit, per CLAUDE.md invariant 2.
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(6)
        val track = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(track.pointCount).isEqualTo(6)
        assertThat(track.bboxMaxLatitude).isWithin(TOLERANCE).of(9.0305)
        assertThat(track.bboxMaxLongitude).isWithin(TOLERANCE).of(38.7405)
    }

    @Test
    fun `a rejected fix never stretches the bounds, even mid-batch`() = runTest {
        // seq 1..3 land. Then a batch re-sends seq 2 with wildly different coordinates
        // alongside genuinely new fixes: the duplicate is rejected, so its coordinates are
        // nowhere in the table and must be nowhere in the box either.
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..3), 20.0, 3_000L, 5_000L)

        val impostor = Fixtures.point(seq = 2, id = "impostor", latitude = 13.0, longitude = 42.0)
        val inserted =
            pointDao.appendBatch(
                trackId = Fixtures.TRACK_ID,
                points = listOf(impostor) + Fixtures.points(4..5),
                distanceDeltaMeters = 20.0,
                movingDurationDeltaMillis = 2_000L,
                updatedAt = 9_000L,
            )

        assertThat(inserted).isEqualTo(2)
        val track = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(track.pointCount).isEqualTo(5)
        assertThat(track.bboxMaxLatitude).isWithin(TOLERANCE).of(9.0305)
        assertThat(track.bboxMaxLongitude).isWithin(TOLERANCE).of(38.7405)
    }

    @Test
    fun `bounds follow the filtered position when the filter produced one`() = runTest {
        val filtered =
            Fixtures.point(
                seq = 1,
                latitude = 9.0400,
                longitude = 38.7500,
                filteredLatitude = 9.0310,
                filteredLongitude = 38.7410,
            )

        pointDao.appendBatch(Fixtures.TRACK_ID, listOf(filtered), 0.0, 0L, 5_000L)

        val track = requireNotNull(trackDao.getTrack(Fixtures.TRACK_ID))
        assertThat(track.bboxMaxLatitude).isWithin(TOLERANCE).of(9.0310)
        assertThat(track.bboxMaxLongitude).isWithin(TOLERANCE).of(38.7410)
    }

    @Test
    fun `the raw fix survives beside its filtered value`() = runTest {
        val filtered =
            Fixtures.point(
                seq = 1,
                latitude = 9.0400,
                longitude = 38.7500,
                filteredLatitude = 9.0310,
                filteredLongitude = 38.7410,
            )

        pointDao.appendBatch(Fixtures.TRACK_ID, listOf(filtered), 0.0, 0L, 5_000L)

        val stored = pointDao.getPoints(Fixtures.TRACK_ID).single()
        assertThat(stored.latitude).isWithin(TOLERANCE).of(9.0400)
        assertThat(stored.longitude).isWithin(TOLERANCE).of(38.7500)
        assertThat(stored.filteredLatitude).isWithin(TOLERANCE).of(9.0310)
    }

    @Test
    fun `points come back ordered by segment then sequence`() = runTest {
        val secondSegment = (11..13).map { Fixtures.point(seq = it, segmentIndex = 1) }
        val firstSegment = (1..3).map { Fixtures.point(seq = it, segmentIndex = 0) }

        pointDao.appendBatch(Fixtures.TRACK_ID, secondSegment + firstSegment, 0.0, 0L, 5_000L)

        val stored = pointDao.getPoints(Fixtures.TRACK_ID)
        assertThat(stored.map { it.segmentIndex to it.seq })
            .containsExactly(0 to 1, 0 to 2, 0 to 3, 1 to 11, 1 to 12, 1 to 13)
            .inOrder()
    }

    @Test
    fun `the drawable geometry is the effective position, outliers excluded`() = runTest {
        val filtered =
            Fixtures.point(
                seq = 1,
                latitude = 9.0400,
                longitude = 38.7500,
                filteredLatitude = 9.0310,
                filteredLongitude = 38.7410,
            )
        val wildFix = Fixtures.point(seq = 2, latitude = 12.0, longitude = 41.0, isOutlier = true)
        val plain = Fixtures.point(seq = 3)

        pointDao.appendBatch(
            Fixtures.TRACK_ID,
            listOf(filtered, wildFix, plain),
            0.0,
            0L,
            5_000L,
        )

        val geometry = pointDao.getTrackGeometry(Fixtures.TRACK_ID)

        assertThat(geometry.map { it.seq }).containsExactly(1, 3).inOrder()
        assertThat(geometry.first().latitude).isWithin(TOLERANCE).of(9.0310)
        assertThat(geometry.first().longitude).isWithin(TOLERANCE).of(38.7410)
        // The raw fix is untouched in the table; only what we draw is filtered.
        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(3)
    }

    @Test
    fun `geometry comes back ordered across segments`() = runTest {
        val second = (11..12).map { Fixtures.point(seq = it, segmentIndex = 1) }
        val first = (1..2).map { Fixtures.point(seq = it, segmentIndex = 0) }

        pointDao.appendBatch(Fixtures.TRACK_ID, second + first, 0.0, 0L, 5_000L)

        assertThat(pointDao.getTrackGeometry(Fixtures.TRACK_ID).map { it.segmentIndex to it.seq })
            .containsExactly(0 to 1, 0 to 2, 1 to 11, 1 to 12)
            .inOrder()
    }

    @Test
    fun `segment count follows the segments written, outlier-only ones included`() = runTest {
        assertThat(pointDao.observeSegmentCount(Fixtures.TRACK_ID).first()).isEqualTo(0)

        val points =
            listOf(
                Fixtures.point(seq = 1, segmentIndex = 0),
                Fixtures.point(seq = 2, segmentIndex = 0),
                Fixtures.point(seq = 3, segmentIndex = 1, isOutlier = true),
                Fixtures.point(seq = 4, segmentIndex = 2),
            )
        pointDao.appendBatch(Fixtures.TRACK_ID, points, 0.0, 0L, 5_000L)

        assertThat(pointDao.observeSegmentCount(Fixtures.TRACK_ID).first()).isEqualTo(3)
    }

    @Test
    fun `the next sequence number is derivable on a fresh track and on a resumed one`() = runTest {
        assertThat(pointDao.getMaxSeq(Fixtures.TRACK_ID)).isNull()
        assertThat(pointDao.getMaxSegmentIndex(Fixtures.TRACK_ID)).isNull()

        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..7), 0.0, 0L, 5_000L)

        assertThat(pointDao.getMaxSeq(Fixtures.TRACK_ID)).isEqualTo(7)
        assertThat(pointDao.getMaxSegmentIndex(Fixtures.TRACK_ID)).isEqualTo(0)
    }

    @Test
    fun `sync chunks page by sequence cursor`() = runTest {
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..25), 0.0, 0L, 5_000L)

        val first = pointDao.getPointsAfter(Fixtures.TRACK_ID, afterSeq = 0, limit = 10)
        val second = pointDao.getPointsAfter(Fixtures.TRACK_ID, afterSeq = first.last().seq, 10)
        val third = pointDao.getPointsAfter(Fixtures.TRACK_ID, afterSeq = second.last().seq, 10)
        val fourth = pointDao.getPointsAfter(Fixtures.TRACK_ID, afterSeq = third.last().seq, 10)

        assertThat(first.map { it.seq }).isEqualTo((1..10).toList())
        assertThat(second.map { it.seq }).isEqualTo((11..20).toList())
        assertThat(third.map { it.seq }).isEqualTo((21..25).toList())
        assertThat(fourth).isEmpty()
    }

    @Test
    fun `points from other tracks stay out of the way`() = runTest {
        trackDao.upsertTrack(Fixtures.track(id = "track-2"))
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..5), 0.0, 0L, 5_000L)
        pointDao.appendBatch("track-2", Fixtures.points(1..9, trackId = "track-2"), 0.0, 0L, 5_000L)

        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(5)
        assertThat(pointDao.countPoints("track-2")).isEqualTo(9)
    }

    @Test
    fun `deleting a track takes its points with it`() = runTest {
        pointDao.appendBatch(Fixtures.TRACK_ID, Fixtures.points(1..5), 0.0, 0L, 5_000L)

        trackDao.deleteTrack(Fixtures.TRACK_ID)

        assertThat(pointDao.countPoints(Fixtures.TRACK_ID)).isEqualTo(0)
    }

    private companion object {
        /** Coordinates are compared at roughly 1 cm. */
        const val TOLERANCE = 1e-7
    }
}
