package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.datastore.di.preferencesCorruptionHandler
import et.alpha.meret.core.model.LocationProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class OnboardingStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "meret.preferences_pb")

    @Test
    fun `a fresh install has skipped nothing`() = runTest {
        assertThat(OnboardingStore(dataStore()).skippedSteps.first()).isEmpty()
    }

    @Test
    fun `skips accumulate`() = runTest {
        val store = OnboardingStore(dataStore())

        store.skip("NOTIFICATIONS")
        store.skip("OEM_AUTOSTART")
        store.skip("NOTIFICATIONS")

        assertThat(store.skippedSteps.first()).containsExactly("NOTIFICATIONS", "OEM_AUTOSTART")
    }

    /** The point of persisting: the next launch must not ask again. */
    @Test
    fun `skips survive a new process`() = runTest {
        val firstJob = Job()
        OnboardingStore(dataStore(CoroutineScope(backgroundScope.coroutineContext + firstJob)))
            .skip("OEM_AUTOSTART")
        firstJob.cancelAndJoin()

        assertThat(
            OnboardingStore(dataStore()).skippedSteps.first(),
        ).containsExactly("OEM_AUTOSTART")
    }

    @Test
    fun `a value of the wrong type reads as nothing skipped and is replaced on the next skip`() =
        runTest {
            val dataStore = dataStore()
            dataStore.edit { it[stringPreferencesKey("onboarding_skipped_steps")] = "garbage" }
            val store = OnboardingStore(dataStore)

            assertThat(store.skippedSteps.first()).isEmpty()

            store.skip("NOTIFICATIONS")
            assertThat(store.skippedSteps.first()).containsExactly("NOTIFICATIONS")
        }

    @Test
    fun `clearing the skips offers every step again and keeps other settings`() = runTest {
        val dataStore = dataStore()
        TrackingConfigStore(dataStore).pinProfile(LocationProfile.PRECISE)
        val store = OnboardingStore(dataStore)
        store.skip("NOTIFICATIONS")
        store.skip("OEM_AUTOSTART")

        store.clearSkips()

        assertThat(store.skippedSteps.first()).isEmpty()
        assertThat(TrackingConfigStore(dataStore).config.first().profile)
            .isEqualTo(LocationProfile.PRECISE)
    }

    /** The store is shared with tracking settings and the active session. */
    @Test
    fun `skipping leaves other settings in the shared store alone`() = runTest {
        val dataStore = dataStore()
        TrackingConfigStore(dataStore).pinProfile(LocationProfile.PRECISE)

        OnboardingStore(dataStore).skip("NOTIFICATIONS")

        assertThat(TrackingConfigStore(dataStore).config.first().profile)
            .isEqualTo(LocationProfile.PRECISE)
    }

    /** On the test's scheduler, never DataStore's default `Dispatchers.IO` scope (run 12). */
    private fun TestScope.dataStore(
        scope: CoroutineScope = backgroundScope,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = preferencesCorruptionHandler(),
        scope = scope,
        produceFile = { file },
    )
}
