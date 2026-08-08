package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.model.LocationProfile
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrackingConfigStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    @Test
    fun `an empty store reads as the defaults`() = runTest {
        val store = TrackingConfigStore(dataStore())

        assertThat(store.config.first()).isEqualTo(TrackingConfig())
    }

    @Test
    fun `a written config round-trips in full`() = runTest {
        val store = TrackingConfigStore(dataStore())
        val written =
            TrackingConfig(
                profile = LocationProfile.PRECISE,
                isProfilePinned = true,
                maxAccuracyMetres = 40.0,
                maxImpliedSpeedMetresPerSecond = 45.0,
                allowMockLocations = true,
                kalmanProcessNoiseSquareMetresPerSecond = 1.5,
                segmentGapSeconds = 90.0,
                minStepMetres = 2.0,
                stepAccuracyFactor = 0.75,
                autoPauseEnabled = false,
                autoPauseWindowMillis = 45_000L,
                autoPauseDisplacementMetres = 10.0,
                autoPauseSpeedMetresPerSecond = 0.25,
                autoResumeDisplacementMetres = 18.0,
                simplifyEpsilonMetres = 5.0,
                writeBatchSize = 50,
                writeBatchWindowMillis = 5_000L,
                economyBatteryPercent = 15,
                transitSpeedMetresPerSecond = 10.0,
            )

        store.set(written)

        // Every field, not a sample. A key typo'd in `set` but not in the reader would otherwise
        // silently revert one setting and nothing else.
        assertThat(store.config.first()).isEqualTo(written)
    }

    @Test
    fun `pinning a profile records both the profile and the pin`() = runTest {
        val store = TrackingConfigStore(dataStore())

        store.pinProfile(LocationProfile.ECONOMY)

        val config = store.config.first()
        assertThat(config.profile).isEqualTo(LocationProfile.ECONOMY)
        assertThat(config.isProfilePinned).isTrue()
    }

    /**
     * An automatic switch to Economy on low battery is not the user choosing Economy. Treating it
     * as one would silently disable automatic switching for the rest of the survey.
     */
    @Test
    fun `recording an automatic switch does not pin the profile`() = runTest {
        val store = TrackingConfigStore(dataStore())

        store.setActiveProfile(LocationProfile.ECONOMY)

        val config = store.config.first()
        assertThat(config.profile).isEqualTo(LocationProfile.ECONOMY)
        assertThat(config.isProfilePinned).isFalse()
    }

    @Test
    fun `an automatic switch does not clear an existing pin`() = runTest {
        val store = TrackingConfigStore(dataStore())
        store.pinProfile(LocationProfile.PRECISE)

        store.setActiveProfile(LocationProfile.ECONOMY)

        assertThat(store.config.first().isProfilePinned).isTrue()
    }

    @Test
    fun `unpinning hands selection back to the engine`() = runTest {
        val store = TrackingConfigStore(dataStore())
        store.pinProfile(LocationProfile.PRECISE)

        store.unpinProfile()

        val config = store.config.first()
        assertThat(config.isProfilePinned).isFalse()
        // The profile itself stays: unpinning means "you may change this", not "change it now".
        assertThat(config.profile).isEqualTo(LocationProfile.PRECISE)
    }

    @Test
    fun `auto-pause can be toggled on its own`() = runTest {
        val store = TrackingConfigStore(dataStore())

        store.setAutoPauseEnabled(false)

        assertThat(store.config.first().autoPauseEnabled).isFalse()
        assertThat(store.config.first().profile).isEqualTo(TrackingConfig().profile)
    }

    /**
     * An unrecognised wire name means the store was written by a newer build with a profile this
     * one does not have. Guessing at the nearest known profile would silently change the sampling
     * rate of a survey.
     */
    @Test
    fun `an unknown stored profile falls back to the default`() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[stringPreferencesKey("tracking_profile")] = "hyperprecise" }

        val config = TrackingConfigStore(dataStore).config.first()

        assertThat(config.profile).isEqualTo(TrackingConfig().profile)
    }

    /**
     * Per-key defaulting cannot guarantee that a *combination* of stored values satisfies
     * `TrackingConfig`'s cross-field invariants. A stored pair that does not is a corrupt store,
     * and the whole default config is the only safe reading of it — the alternative is the
     * tracking service throwing on every start, which would be unrecoverable in the field.
     */
    @Test
    fun `a stored value outside its range falls back to the defaults`() = runTest {
        val dataStore = dataStore()
        dataStore.edit { it[doublePreferencesKey("tracking_max_accuracy_m")] = 5_000.0 }

        assertThat(TrackingConfigStore(dataStore).config.first()).isEqualTo(TrackingConfig())
    }

    @Test
    fun `a stored pair breaking the auto-pause invariant falls back to the defaults`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[doublePreferencesKey("tracking_auto_pause_displacement_m")] = 30.0
            it[doublePreferencesKey("tracking_auto_resume_displacement_m")] = 10.0
        }

        assertThat(TrackingConfigStore(dataStore).config.first()).isEqualTo(TrackingConfig())
    }

    /**
     * The reason every field gets its own key rather than one JSON blob: a field the store has
     * never heard of reads as absent and falls back, leaving the rest intact. With a blob, a shape
     * change makes the whole config unreadable and a carefully loosened accuracy gate reverts
     * mid-survey.
     */
    @Test
    fun `an unrelated key in the store is ignored`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[doublePreferencesKey("tracking_max_accuracy_m")] = 40.0
            it[stringPreferencesKey("something_a_later_release_added")] = "whatever"
        }

        assertThat(TrackingConfigStore(dataStore).config.first().maxAccuracyMetres).isEqualTo(40.0)
    }

    @Test
    fun `changes are observed by an existing collector`() = runTest {
        val store = TrackingConfigStore(dataStore())

        assertThat(store.config.first().profile).isEqualTo(LocationProfile.STANDARD)
        store.pinProfile(LocationProfile.TRANSIT)

        assertThat(store.config.first().profile).isEqualTo(LocationProfile.TRANSIT)
    }

    /**
     * A real `PreferenceDataStoreFactory` over a temp file rather than a fake, because the things
     * worth testing here are the key names and the fallback behaviour — both of which a fake map
     * would let me get wrong in exactly the same way in the test and in the code.
     */
    private fun dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        // A path, not a created file: DataStore writes it itself, and `newFile` would leave a
        // zero-byte file behind for the serializer to interpret.
        produceFile = { File(temporaryFolder.root, "tracking_config.preferences_pb") },
    )
}
