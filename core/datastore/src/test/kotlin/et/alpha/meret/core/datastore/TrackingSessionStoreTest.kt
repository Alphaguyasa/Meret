package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.common.truth.Truth.assertThat
import et.alpha.meret.core.datastore.di.preferencesCorruptionHandler
import et.alpha.meret.core.model.LocationProfile
import et.alpha.meret.core.model.TrackingSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class TrackingSessionStoreTest {
    @get:Rule
    val temporaryFolder: TemporaryFolder = TemporaryFolder()

    private val file: File get() = File(temporaryFolder.root, "meret.preferences_pb")

    @Test
    fun `an empty store has no active session`() = runTest {
        assertThat(TrackingSessionStore(dataStore()).session.first()).isNull()
    }

    @Test
    fun `a started session reads back in full`() = runTest {
        val store = TrackingSessionStore(dataStore())
        val written = SESSION.copy(segmentIndex = 3, isPaused = true)

        store.start(written)

        assertThat(store.session.first()).isEqualTo(written)
    }

    @Test
    fun `starting a session replaces the previous one`() = runTest {
        val store = TrackingSessionStore(dataStore())
        store.start(SESSION.copy(segmentIndex = 4, isPaused = true))

        val next = SESSION.copy(trackId = "0192f0a4-7c1e-7000-8000-000000000002")
        store.start(next)

        assertThat(store.session.first()).isEqualTo(next)
    }

    @Test
    fun `pause and segment changes keep the same track`() = runTest {
        val store = TrackingSessionStore(dataStore())
        store.start(SESSION)

        store.setPaused(true)
        store.setSegmentIndex(1)

        assertThat(store.session.first())
            .isEqualTo(SESSION.copy(isPaused = true, segmentIndex = 1))
    }

    @Test
    fun `clearing ends the session`() = runTest {
        val store = TrackingSessionStore(dataStore())
        store.start(SESSION)

        store.clear()

        assertThat(store.session.first()).isNull()
    }

    /** The store is shared with the tracking config; ending a track must not reset settings. */
    @Test
    fun `clearing leaves other settings in the shared store alone`() = runTest {
        val dataStore = dataStore()
        val configStore = TrackingConfigStore(dataStore)
        val sessionStore = TrackingSessionStore(dataStore)
        configStore.pinProfile(LocationProfile.PRECISE)
        sessionStore.start(SESSION)

        sessionStore.clear()

        assertThat(configStore.config.first().profile).isEqualTo(LocationProfile.PRECISE)
        assertThat(configStore.config.first().isProfilePinned).isTrue()
    }

    /**
     * A pause from the notification can race a stop. If it landed after the clear and wrote its
     * flag anyway, the next session would start out paused.
     */
    @Test
    fun `pause and segment writes without a session change nothing`() = runTest {
        val store = TrackingSessionStore(dataStore())

        store.setPaused(true)
        store.setSegmentIndex(2)
        store.start(SESSION)

        assertThat(store.session.first()).isEqualTo(SESSION)
    }

    /**
     * Process death: the service's process is killed and a new one opens the same file with a
     * fresh DataStore. The first instance's scope is cancelled first, because DataStore refuses
     * two live instances on one file — just as a dead process holds none.
     */
    @Test
    fun `a new store on the same file sees the session`() = runTest {
        val firstJob = Job()
        TrackingSessionStore(dataStore(CoroutineScope(Dispatchers.IO + firstJob)))
            .start(SESSION.copy(segmentIndex = 2, isPaused = true))
        firstJob.cancelAndJoin()

        val restarted = TrackingSessionStore(dataStore())

        assertThat(restarted.session.first())
            .isEqualTo(SESSION.copy(segmentIndex = 2, isPaused = true))
    }

    @Test
    fun `a session missing a key reads as no session`() = runTest {
        val dataStore = dataStore()
        dataStore.edit {
            it[stringPreferencesKey("session_track_id")] = SESSION.trackId
            it[intPreferencesKey("session_segment_index")] = 0
            // No paused flag and no start times: a set that was never written whole.
        }

        assertThat(TrackingSessionStore(dataStore).session.first()).isNull()
    }

    @Test
    fun `a stored value out of range reads as no session`() = runTest {
        val dataStore = dataStore()
        TrackingSessionStore(dataStore).start(SESSION)
        dataStore.edit { it[intPreferencesKey("session_segment_index")] = -1 }

        assertThat(TrackingSessionStore(dataStore).session.first()).isNull()
    }

    @Test
    fun `a blank stored track id reads as no session`() = runTest {
        val dataStore = dataStore()
        TrackingSessionStore(dataStore).start(SESSION)
        dataStore.edit { it[stringPreferencesKey("session_track_id")] = "" }

        assertThat(TrackingSessionStore(dataStore).session.first()).isNull()
    }

    @Test
    fun `a stored value of the wrong type reads as no session`() = runTest {
        val dataStore = dataStore()
        TrackingSessionStore(dataStore).start(SESSION)
        dataStore.edit {
            it.remove(longPreferencesKey("session_started_at_ms"))
            it[stringPreferencesKey("session_started_at_ms")] = "yesterday"
        }

        assertThat(TrackingSessionStore(dataStore).session.first()).isNull()
    }

    /**
     * A file cut off mid-write by a dead battery. Without the corruption handler every read and
     * every write would throw, on every service restart, with no way out for the field worker.
     */
    @Test
    fun `a corrupt file reads as no session and accepts a new one`() = runTest {
        file.writeBytes(ByteArray(CORRUPT_LENGTH) { 0xFF.toByte() })
        val store = TrackingSessionStore(dataStore())

        assertThat(store.session.first()).isNull()

        store.start(SESSION)
        assertThat(store.session.first()).isEqualTo(SESSION)
    }

    /**
     * A real `PreferenceDataStoreFactory` over a temp file, with production's corruption
     * handler, rather than a fake: the key names and the fallback behaviour are what is under
     * test, and a fake map would get them wrong the same way the code does.
     */
    private fun dataStore(scope: CoroutineScope? = null): DataStore<Preferences> =
        if (scope == null) {
            PreferenceDataStoreFactory.create(
                corruptionHandler = preferencesCorruptionHandler(),
                produceFile = { file },
            )
        } else {
            PreferenceDataStoreFactory.create(
                corruptionHandler = preferencesCorruptionHandler(),
                scope = scope,
                produceFile = { file },
            )
        }

    private companion object {
        const val CORRUPT_LENGTH = 16

        val SESSION =
            TrackingSession(
                trackId = "0192f0a4-7c1e-7000-8000-000000000001",
                startedAtMillis = 1_760_500_000_000L,
                startedAtElapsedRealtimeNanos = 5_000_000_000L,
            )
    }
}
