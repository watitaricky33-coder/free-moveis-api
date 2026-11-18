package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import com.example.model.AppConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.configDataStore: DataStore<Preferences> by preferencesDataStore(name = "tmdb_embed_config")

class ConfigRepository(private val context: Context) {
    companion object {
        private val KEY_CONFIG_JSON = stringPreferencesKey("config_override_json")
        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = true
            encodeDefaults = true
        }

        fun parseCookies(raw: String?): List<String> {
            if (raw.isNullOrBlank()) return emptyList()
            val trimmed = raw.trim()
            val list = if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                runCatching { json.decodeFromString<List<String>>(trimmed) }.getOrDefault(emptyList())
            } else {
                trimmed.split(Regex("[,;\\n]+")).map { it.trim() }.filter { it.isNotEmpty() }
            }
            return list.map { it.removePrefix("ui=").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        fun normalizeTmdbKeys(keysInput: List<String>, legacyKey: String? = null, clearedByUser: Boolean = false): List<String> {
            val base = if (keysInput.isNotEmpty()) {
                keysInput
            } else if (!clearedByUser && !legacyKey.isNullOrBlank()) {
                listOf(legacyKey)
            } else {
                emptyList()
            }
            return base.flatMap { it.split(Regex("[,;\\n]+")) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        fun defaultConfig(defaultApiBaseUrl: String = BuildConfig.FREE_MOVIES_API_BASE_URL): AppConfig {
            return AppConfig(
                apiBaseUrl = defaultApiBaseUrl.trim().trimEnd('/'),
                enableProxy = true
            )
        }
    }

    val configFlow: Flow<AppConfig> = context.configDataStore.data.map { prefs ->
        val raw = prefs[KEY_CONFIG_JSON]
        if (raw.isNullOrBlank()) {
            defaultConfig()
        } else {
            runCatching { json.decodeFromString<AppConfig>(raw) }.getOrElse { defaultConfig() }
        }
    }

    suspend fun getConfig(): AppConfig = configFlow.first()

    suspend fun saveConfig(config: AppConfig) {
        val normalized = config.copy(
            apiBaseUrl = config.apiBaseUrl.trim().trimEnd('/'),
            tmdbApiKeys = normalizeTmdbKeys(config.tmdbApiKeys, clearedByUser = true),
            febboxCookies = config.febboxCookies.map { it.removePrefix("ui=").trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        )
        context.configDataStore.edit { prefs ->
            prefs[KEY_CONFIG_JSON] = json.encodeToString(normalized)
        }
    }

    suspend fun resetToDefaults() {
        context.configDataStore.edit { prefs ->
            prefs.remove(KEY_CONFIG_JSON)
        }
    }

    fun toPrettyJson(config: AppConfig): String = json.encodeToString(config)
}
