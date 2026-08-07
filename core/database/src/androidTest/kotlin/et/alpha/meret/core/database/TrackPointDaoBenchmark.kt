package et.alpha.meret.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import et.alpha.meret.core.common.time.SystemTimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.database.entity.TrackEntity
import et.alpha.meret.core.database.entity.TrackPointEntity
import et.alpha.meret.core.model.LocationProvider
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.time.Duration.Companion.minutes

/**
 * The numbers BUILD_PLAN phase 3 holds the database to.
 *
 * These run on a device, not under Robolectric, and against a **file-backed** database. An
 * in-memory measurement would clear every bar by a wide margin and tell us nothing: the
 * thing being measured is a low-end device's flash and its journal.
 *
 * Ids are real UUIDv7, because that is what production writes and key distribution is
 * exactly the sort of thing that changes B-tree insert cost.
 *
 * The generous `runTest` timeout is deliberate — a regression should fail with the number it
 * measured, not with runTest's opaque 60 s "did not run to completion".
 */
@RunWith(AndroidJUnit4::class)
class TrackPointDaoBenchmark {
    private lateinit var database: MeretDatabase
    private val ids = UuidV7Generator(SystemTimeProvider())

    @Before
    fun createDatabase() {
        context.deleteDatabase(DB_NAME)
        database = Room.databaseBuilder(context, MeretDatabase::class.java, DB_NAME).build()
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(DB_NAME)
    }

    /**
     * The insert bar is a ratio against hand-written SQLite, plus a wall-clock ceiling.
     *
     * The ratio is the part that earns its keep. This phase shipped with `@Insert` declared
     * as returning `List<Long>`, which makes Room run `SELECT changes()` and
     * `SELECT last_insert_rowid()` after every row — 800 µs/row against raw SQLite's 61.
     * A wall-clock bar alone cannot tell that apart from a slow device; a ratio can.
     */
    @Test
    fun bulkInsertStaysCloseToHandWrittenSqlite() = runTest(timeout = TEST_TIMEOUT) {
        database.trackDao().upsertTrack(track())
        val batches = (0 until POINT_COUNT).map(::point).chunked(BATCH_SIZE)

        // One enclosing transaction. 100,000 points is a bulk-ingest shape — an import or a
        // peer handoff — not the fix path, which writes ~20 at a time. Committing 200 times
        // costs ~40 ms of fsync each here and would measure the flash, not the code.
        val roomMillis =
            measureTimeMillis {
                database.withTransaction {
                    for (batch in batches) {
                        database.trackPointDao().appendBatch(
                            trackId = TRACK_ID,
                            points = batch,
                            distanceDeltaMeters = batch.size * 10.0,
                            movingDurationDeltaMillis = batch.size * 1_000L,
                            updatedAt = 1_000L,
                        )
                    }
                }
            }

        assertThat(database.trackPointDao().countPoints(TRACK_ID)).isEqualTo(POINT_COUNT)

        // Both halves must insert into an empty table, or the raw run pays for a B-tree the
        // Room run did not have and the ratio flatters Room.
        database.trackPointDao().deletePoints(TRACK_ID)
        val rawMillis = insertViaRawSqlite(POINT_COUNT)
        val ratio = roomMillis.toDouble() / rawMillis.toDouble()

        assertWithMessage(
            "Room ${roomMillis}ms (${perRow(roomMillis)}us/row) vs " +
                "raw SQLite ${rawMillis}ms (${perRow(rawMillis)}us/row), ratio $ratio",
        ).that(ratio).isLessThan(MAX_RATIO_OVER_RAW)
        assertThat(roomMillis).isLessThan(INSERT_BUDGET_MS)
    }

    /** What the map draws from. The narrow projection is the whole point — see BUILD_PLAN. */
    @Test
    fun twentyThousandPointGeometryLoadsUnderTwoHundredMilliseconds() =
        runTest(timeout = TEST_TIMEOUT) {
            seed(OBSERVE_POINT_COUNT)

            var loaded = 0
            val elapsed =
                measureTimeMillis {
                    loaded =
                        database
                            .trackPointDao()
                            .observeTrackGeometry(TRACK_ID)
                            .first()
                            .size
                }

            assertThat(loaded).isEqualTo(OBSERVE_POINT_COUNT)
            assertWithMessage("geometry load took ${elapsed}ms")
                .that(elapsed)
                .isLessThan(GEOMETRY_BUDGET_MS)
        }

    /**
     * The track detail screen, which does want every column. Looser on purpose: materialising
     * 20,000 twenty-one-column entities is inherently ~5x the projection above.
     */
    @Test
    fun twentyThousandFullEntitiesLoadWithinTheDetailScreenBudget() =
        runTest(timeout = TEST_TIMEOUT) {
            seed(OBSERVE_POINT_COUNT)

            var loaded = 0
            val elapsed =
                measureTimeMillis {
                    val emission = database.trackDao().observeTrackWithPoints(TRACK_ID).first()
                    loaded = emission?.points?.size ?: 0
                }

            assertThat(loaded).isEqualTo(OBSERVE_POINT_COUNT)
            assertWithMessage("full-entity load took ${elapsed}ms")
                .that(elapsed)
                .isLessThan(DETAIL_BUDGET_MS)
        }

    private suspend fun seed(count: Int) {
        database.trackDao().upsertTrack(track())
        database.withTransaction {
            for (batch in (0 until count).map(::point).chunked(BATCH_SIZE)) {
                database.trackPointDao().appendBatch(TRACK_ID, batch, 0.0, 0L, 1_000L)
            }
        }
    }

    /** The same rows, the same table, the same indices — with Room out of the path. */
    private fun insertViaRawSqlite(count: Int): Long {
        val db = database.openHelper.writableDatabase
        val sql =
            "INSERT OR IGNORE INTO track_point (" +
                "id, track_id, segment_index, seq, lat, lon, altitude_m, accuracy_m, " +
                "vertical_accuracy_m, bearing_deg, speed_mps, speed_accuracy_mps, provider, " +
                "satellites_used, recorded_at, elapsed_realtime_nanos, is_mock, filtered_lat, " +
                "filtered_lon, filter_variance, is_outlier" +
                ") VALUES (?,?,?,?,?,?,NULL,?,NULL,NULL,NULL,NULL,?,NULL,?,?,0,NULL,NULL,NULL,0)"

        return measureTimeMillis {
            db.beginTransaction()
            try {
                val statement = db.compileStatement(sql)
                for (seq in 0 until count) {
                    statement.clearBindings()
                    statement.bindString(1, ids.generate())
                    statement.bindString(2, TRACK_ID)
                    statement.bindLong(3, 0L)
                    statement.bindLong(4, seq.toLong())
                    statement.bindDouble(5, 9.0300 + seq * 0.00001)
                    statement.bindDouble(6, 38.7400 + seq * 0.00001)
                    statement.bindDouble(7, 6.0)
                    statement.bindString(8, LocationProvider.FUSED.wireName)
                    statement.bindLong(9, 1_000L + seq)
                    statement.bindLong(10, seq.toLong())
                    statement.executeInsert()
                }
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private fun perRow(millis: Long) = millis * 1_000.0 / POINT_COUNT

    private fun track() = TrackEntity(
        id = TRACK_ID,
        projectId = "project-1",
        deviceId = "device-1",
        userId = "user-1",
        startedAt = 1_000L,
        status = TrackStatus.ACTIVE,
        updatedAt = 1_000L,
    )

    private fun point(seq: Int) = TrackPointEntity(
        id = ids.generate(),
        trackId = TRACK_ID,
        seq = seq,
        latitude = 9.0300 + seq * 0.00001,
        longitude = 38.7400 + seq * 0.00001,
        accuracyMeters = 6.0,
        provider = LocationProvider.FUSED,
        recordedAt = 1_000L + seq * 1_000L,
        elapsedRealtimeNanos = seq.toLong() * 1_000_000_000L,
    )

    private val context: Context
        get() = InstrumentationRegistry.getInstrumentation().targetContext

    private companion object {
        const val DB_NAME = "benchmark.db"
        const val TRACK_ID = "benchmark-track"

        const val POINT_COUNT = 100_000
        const val OBSERVE_POINT_COUNT = 20_000

        /** The size a sync chunk uses, so the benchmark exercises the real batching. */
        const val BATCH_SIZE = 500

        const val INSERT_BUDGET_MS = 10_000L
        const val MAX_RATIO_OVER_RAW = 1.25

        const val GEOMETRY_BUDGET_MS = 200L

        /** Measured at ~690 ms on the reference device; the headroom is for slower hardware. */
        const val DETAIL_BUDGET_MS = 1_200L

        val TEST_TIMEOUT = 5.minutes
    }
}
