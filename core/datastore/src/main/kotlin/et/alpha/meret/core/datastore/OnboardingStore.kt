package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Remembers which permission-onboarding steps the worker skipped, so the flow does not ask
 * again on every launch.
 *
 * Steps are stored by name, not as `:domain`'s `SkippableStep`, because this module sits
 * below `:domain`. The caller maps names back and ignores any it does not know: a step
 * removed in a later version must not crash the screen.
 *
 * Only skips are stored. Whether a permission is granted is never cached here; the screen
 * reads it fresh on every resume, since the worker can revoke it in system settings.
 */
public class OnboardingStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Empty for a fresh install, and for a stored value of the wrong type. */
    public val skippedSteps: Flow<Set<String>> =
        dataStore.data.map { it.skipped() }.distinctUntilChanged()

    public suspend fun skip(step: String) {
        dataStore.edit { preferences ->
            preferences[SKIPPED] = preferences.skipped() + step
        }
    }

    private companion object {
        val SKIPPED = stringSetPreferencesKey("onboarding_skipped_steps")

        /** `runCatching`: a value of another type under this key reads as nothing skipped. */
        fun Preferences.skipped(): Set<String> =
            runCatching { this[SKIPPED]?.toSet() }.getOrNull().orEmpty()
    }
}
