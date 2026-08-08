package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import et.alpha.meret.core.model.LocationProfile
import et.alpha.meret.core.model.TrackingConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persists [TrackingConfig].
 *
 * ## Why every field is stored individually
 *
 * The obvious alternative — serialise the whole object to JSON in one key — loses the one
 * property that matters for a settings store: forward and backward tolerance. A field added
 * next release reads as absent and falls back to its default; a field removed is simply
 * ignored. With one JSON blob, a shape change makes the whole stored config unreadable, and
 * the failure mode is a field worker's carefully loosened accuracy gate silently reverting to
 * 25 m mid-survey.
 *
 * ## Reading is total
 *
 * [config] never fails and never emits nothing. A missing key falls back to the default, and
 * a *stored value that is out of range* does too, rather than propagating the
 * [IllegalArgumentException] that `TrackingConfig`'s own validation would throw. That
 * matters because these keys are reachable from a settings screen and, later, from a synced
 * project policy: a bad value must degrade to the default, not crash the tracking service on
 * every start.
 */
public class TrackingConfigStore(
    private val dataStore: DataStore<Preferences>,
) {
    public val config: Flow<TrackingConfig> = dataStore.data.map { it.toTrackingConfig() }

    /** Replaces the whole config. */
    public suspend fun set(config: TrackingConfig) {
        dataStore.edit { preferences ->
            preferences[Keys.PROFILE] = config.profile.wireName
            preferences[Keys.PROFILE_PINNED] = config.isProfilePinned
            preferences[Keys.MAX_ACCURACY] = config.maxAccuracyMetres
            preferences[Keys.MAX_IMPLIED_SPEED] = config.maxImpliedSpeedMetresPerSecond
            preferences[Keys.ALLOW_MOCK] = config.allowMockLocations
            preferences[Keys.PROCESS_NOISE] = config.kalmanProcessNoiseSquareMetresPerSecond
            preferences[Keys.SEGMENT_GAP_SECONDS] = config.segmentGapSeconds
            preferences[Keys.MIN_STEP] = config.minStepMetres
            preferences[Keys.STEP_ACCURACY_FACTOR] = config.stepAccuracyFactor
            preferences[Keys.AUTO_PAUSE_ENABLED] = config.autoPauseEnabled
            preferences[Keys.AUTO_PAUSE_WINDOW_MS] = config.autoPauseWindowMillis
            preferences[Keys.AUTO_PAUSE_DISPLACEMENT] = config.autoPauseDisplacementMetres
            preferences[Keys.AUTO_PAUSE_SPEED] = config.autoPauseSpeedMetresPerSecond
            preferences[Keys.AUTO_RESUME_DISPLACEMENT] = config.autoResumeDisplacementMetres
            preferences[Keys.SIMPLIFY_EPSILON] = config.simplifyEpsilonMetres
            preferences[Keys.WRITE_BATCH_SIZE] = config.writeBatchSize
            preferences[Keys.WRITE_BATCH_WINDOW_MS] = config.writeBatchWindowMillis
            preferences[Keys.ECONOMY_BATTERY_PERCENT] = config.economyBatteryPercent
            preferences[Keys.TRANSIT_SPEED] = config.transitSpeedMetresPerSecond
        }
    }

    /**
     * Records the profile the engine switched to on its own.
     *
     * Separate from [pinProfile] because it must not set the pinned flag: an automatic switch
     * to Economy on low battery is not the user choosing Economy, and treating it as one would
     * silently disable automatic switching for the rest of the survey.
     */
    public suspend fun setActiveProfile(profile: LocationProfile) {
        dataStore.edit { it[Keys.PROFILE] = profile.wireName }
    }

    /** Records a user's explicit profile choice, which blocks automatic switching. */
    public suspend fun pinProfile(profile: LocationProfile) {
        dataStore.edit {
            it[Keys.PROFILE] = profile.wireName
            it[Keys.PROFILE_PINNED] = true
        }
    }

    /** Hands profile selection back to the engine. */
    public suspend fun unpinProfile() {
        dataStore.edit { it[Keys.PROFILE_PINNED] = false }
    }

    public suspend fun setAutoPauseEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_PAUSE_ENABLED] = enabled }
    }

    private object Keys {
        val PROFILE = stringPreferencesKey("tracking_profile")
        val PROFILE_PINNED = booleanPreferencesKey("tracking_profile_pinned")
        val MAX_ACCURACY = doublePreferencesKey("tracking_max_accuracy_m")
        val MAX_IMPLIED_SPEED = doublePreferencesKey("tracking_max_implied_speed_mps")
        val ALLOW_MOCK = booleanPreferencesKey("tracking_allow_mock")
        val PROCESS_NOISE = doublePreferencesKey("tracking_kalman_process_noise")
        val SEGMENT_GAP_SECONDS = doublePreferencesKey("tracking_segment_gap_s")
        val MIN_STEP = doublePreferencesKey("tracking_min_step_m")
        val STEP_ACCURACY_FACTOR = doublePreferencesKey("tracking_step_accuracy_factor")
        val AUTO_PAUSE_ENABLED = booleanPreferencesKey("tracking_auto_pause_enabled")
        val AUTO_PAUSE_WINDOW_MS = longPreferencesKey("tracking_auto_pause_window_ms")
        val AUTO_PAUSE_DISPLACEMENT = doublePreferencesKey("tracking_auto_pause_displacement_m")
        val AUTO_PAUSE_SPEED = doublePreferencesKey("tracking_auto_pause_speed_mps")
        val AUTO_RESUME_DISPLACEMENT = doublePreferencesKey("tracking_auto_resume_displacement_m")
        val SIMPLIFY_EPSILON = doublePreferencesKey("tracking_simplify_epsilon_m")
        val WRITE_BATCH_SIZE = intPreferencesKey("tracking_write_batch_size")
        val WRITE_BATCH_WINDOW_MS = longPreferencesKey("tracking_write_batch_window_ms")
        val ECONOMY_BATTERY_PERCENT = intPreferencesKey("tracking_economy_battery_percent")
        val TRANSIT_SPEED = doublePreferencesKey("tracking_transit_speed_mps")
    }

    private companion object {
        val DEFAULTS = TrackingConfig()

        /**
         * Builds a config from stored preferences, falling back to [DEFAULTS] wholesale if the
         * result would be invalid.
         *
         * The `runCatching` is load-bearing rather than defensive noise: `TrackingConfig`
         * validates cross-field invariants in its `init` — auto-resume must exceed auto-pause
         * — and no amount of per-key defaulting can guarantee a combination of *stored* values
         * satisfies them. A stored pair that does not is a corrupt store, and the default
         * config is the only safe reading of it.
         */
        fun Preferences.toTrackingConfig(): TrackingConfig =
            runCatching { readTrackingConfig() }.getOrDefault(DEFAULTS)

        @Suppress("CyclomaticComplexMethod")
        private fun Preferences.readTrackingConfig(): TrackingConfig = TrackingConfig(
            profile = readProfile(),
            isProfilePinned = this[Keys.PROFILE_PINNED] ?: DEFAULTS.isProfilePinned,
            maxAccuracyMetres = this[Keys.MAX_ACCURACY] ?: DEFAULTS.maxAccuracyMetres,
            maxImpliedSpeedMetresPerSecond =
                this[Keys.MAX_IMPLIED_SPEED] ?: DEFAULTS.maxImpliedSpeedMetresPerSecond,
            allowMockLocations = this[Keys.ALLOW_MOCK] ?: DEFAULTS.allowMockLocations,
            kalmanProcessNoiseSquareMetresPerSecond =
                this[Keys.PROCESS_NOISE]
                    ?: DEFAULTS.kalmanProcessNoiseSquareMetresPerSecond,
            segmentGapSeconds =
                this[Keys.SEGMENT_GAP_SECONDS] ?: DEFAULTS.segmentGapSeconds,
            minStepMetres = this[Keys.MIN_STEP] ?: DEFAULTS.minStepMetres,
            stepAccuracyFactor =
                this[Keys.STEP_ACCURACY_FACTOR] ?: DEFAULTS.stepAccuracyFactor,
            autoPauseEnabled =
                this[Keys.AUTO_PAUSE_ENABLED] ?: DEFAULTS.autoPauseEnabled,
            autoPauseWindowMillis =
                this[Keys.AUTO_PAUSE_WINDOW_MS] ?: DEFAULTS.autoPauseWindowMillis,
            autoPauseDisplacementMetres =
                this[Keys.AUTO_PAUSE_DISPLACEMENT] ?: DEFAULTS.autoPauseDisplacementMetres,
            autoPauseSpeedMetresPerSecond =
                this[Keys.AUTO_PAUSE_SPEED] ?: DEFAULTS.autoPauseSpeedMetresPerSecond,
            autoResumeDisplacementMetres =
                this[Keys.AUTO_RESUME_DISPLACEMENT]
                    ?: DEFAULTS.autoResumeDisplacementMetres,
            simplifyEpsilonMetres =
                this[Keys.SIMPLIFY_EPSILON] ?: DEFAULTS.simplifyEpsilonMetres,
            writeBatchSize = this[Keys.WRITE_BATCH_SIZE] ?: DEFAULTS.writeBatchSize,
            writeBatchWindowMillis =
                this[Keys.WRITE_BATCH_WINDOW_MS] ?: DEFAULTS.writeBatchWindowMillis,
            economyBatteryPercent =
                this[Keys.ECONOMY_BATTERY_PERCENT] ?: DEFAULTS.economyBatteryPercent,
            transitSpeedMetresPerSecond =
                this[Keys.TRANSIT_SPEED] ?: DEFAULTS.transitSpeedMetresPerSecond,
        )

        /**
         * Resolves the stored wire name.
         *
         * An unrecognised name means the store was written by a newer build with a profile
         * this one does not have. Falling back to the default is right: guessing at the
         * nearest known profile would silently change the sampling rate of a survey.
         */
        private fun Preferences.readProfile(): LocationProfile {
            val stored = this[Keys.PROFILE] ?: return DEFAULTS.profile
            return LocationProfile.entries.firstOrNull { it.wireName == stored }
                ?: DEFAULTS.profile
        }
    }
}
