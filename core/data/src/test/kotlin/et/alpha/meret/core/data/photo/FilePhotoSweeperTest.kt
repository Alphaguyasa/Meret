package et.alpha.meret.core.data.photo

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.WaypointDraftEntity
import et.alpha.meret.core.database.entity.WaypointEntity
import et.alpha.meret.core.database.entity.WaypointPhotoEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/** Real files in the app's own directories, and real Room in memory. */
@RunWith(RobolectricTestRunner::class)
class FilePhotoSweeperTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var database: MeretDatabase
    private lateinit var sweeper: FilePhotoSweeper
    private lateinit var photos: File
    private lateinit var captures: File

    @Before
    fun setUp() {
        photos = File(context.filesDir, JpegPhotoStore.DIRECTORY)
        captures = File(context.cacheDir, FilePhotoSweeper.CAPTURE_DIRECTORY)
        listOf(photos, captures).forEach {
            it.deleteRecursively()
            it.mkdirs()
        }
        database =
            Room
                .inMemoryDatabaseBuilder(context, MeretDatabase::class.java)
                .allowMainThreadQueries()
                .build()
        sweeper =
            FilePhotoSweeper(
                context = context,
                photoDao = database.waypointPhotoDao(),
                waypointDao = database.waypointDao(),
                draftDao = database.waypointDraftDao(),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `an old photo that nothing names is deleted`() = runBlocking<Unit> {
        val orphan = photo(ID_1, ageMillis = TWO_DAYS)

        val deleted = sweeper.sweep()

        assertThat(deleted).isEqualTo(1)
        assertThat(orphan.exists()).isFalse()
    }

    @Test
    fun `a photo taken less than a day ago is kept even when nothing names it yet`() =
        runBlocking<Unit> {
            val fresh = photo(ID_1, ageMillis = ONE_HOUR)

            assertThat(sweeper.sweep()).isEqualTo(0)
            assertThat(fresh.exists()).isTrue()
        }

    @Test
    fun `a photo with a row is never deleted, however old`() = runBlocking<Unit> {
        val file = photo(ID_1, ageMillis = 400 * ONE_DAY)
        database.waypointDao().upsertWaypointWithPhotos(waypoint(), listOf(photoRow(ID_1, file)))

        assertThat(sweeper.sweep()).isEqualTo(0)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `a file a row points at by path is kept even if it is not named after the row`() =
        runBlocking<Unit> {
            val file = photo(ID_2, ageMillis = TWO_DAYS)
            val row = photoRow(ID_1, file)
            database.waypointDao().upsertWaypointWithPhotos(waypoint(), listOf(row))

            assertThat(sweeper.sweep()).isEqualTo(0)
            assertThat(file.exists()).isTrue()
        }

    @Test
    fun `a photo the open draft names is kept`() = runBlocking<Unit> {
        val file = photo(ID_1, ageMillis = TWO_DAYS)
        database.waypointDraftDao().upsertDraft(
            WaypointDraftEntity(
                projectId = "local",
                title = "",
                notes = "",
                formTemplateId = "bundled:farm_visit",
                formVersion = 1,
                formData = """{"plot_photos":["$ID_1"]}""",
                updatedAt = NOW,
            ),
        )

        assertThat(sweeper.sweep()).isEqualTo(0)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `a photo a saved waypoint's answers name is kept even without its row`() =
        runBlocking<Unit> {
            val file = photo(ID_1, ageMillis = TWO_DAYS)
            database.waypointDao().upsertWaypoint(
                waypoint(formData = """{"plot_photos":["$ID_1"]}"""),
            )

            assertThat(sweeper.sweep()).isEqualTo(0)
            assertThat(file.exists()).isTrue()
        }

    @Test
    fun `only the orphans go when kept and orphaned photos sit side by side`() =
        runBlocking<Unit> {
            val saved = photo(ID_1, ageMillis = TWO_DAYS)
            val orphan = photo(ID_2, ageMillis = TWO_DAYS)
            val fresh = photo(ID_3, ageMillis = ONE_HOUR)
            val row = photoRow(ID_1, saved)
            database.waypointDao().upsertWaypointWithPhotos(waypoint(), listOf(row))

            assertThat(sweeper.sweep()).isEqualTo(1)
            assertThat(photos.list()!!.toList()).containsExactly("$ID_1.jpg", "$ID_3.jpg")
            assertThat(orphan.exists()).isFalse()
            assertThat(fresh.exists()).isTrue()
        }

    @Test
    fun `an old interrupted write is deleted, and files the store did not make are left`() =
        runBlocking<Unit> {
            val partial = file(photos, "$ID_1.jpg.part", TWO_DAYS)
            val stranger = file(photos, "notes.txt", TWO_DAYS)
            val notAnId = file(photos, "holiday.jpg", TWO_DAYS)

            assertThat(sweeper.sweep()).isEqualTo(1)
            assertThat(partial.exists()).isFalse()
            assertThat(stranger.exists()).isTrue()
            assertThat(notAnId.exists()).isTrue()
        }

    @Test
    fun `camera captures older than a day are deleted, newer ones kept`() = runBlocking<Unit> {
        val old = file(captures, "capture-1.jpg", TWO_DAYS)
        val recent = file(captures, "capture-2.jpg", ONE_HOUR)

        assertThat(sweeper.sweep()).isEqualTo(1)
        assertThat(old.exists()).isFalse()
        assertThat(recent.exists()).isTrue()
    }

    @Test
    fun `a photo dated in the future by a clock set back is kept`() = runBlocking<Unit> {
        val file = photo(ID_1, ageMillis = -TWO_DAYS)

        assertThat(sweeper.sweep()).isEqualTo(0)
        assertThat(file.exists()).isTrue()
    }

    @Test
    fun `no photo folder yet is nothing to sweep`() = runBlocking<Unit> {
        photos.deleteRecursively()
        captures.deleteRecursively()

        assertThat(sweeper.sweep()).isEqualTo(0)
    }

    private fun photo(
        id: String,
        ageMillis: Long,
    ): File = file(photos, "$id${JpegPhotoStore.SUFFIX}", ageMillis)

    private fun file(
        directory: File,
        name: String,
        ageMillis: Long,
    ): File = File(directory, name).apply {
        writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        check(setLastModified(NOW - ageMillis))
    }

    private fun waypoint(formData: String? = null) = WaypointEntity(
        id = WAYPOINT_ID,
        projectId = "local",
        latitude = 9.0301,
        longitude = 38.7402,
        accuracyMeters = 4.5,
        recordedAt = NOW,
        formData = formData,
        updatedAt = NOW,
    )

    private fun photoRow(
        id: String,
        file: File,
    ) = WaypointPhotoEntity(
        id = id,
        waypointId = WAYPOINT_ID,
        localPath = file.path,
        width = 1600,
        height = 1200,
        bytes = file.length(),
        sha256 = "ab".repeat(32),
        capturedAt = NOW,
    )

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = NOW

        override fun elapsedRealtimeNanos(): Long = 0L
    }

    private class TestDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
        override val mainImmediate: CoroutineDispatcher = dispatcher
    }

    private companion object {
        const val NOW = 1_762_400_000_000L
        const val ONE_HOUR = 60L * 60 * 1000
        const val ONE_DAY = 24 * ONE_HOUR
        const val TWO_DAYS = 2 * ONE_DAY
        const val WAYPOINT_ID = "0190b4c2-7f00-7000-8000-0000000000aa"
        const val ID_1 = "0190b4c2-7f00-7000-8000-000000000001"
        const val ID_2 = "0190b4c2-7f00-7000-8000-000000000002"
        const val ID_3 = "0190b4c2-7f00-7000-8000-000000000003"
    }
}
