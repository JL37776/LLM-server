package com.nzshores.llmserver.data.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nzshores.llmserver.core.model.DevicePreference
import com.nzshores.llmserver.core.model.ServerConfig
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "llm_manager_settings")

/**
 * Persists the handful of settings that should survive an app restart per the Phase 5 exit
 * criteria: last device preference and server config. Deliberately not used for download/model
 * state - that lives in Room, which is already durable.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val PORT = intPreferencesKey("server_port")
        val REQUIRE_API_KEY = booleanPreferencesKey("server_require_api_key")
        val API_KEY = stringPreferencesKey("server_api_key")
        val RESTRICT_SUBNET = booleanPreferencesKey("server_restrict_subnet")
        val MAX_CONCURRENT = intPreferencesKey("server_max_concurrent")
        val DEVICE_PREFERENCE = stringPreferencesKey("device_preference")
    }

    suspend fun loadServerConfig(): ServerConfig {
        val prefs = context.dataStore.data.map { it }.first()
        val default = ServerConfig()
        return ServerConfig(
            port = prefs[Keys.PORT] ?: default.port,
            requireApiKey = prefs[Keys.REQUIRE_API_KEY] ?: default.requireApiKey,
            apiKey = prefs[Keys.API_KEY] ?: default.apiKey,
            restrictToLanSubnet = prefs[Keys.RESTRICT_SUBNET] ?: default.restrictToLanSubnet,
            maxConcurrentRequests = prefs[Keys.MAX_CONCURRENT] ?: default.maxConcurrentRequests,
        )
    }

    suspend fun saveServerConfig(config: ServerConfig) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PORT] = config.port
            prefs[Keys.REQUIRE_API_KEY] = config.requireApiKey
            config.apiKey?.let { prefs[Keys.API_KEY] = it }
            prefs[Keys.RESTRICT_SUBNET] = config.restrictToLanSubnet
            prefs[Keys.MAX_CONCURRENT] = config.maxConcurrentRequests
        }
    }

    suspend fun loadDevicePreference(): DevicePreference {
        val prefs = context.dataStore.data.map { it }.first()
        return prefs[Keys.DEVICE_PREFERENCE]?.let {
            runCatching { DevicePreference.valueOf(it) }.getOrNull()
        } ?: DevicePreference.GPU_FIRST
    }

    suspend fun saveDevicePreference(preference: DevicePreference) {
        context.dataStore.edit { prefs -> prefs[Keys.DEVICE_PREFERENCE] = preference.name }
    }
}
