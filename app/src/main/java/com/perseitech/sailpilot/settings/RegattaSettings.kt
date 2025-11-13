package com.perseitech.sailpilot.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RegattaSettings(
    val classId: String = "GENERIC_MONOHULL",
    val isafCountdownEnabled: Boolean = true
)

/**
 * Repo leggero basato su SharedPreferences.
 * Tiene in memoria la classe selezionata e se il countdown ISAF è attivo.
 */
class RegattaSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("regatta_settings", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(loadFromPrefs())
    val flow: StateFlow<RegattaSettings> = _flow.asStateFlow()

    private fun loadFromPrefs(): RegattaSettings {
        val id = prefs.getString("classId", "GENERIC_MONOHULL") ?: "GENERIC_MONOHULL"
        val enabled = prefs.getBoolean("isafCountdownEnabled", true)
        return RegattaSettings(id, enabled)
    }

    fun saveClassId(id: String) {
        prefs.edit().putString("classId", id).apply()
        _flow.value = _flow.value.copy(classId = id)
    }

    fun saveIsafCountdownEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("isafCountdownEnabled", enabled).apply()
        _flow.value = _flow.value.copy(isafCountdownEnabled = enabled)
    }
}
