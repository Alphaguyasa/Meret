package et.alpha.meret.core.data.measure

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.dispatcher.DispatcherProvider
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.common.time.TimeProvider
import et.alpha.meret.core.common.uuid.UuidV7Generator
import et.alpha.meret.core.common.uuid.uuidV7TimestampMillis
import et.alpha.meret.core.data.tracking.RoomNewTrackStore
import et.alpha.meret.core.database.MeretDatabase
import et.alpha.meret.core.model.MeasurementKind
import et.alpha.meret.core.model.MeasurementMethod
import et.alpha.meret.core.model.SyncState
import et.alpha.meret.domain.measure.NewMeasurement
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
class MeasurementRepositoryTest {
    private lateinit var database: MeretDatabase
    private lateinit var repository: MeasurementRepository

    private val measurement =
        NewMeasurement(
            name = "North field",
            verticesGeoJson = """{"type":"Polygon","coordinates":[]}""",
            areaSquareMetres = 17_632.74,
            perimeterMetres = 533.31,
            method = MeasurementMethod.WALKED,
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
        repository =
            MeasurementRepository(
                measurementDao = database.measurementDao(),
                ids = UuidV7Generator(FixedTime),
                time = FixedTime,
                dispatchers = TestDispatchers(Dispatchers.IO),
            )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `a saved measurement is a pending area row under the local project`() {
        runBlocking {
            val result = repository.save(measurement)

            val id = (result as AppResult.Success).value
            val row = checkNotNull(database.measurementDao().getMeasurement(id))
            assertThat(uuidV7TimestampMillis(id)).isEqualTo(FixedTime.nowMillis())
            assertThat(row.projectId).isEqualTo(RoomNewTrackStore.LOCAL_PROJECT_ID)
            assertThat(row.name).isEqualTo("North field")
            assertThat(row.kind).isEqualTo(MeasurementKind.AREA)
            assertThat(row.verticesGeoJson).isEqualTo(measurement.verticesGeoJson)
            assertThat(row.areaSquareMeters).isEqualTo(17_632.74)
            assertThat(row.perimeterMeters).isEqualTo(533.31)
            assertThat(row.distanceMeters).isNull()
            assertThat(row.method).isEqualTo(MeasurementMethod.WALKED)
            assertThat(row.recordedAt).isEqualTo(FixedTime.nowMillis())
            assertThat(row.updatedAt).isEqualTo(FixedTime.nowMillis())
            assertThat(row.syncState).isEqualTo(SyncState.PENDING)
        }
    }

    @Test
    fun `two saves are two rows`() {
        runBlocking {
            val first = repository.save(measurement) as AppResult.Success
            val second = repository.save(measurement) as AppResult.Success

            assertThat(first.value).isNotEqualTo(second.value)
        }
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
