package et.alpha.meret.core.database

import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.test.runTest
import org.junit.Test

/**
 * What is actually in the column.
 *
 * Every DAO test would pass just as well if enums were stored as ordinals, because the
 * converter would be symmetric either way. This reads the raw SQLite value instead: the
 * strings here are the contract `DATA_MODEL.md` shares with Postgres, and a hand-written
 * `WHERE sync_state = 'pending'` in a DAO depends on them.
 */
class EnumStorageTest : DatabaseTest() {
    @Test
    fun `enum columns hold their wire string`() = runTest {
        database.trackDao().upsertTrack(
            Fixtures.track(status = TrackStatus.PAUSED),
        )

        val cursor =
            database.openHelper.readableDatabase.query(
                "SELECT status, sync_state FROM track WHERE id = ?",
                arrayOf(Fixtures.TRACK_ID),
            )

        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getString(0)).isEqualTo("paused")
            assertThat(it.getString(1)).isEqualTo("pending")
        }
    }

    @Test
    fun `booleans are stored as integers and read back as booleans`() = runTest {
        database.trackDao().upsertTrack(Fixtures.track())
        database.trackPointDao().appendBatch(
            trackId = Fixtures.TRACK_ID,
            points = listOf(Fixtures.point(seq = 1, isOutlier = true)),
            distanceDeltaMeters = 0.0,
            movingDurationDeltaMillis = 0L,
            updatedAt = 5_000L,
        )

        val cursor =
            database.openHelper.readableDatabase.query(
                "SELECT is_outlier, is_mock FROM track_point",
            )

        cursor.use {
            assertThat(it.moveToFirst()).isTrue()
            assertThat(it.getInt(0)).isEqualTo(1)
            assertThat(it.getInt(1)).isEqualTo(0)
        }
        assertThat(
            database
                .trackPointDao()
                .getPoints(Fixtures.TRACK_ID)
                .single()
                .isOutlier,
        ).isTrue()
    }
}
