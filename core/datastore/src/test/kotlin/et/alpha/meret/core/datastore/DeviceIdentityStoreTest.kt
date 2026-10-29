package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.datastore.di.preferencesCorruptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DeviceIdentityStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "meret.preferences_pb")

    @Test
    fun `the first call makes the id and later calls keep it`() = runTest {
        val store = DeviceIdentityStore(dataStore(backgroundScope))
        var made = 0

        val first = store.deviceId { "device-${++made}" }
        val second = store.deviceId { "device-${++made}" }

        assertThat(first).isEqualTo("device-1")
        assertThat(second).isEqualTo("device-1")
        assertThat(made).isEqualTo(1)
    }

    @Test
    fun `the id survives a new process`() = runTest {
        val firstProcess = Job()
        DeviceIdentityStore(dataStore(CoroutineScope(coroutineContext + firstProcess)))
            .deviceId { "device-1" }
        firstProcess.cancelAndJoin()

        val again = DeviceIdentityStore(dataStore(backgroundScope)).deviceId { "device-2" }

        assertThat(again).isEqualTo("device-1")
    }

    @Test
    fun `a value of the wrong type is replaced`() = runTest {
        val dataStore = dataStore(backgroundScope)
        dataStore.edit { it[intPreferencesKey("device_id")] = 7 }

        assertThat(DeviceIdentityStore(dataStore).deviceId { "device-1" }).isEqualTo("device-1")
    }

    private fun dataStore(scope: CoroutineScope): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            corruptionHandler = preferencesCorruptionHandler(),
            scope = scope,
            produceFile = { file },
        )
}
