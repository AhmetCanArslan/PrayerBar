package com.arslan.prayerbar.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.arslan.prayerbar.prayer.PrayerSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "prayerbar")

/**
 * Single JSON blob in DataStore. Decoding is lenient: an unreadable or outdated blob falls back to
 * defaults instead of crashing a broadcast receiver.
 */
class SettingsRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
    }

    val settings: Flow<PrayerSettings> = context.dataStore.data.map { prefs ->
        decode(prefs[KEY])
    }

    suspend fun current(): PrayerSettings = settings.first()

    suspend fun update(transform: (PrayerSettings) -> PrayerSettings): PrayerSettings {
        var result = PrayerSettings()
        context.dataStore.edit { prefs ->
            result = transform(decode(prefs[KEY]))
            prefs[KEY] = json.encodeToString(result)
        }
        return result
    }

    private fun decode(raw: String?): PrayerSettings {
        if (raw.isNullOrBlank()) return PrayerSettings()
        return runCatching { json.decodeFromString<PrayerSettings>(raw) }.getOrElse { PrayerSettings() }
    }

    private companion object {
        val KEY = stringPreferencesKey("settings_json")
    }
}
