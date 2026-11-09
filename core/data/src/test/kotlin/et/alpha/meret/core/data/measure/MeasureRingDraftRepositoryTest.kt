package et.alpha.meret.core.data.measure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** Against a real preferences file: the point of this class is what is still there later. */
class MeasureRingDraftRepositoryTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val scopes = mutableListOf<CoroutineScope>()
    private val file: File by lazy { temporaryFolder.newFile("ring.preferences_pb") }

    private fun dataStore(): DataStore<Preferences> {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scopes += scope
        return PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
    }

    @After
    fun tearDown() {
        runBlocking { scopes.forEach { it.coroutineContext.job.cancelAndJoin() } }
    }

    @Test
    fun `nothing is in progress at first`() = runBlocking<Unit> {
        assertThat(MeasureRingDraftRepository(dataStore()).vertices.first()).isEmpty()
    }

    @Test
    fun `corners keep their order, and one and two corners read back as they are`() =
        runBlocking<Unit> {
            val draft = MeasureRingDraftRepository(dataStore())

            assertThat(draft.update { it + A }).isEqualTo(AppResult.Success(Unit))
            assertThat(draft.vertices.first()).containsExactly(A)

            draft.update { it + B }
            assertThat(draft.vertices.first()).containsExactly(A, B).inOrder()

            draft.update { it + C }
            assertThat(draft.vertices.first()).containsExactly(A, B, C).inOrder()
        }

    @Test
    fun `an empty ring is forgotten`() = runBlocking<Unit> {
        val draft = MeasureRingDraftRepository(dataStore())
        draft.update { listOf(A, B, C) }

        draft.update { emptyList() }

        assertThat(draft.vertices.first()).isEmpty()
    }

    @Test
    fun `the ring is still there after the process is killed`() = runBlocking<Unit> {
        val first = MeasureRingDraftRepository(dataStore())
        first.update { listOf(A, B, C) }
        scopes.single().coroutineContext.job.cancelAndJoin()

        val reopened = MeasureRingDraftRepository(dataStore())

        assertThat(reopened.vertices.first()).containsExactly(A, B, C).inOrder()
    }

    @Test
    fun `a value of the wrong type reads as no ring, and can be written over`() =
        runBlocking<Unit> {
            val dataStore = dataStore()
            dataStore.edit { it[intPreferencesKey("measure_ring_in_progress")] = 7 }
            val draft = MeasureRingDraftRepository(dataStore)

            assertThat(draft.vertices.first()).isEmpty()

            draft.update { it + A }
            assertThat(draft.vertices.first()).containsExactly(A)
        }

    private companion object {
        val A = LatLng(9.03, 38.74)
        val B = LatLng(9.03, 38.75)
        val C = LatLng(9.04, 38.75)
    }
}
