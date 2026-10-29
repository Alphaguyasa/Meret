package et.alpha.meret.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first

/**
 * This installation's id, which every track records as its `device_id` and which sync and
 * peer sync use as the origin of a row.
 *
 * A random id made on first use, never a hardware identifier: records identify people, and
 * an IMEI or Android ID would tie them to a phone across reinstalls. A reinstall, or a
 * corrupt preferences file, gives a new id. That costs nothing: ids only say which install
 * wrote a row, and every row has its own UUID.
 */
public class DeviceIdentityStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** The stored id, or [newId]'s, stored first. The same id on every later call. */
    public suspend fun deviceId(newId: () -> String): String {
        dataStore.data.first().storedId()?.let { return it }
        val written =
            dataStore.edit { preferences ->
                // Checked again inside the edit: two first calls racing keep one id.
                if (preferences.storedId() == null) preferences[DEVICE_ID] = newId()
            }
        return checkNotNull(written.storedId())
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")

        /** `runCatching`: a value of another type under this key reads as no id. */
        fun Preferences.storedId(): String? =
            runCatching { this[DEVICE_ID] }.getOrNull()?.takeIf { it.isNotBlank() }
    }
}
