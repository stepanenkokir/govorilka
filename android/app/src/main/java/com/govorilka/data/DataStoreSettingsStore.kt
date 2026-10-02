package com.govorilka.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.govorilka.domain.DEFAULT_INSTRUCTIONS
import com.govorilka.domain.Settings
import com.govorilka.domain.SettingsStore
import com.govorilka.domain.Voice
import java.io.IOException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

private val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
private val APP_SECRET = stringPreferencesKey("app_secret")
private val INSTRUCTIONS = stringPreferencesKey("instructions")
private val VOICE = stringPreferencesKey("voice")
private val WEB_SEARCH = booleanPreferencesKey("web_search")

class DataStoreSettingsStore(context: Context) : SettingsStore {
    private val dataStore = context.settingsDataStore

    override val settings: Flow<Settings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            Settings(
                serverBaseUrl = prefs[SERVER_BASE_URL].orEmpty(),
                appSecret = prefs[APP_SECRET].orEmpty(),
                instructions = prefs[INSTRUCTIONS] ?: DEFAULT_INSTRUCTIONS,
                voice = Voice.fromWireName(prefs[VOICE]),
                webSearch = prefs[WEB_SEARCH] ?: false,
            )
        }

    override suspend fun setServerBaseUrl(value: String) = put(SERVER_BASE_URL, value.trim().trimEnd('/'))

    override suspend fun setAppSecret(value: String) = put(APP_SECRET, value)

    override suspend fun setInstructions(value: String) = put(INSTRUCTIONS, value)

    override suspend fun setVoice(value: Voice) = put(VOICE, value.wireName)

    override suspend fun setWebSearch(value: Boolean) = put(WEB_SEARCH, value)

    private suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        // The settings screen may close right after the last keystroke; the write must still land.
        withContext(NonCancellable) { dataStore.edit { it[key] = value } }
    }
}
