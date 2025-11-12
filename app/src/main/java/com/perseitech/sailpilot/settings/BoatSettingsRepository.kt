package com.perseitech.sailpilot.settings

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "boat_settings")

data class BoatSettings(
    val draftM: Double = 1.8,   // pescaggio
    val lengthM: Double = 11.0, // LOA
    val beamM: Double = 3.8,    // larghezza
    val seaRatesApiKey: String? = null
)

class BoatSettingsRepository(private val context: Context) {

    companion object {
        private val DRAFT = doublePreferencesKey("draft_m")
        private val LENGTH = doublePreferencesKey("length_m")
        private val BEAM = doublePreferencesKey("beam_m")
        private val SR_KEY = stringPreferencesKey("searates_api_key")
    }

    val flow: Flow<BoatSettings> = context.dataStore.data.map { prefs ->
        BoatSettings(
            draftM = prefs[DRAFT] ?: 1.8,
            lengthM = prefs[LENGTH] ?: 11.0,
            beamM = prefs[BEAM] ?: 3.8,
            seaRatesApiKey = prefs[SR_KEY]
        )
    }

    suspend fun saveDraft(v: Double) { context.dataStore.edit { it[DRAFT] = v } }
    suspend fun saveLength(v: Double) { context.dataStore.edit { it[LENGTH] = v } }
    suspend fun saveBeam(v: Double) { context.dataStore.edit { it[BEAM] = v } }
    suspend fun saveApiKey(k: String?) { context.dataStore.edit { if (k.isNullOrBlank()) it.remove(SR_KEY) else it[SR_KEY] = k } }
}
