package com.ouvinte.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ouvinte.app.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "ouvinte_settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private val KEY_GEMINI = stringPreferencesKey("gemini_api_key")
        private val KEY_SEARCH = stringPreferencesKey("google_search_api_key")
        private val KEY_ENGINE = stringPreferencesKey("google_search_engine_id")
        private val KEY_PIN_HASH = stringPreferencesKey("pin_hash")
        private val KEY_PIN_SET = booleanPreferencesKey("pin_set")
        private val KEY_KEYS_SET = booleanPreferencesKey("keys_set")
    }

    // ── Fluxos de estado ──────────────────────────────────────────────────────

    val isPinSet: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_PIN_SET] == true
    }

    // Considera chaves configuradas se estão no DataStore OU no BuildConfig (local.properties)
    val areKeysSet: Flow<Boolean> = context.dataStore.data.map {
        it[KEY_KEYS_SET] == true || BuildConfig.GEMINI_API_KEY.isNotBlank()
    }

    // ── Leitura de chaves (BuildConfig tem prioridade; DataStore só como override) ──

    suspend fun getGeminiApiKey(): String {
        if (BuildConfig.GEMINI_API_KEY.isNotBlank()) return BuildConfig.GEMINI_API_KEY
        return context.dataStore.data.map { it[KEY_GEMINI] ?: "" }.first()
    }

    suspend fun getSearchApiKey(): String {
        if (BuildConfig.GOOGLE_SEARCH_API_KEY.isNotBlank()) return BuildConfig.GOOGLE_SEARCH_API_KEY
        return context.dataStore.data.map { it[KEY_SEARCH] ?: "" }.first()
    }

    suspend fun getSearchEngineId(): String {
        if (BuildConfig.GOOGLE_SEARCH_ENGINE_ID.isNotBlank()) return BuildConfig.GOOGLE_SEARCH_ENGINE_ID
        return context.dataStore.data.map { it[KEY_ENGINE] ?: "" }.first()
    }

    // ── Escrita de chaves ─────────────────────────────────────────────────────

    suspend fun saveApiKeys(geminiKey: String, searchKey: String, engineId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_GEMINI] = geminiKey.trim()
            prefs[KEY_SEARCH] = searchKey.trim()
            prefs[KEY_ENGINE] = engineId.trim()
            prefs[KEY_KEYS_SET] = geminiKey.isNotBlank()
        }
    }

    suspend fun getStoredKeys(): Triple<String, String, String> {
        return Triple(
            getGeminiApiKey(),
            getSearchApiKey(),
            getSearchEngineId()
        )
    }

    // ── PIN ───────────────────────────────────────────────────────────────────

    suspend fun setPin(pin: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PIN_HASH] = hash(pin)
            prefs[KEY_PIN_SET] = true
        }
    }

    suspend fun verifyPin(pin: String): Boolean =
        context.dataStore.data.map {
            it[KEY_PIN_HASH] == hash(pin)
        }.first()

    private fun hash(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

}
