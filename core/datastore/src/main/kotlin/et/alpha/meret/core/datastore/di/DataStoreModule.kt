package et.alpha.meret.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.datastore.OnboardingStore
import et.alpha.meret.core.datastore.TrackingConfigStore
import et.alpha.meret.core.datastore.TrackingSessionStore
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
public object DataStoreModule {
    /**
     * One preferences store for the whole app.
     *
     * No explicit `scope` argument, deliberately. `PreferenceDataStoreFactory` defaults to its
     * own I/O-dispatched supervisor scope, which is already the right thread for a file read;
     * passing one built from `DispatcherProvider` would add a dependency for no behavioural
     * change, and hard-coding `Dispatchers.IO` here would break the rule that nothing outside
     * `DispatcherProvider` names a dispatcher.
     *
     * The corruption handler is not optional. Without it a damaged file (a write cut off by a
     * dead battery, a bad flash cell) throws `CorruptionException` from every read *and*
     * every write, forever, and the tracking service would fail on each restart. Starting
     * from empty preferences loses only settings and the resume pointer; recorded data lives
     * in Room.
     */
    @Provides
    @Singleton
    public fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        corruptionHandler = preferencesCorruptionHandler(),
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_NAME) },
    )

    @Provides
    @Singleton
    public fun provideTrackingConfigStore(dataStore: DataStore<Preferences>): TrackingConfigStore =
        TrackingConfigStore(dataStore)

    @Provides
    @Singleton
    public fun provideTrackingSessionStore(
        dataStore: DataStore<Preferences>,
    ): TrackingSessionStore = TrackingSessionStore(dataStore)

    @Provides
    @Singleton
    public fun provideOnboardingStore(dataStore: DataStore<Preferences>): OnboardingStore =
        OnboardingStore(dataStore)

    private const val PREFERENCES_NAME = "meret_preferences"
}

/** Shared with the tests, so they exercise the handler production uses. */
internal fun preferencesCorruptionHandler(): ReplaceFileCorruptionHandler<Preferences> =
    ReplaceFileCorruptionHandler { emptyPreferences() }
