package net.sagberg.tournarrat.core.data.preferences

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.sagberg.tournarrat.core.model.AppPreferences

interface PreferencesRepository {
    val preferences: Flow<AppPreferences>

    suspend fun update(transform: (AppPreferences) -> AppPreferences)
}

private val Context.preferencesDataStore by preferencesDataStore(name = "app_preferences")

class PreferencesRepositoryImpl(
    private val context: Context,
    private val json: Json,
) : PreferencesRepository {
    private val key = stringPreferencesKey("app_preferences_json")

    override val preferences: Flow<AppPreferences> =
        context.preferencesDataStore.data.map { store ->
            store[key]?.let { stored ->
                runCatching { json.decodeFromString<AppPreferences>(stored) }
                    .getOrElse { AppPreferences() }
            } ?: AppPreferences()
        }

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        context.preferencesDataStore.edit { store ->
            val current = store[key]?.let { stored ->
                runCatching { json.decodeFromString<AppPreferences>(stored) }
                    .getOrElse { AppPreferences() }
            } ?: AppPreferences()
            store[key] = json.encodeToString(transform(current))
        }
    }
}
