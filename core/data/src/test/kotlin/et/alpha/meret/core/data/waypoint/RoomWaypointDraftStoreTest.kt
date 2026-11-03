package et.alpha.meret.core.data.waypoint

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.database.entity.WaypointDraftEntity
import et.alpha.meret.domain.waypoint.WaypointDraft
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Against real Room, in memory, as `RoomWaypointRepositoryTest`. */
@RunWith(RobolectricTestRunner::class)
class RoomWaypointDraftStoreTest {
    private lateinit var database: MeretDatabase
    private lateinit var store: RoomWaypointDraftStore

    private val draft =
        WaypointDraft(
            title = "Borehole",
            categoryKey = "water_point",
            notes = "Pump broken",
            formTemplateId = "template-1",
            formVersion = 2,
            answersJson = """{"present":"yes"}""",
            numberTexts = mapOf("people" to "4.", "plots" to "-"),
            showErrors = true,
        )

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MeretDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        store =
            RoomWaypointDraftStore(
                dao = database.waypointDraftDao(),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `no draft reads as none`() = runBlocking<Unit> {
        assertThat(store.load()).isNull()
    }

    @Test
    fun `a saved draft reads back whole, under the local project`() = runBlocking<Unit> {
        assertThat(store.save(draft)).isEqualTo(AppResult.Success(Unit))

        assertThat(store.load()).isEqualTo(draft)
        val row = checkNotNull(database.waypointDraftDao().getDraft(LOCAL))
        assertThat(row.updatedAt).isEqualTo(FixedTime.nowMillis())
    }

    @Test
    fun `a second save replaces the first, it does not add a row`() = runBlocking<Unit> {
        store.save(draft)
        store.save(WaypointDraft(title = "Spring"))

        assertThat(store.load()).isEqualTo(WaypointDraft(title = "Spring"))
    }

    @Test
    fun `clear deletes the draft`() = runBlocking<Unit> {
        store.save(draft)

        assertThat(store.clear()).isEqualTo(AppResult.Success(Unit))

        assertThat(store.load()).isNull()
    }

    @Test
    fun `unreadable box texts give an empty set, the rest of the draft stays`() =
        runBlocking<Unit> {
            database.waypointDraftDao().upsertDraft(
                WaypointDraftEntity(
                    projectId = LOCAL,
                    title = "Borehole",
                    notes = "",
                    numberTexts = "not json",
                    updatedAt = 1,
                ),
            )

            val loaded = checkNotNull(store.load())

            assertThat(loaded.title).isEqualTo("Borehole")
            assertThat(loaded.numberTexts).isEmpty()
        }

    private companion object {
        val LOCAL: String = RoomNewTrackStore.LOCAL_PROJECT_ID
    }

    private object FixedTime : TimeProvider {
        override fun nowMillis(): Long = 1_761_000_000_000L

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
}
