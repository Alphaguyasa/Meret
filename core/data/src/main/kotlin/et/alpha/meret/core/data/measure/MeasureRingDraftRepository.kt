package et.alpha.meret.core.data.measure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import et.alpha.meret.core.common.result.AppError
import et.alpha.meret.core.common.result.AppResult
import et.alpha.meret.core.model.geo.LatLng
import et.alpha.meret.core.model.geo.RingGeoJson
import et.alpha.meret.domain.measure.MeasureRingDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject

/**
 * The ring in progress in the preferences file, as the same GeoJSON a saved measurement
 * stores. A value that cannot be read, of the wrong type or not a ring, reads as no ring: a
 * lost ring is re-tapped, a screen that cannot open is not.
 */
public class MeasureRingDraftRepository
    @Inject
    internal constructor(
        private val dataStore: DataStore<Preferences>,
    ) : MeasureRingDraft {
        override val vertices: Flow<List<LatLng>> =
            dataStore.data
                .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
                .map { it.ring() }
                .distinctUntilChanged()

        override suspend fun update(transform: (List<LatLng>) -> List<LatLng>): AppResult<Unit> =
            try {
                dataStore.edit { preferences ->
                    val next = transform(preferences.ring())
                    if (next.isEmpty()) {
                        preferences.remove(RING)
                    } else {
                        preferences[RING] = RingGeoJson.encode(next)
                    }
                }
                AppResult.Success(Unit)
            } catch (e: IOException) {
                AppResult.Failure(AppError.Storage(AppError.Storage.Kind.WriteFailed, e))
            }

        private fun Preferences.ring(): List<LatLng> =
            runCatching { this[RING] }.getOrNull()?.let(RingGeoJson::decode).orEmpty()

        private companion object {
            val RING = stringPreferencesKey("measure_ring_in_progress")
        }
    }
