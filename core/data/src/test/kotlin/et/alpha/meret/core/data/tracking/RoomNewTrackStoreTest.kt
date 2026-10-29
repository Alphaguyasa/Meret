package et.alpha.meret.core.data.tracking

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.common.uuid.uuidV7TimestampMillis
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.datastore.DeviceIdentityStore
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.core.model.TrackStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Against real Room (in memory) and a real preferences file, on real threads, as in
 * `DefaultTrackingControllerTest`.
 */
@RunWith(RobolectricTestRunner::class)
class RoomNewTrackStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private lateinit var database: MeretDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var store: RoomNewTrackStore

    @Before
    fun setUp() {
        database =
            Room
                .inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    MeretDatabase::class.java,
                ).allowMainThreadQueries()
                .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val dataStore =
            PreferenceDataStoreFactory.create(
                scope = dataStoreScope,
                produceFile = { File(temporaryFolder.root, "meret.preferences_pb") },
            )
        store =
            RoomNewTrackStore(
                trackDao = database.trackDao(),
                identity = DeviceIdentityStore(dataStore),
                ids = UuidV7Generator(FixedTime),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        dataStoreScope.cancel()
        database.close()
    }

    @Test
    fun `a new track is an open, empty, unsynced row with a time-ordered id`() = runBlocking<Unit> {
        val id = (store.create() as AppResult.Success).value

        val row = checkNotNull(database.trackDao().getTrack(id))
        assertThat(row.status).isEqualTo(TrackStatus.ACTIVE)
        assertThat(row.startedAt).isEqualTo(FixedTime.nowMillis())
        assertThat(row.endedAt).isNull()
        assertThat(row.pointCount).isEqualTo(0)
        assertThat(row.syncState).isEqualTo(SyncState.PENDING)
        assertThat(row.projectId).isEqualTo(RoomNewTrackStore.LOCAL_PROJECT_ID)
        assertThat(row.userId).isEqualTo(RoomNewTrackStore.LOCAL_USER_ID)
        assertThat(uuidV7TimestampMillis(id)).isEqualTo(FixedTime.nowMillis())
    }

    @Test
    fun `tracks get their own ids and one device id`() = runBlocking<Unit> {
        val first = (store.create() as AppResult.Success).value
        val second = (store.create() as AppResult.Success).value

        val rows = listOf(first, second).map { checkNotNull(database.trackDao().getTrack(it)) }
        assertThat(first).isNotEqualTo(second)
        assertThat(rows.map { it.deviceId }.toSet()).hasSize(1)
        assertThat(rows.first().deviceId).isNotEmpty()
    }

    @Test
    fun `an empty track is discarded`() = runBlocking<Unit> {
        val id = (store.create() as AppResult.Success).value

        store.discardIfEmpty(id)

        assertThat(database.trackDao().getTrack(id)).isNull()
    }

    @Test
    fun `a track with points is never discarded`() = runBlocking<Unit> {
        val id = (store.create() as AppResult.Success).value
        val row = checkNotNull(database.trackDao().getTrack(id))
        database.trackDao().upsertTrack(row.copy(pointCount = 3))

        store.discardIfEmpty(id)
        store.discardIfEmpty("no-such-track")

        assertThat(database.trackDao().getTrack(id)).isNotNull()
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
