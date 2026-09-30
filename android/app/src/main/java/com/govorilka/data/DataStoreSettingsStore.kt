package com.govorilka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.govorilka.domain.DEFAULT_INSTRUCTIONS
import com.govorilka.domain.Settings
import com.govorilka.domain.SettingsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
private val APP_SECRET = stringPreferencesKey("app_secret")
private val INSTRUCTIONS = stringPreferencesKey("instructions")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.settingsDataStore

    override val settings: Flow<Settings> = dataStore.data.map { prefs ->
        Settings(
            serverBaseUrl = prefs[SERVER_BASE_URL].orEmpty(),
            appSecret = prefs[APP_SECRET].orEmpty(),
            instructions = prefs[INSTRUCTIONS] ?: DEFAULT_INSTRUCTIONS,
        )
    }

    override suspend fun setServerBaseUrl(value: String) = put(SERVER_BASE_URL, value.trim().trimEnd('/'))

    override suspend fun setAppSecret(value: String) = put(APP_SECRET, value)

    override suspend fun setInstructions(value: String) = put(INSTRUCTIONS, value)

    private suspend fun put(key: Preferences.Key<String>, value: String) {
        dataStore.edit { it[key] = value }
    }
}
