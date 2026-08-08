package et.alpha.meret.core.datastore.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import et.alpha.meret.core.datastore.TrackingConfigStore
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
     */
    @Provides
    @Singleton
    public fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(PREFERENCES_NAME) },
    )

    @Provides
    @Singleton
    public fun provideTrackingConfigStore(dataStore: DataStore<Preferences>): TrackingConfigStore =
        TrackingConfigStore(dataStore)

    private const val PREFERENCES_NAME = "meret_preferences"
}
