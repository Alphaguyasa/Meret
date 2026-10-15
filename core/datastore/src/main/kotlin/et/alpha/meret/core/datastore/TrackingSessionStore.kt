package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import et.alpha.meret.core.model.TrackingSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Persists the [TrackingSession] in progress, so a tracking service restarted by the system
 * (`START_STICKY`), or a boot receiver, finds the same track rather than opening a new one.
 *
 * ## Reading is total
 *
 * [session] emits `null` for "no active session", and it does so for *any* stored state it
 * cannot turn into a valid session: missing keys, a half-written set, a blank id, a negative
 * segment. A corrupt store must never crash the service on start, because a crash there
 * repeats on every restart and the field worker has no way out of it. Losing a corrupt session
 * costs only the resume: the points already recorded are in Room under their track id.
 *
 * ## Writes are scoped to the session that exists
 *
 * [setPaused] and [setSegmentIndex] change nothing when no session is stored. Otherwise a
 * late pause from a notification action, racing a stop, would leave a stray flag that the
 * next session silently inherits.
 */
public class TrackingSessionStore(
    private val dataStore: DataStore<Preferences>,
) {
    public val session: Flow<TrackingSession?> =
        dataStore.data.map { it.toTrackingSession() }.distinctUntilChanged()

    /** Records a new active session, replacing any previous one in full. */
    public suspend fun start(session: TrackingSession) {
        dataStore.edit { preferences ->
            preferences[Keys.TRACK_ID] = session.trackId
            preferences[Keys.SEGMENT_INDEX] = session.segmentIndex
            preferences[Keys.PAUSED] = session.isPaused
            preferences[Keys.STARTED_AT_MS] = session.startedAtMillis
            preferences[Keys.STARTED_AT_ELAPSED_NS] = session.startedAtElapsedRealtimeNanos
        }
    }

    public suspend fun setPaused(paused: Boolean) {
        dataStore.edit { preferences ->
            if (preferences.hasSession()) preferences[Keys.PAUSED] = paused
        }
    }

    public suspend fun setSegmentIndex(segmentIndex: Int) {
        require(segmentIndex >= 0) { "segmentIndex must not be negative, was $segmentIndex" }
        dataStore.edit { preferences ->
            if (preferences.hasSession()) preferences[Keys.SEGMENT_INDEX] = segmentIndex
        }
    }

    /** Ends the session. Leaves every other key in the shared store untouched. */
    public suspend fun clear() {
        dataStore.edit { preferences ->
            preferences -= Keys.TRACK_ID
            preferences -= Keys.SEGMENT_INDEX
            preferences -= Keys.PAUSED
            preferences -= Keys.STARTED_AT_MS
            preferences -= Keys.STARTED_AT_ELAPSED_NS
        }
    }

    private object Keys {
        val TRACK_ID = stringPreferencesKey("session_track_id")
        val SEGMENT_INDEX = intPreferencesKey("session_segment_index")
        val PAUSED = booleanPreferencesKey("session_paused")
        val STARTED_AT_MS = longPreferencesKey("session_started_at_ms")
        val STARTED_AT_ELAPSED_NS = longPreferencesKey("session_started_at_elapsed_ns")
    }

    private companion object {
        fun Preferences.hasSession(): Boolean = toTrackingSession() != null

        /**
         * Every key is required. A missing start time means the set was never written whole
         * — no write path leaves it so — and guessing a value would hand the service a
         * duration or an ordering anchor that is simply wrong.
         *
         * The `runCatching` covers both a missing key and an out-of-range value, which
         * `TrackingSession` rejects in its `init`. Either is a corrupt store, and "no
         * session" is the only safe reading of it.
         */
        fun Preferences.toTrackingSession(): TrackingSession? = runCatching {
            TrackingSession(
                trackId = required(Keys.TRACK_ID),
                segmentIndex = required(Keys.SEGMENT_INDEX),
                isPaused = required(Keys.PAUSED),
                startedAtMillis = required(Keys.STARTED_AT_MS),
                startedAtElapsedRealtimeNanos = required(Keys.STARTED_AT_ELAPSED_NS),
            )
        }.getOrNull()

        private fun <T> Preferences.required(key: Preferences.Key<T>): T =
            checkNotNull(this[key]) { "missing ${key.name}" }
    }
}
