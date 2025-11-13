package com.perseitech.sailpilot.settings

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NightAccent {
    RED, GREEN
}

data class AppUiSettings(
    val darkTheme: Boolean = false,
    val nightAccent: NightAccent = NightAccent.RED
)

/**
 * Impostazioni UI generali (tema, colori night).
 * Usa SharedPreferences per persistenza.
 */
class AppUiSettingsRepository(context: Context) {

    private val prefs = context.getSharedPreferences("app_ui_settings", Context.MODE_PRIVATE)

    private val _flow = MutableStateFlow(loadFromPrefs())
    val flow: StateFlow<AppUiSettings> = _flow.asStateFlow()

    private fun loadFromPrefs(): AppUiSettings {
        val dark = prefs.getBoolean("darkTheme", false)
        val accentName = prefs.getString("nightAccent", NightAccent.RED.name) ?: NightAccent.RED.name
        val accent = runCatching { NightAccent.valueOf(accentName) }.getOrElse { NightAccent.RED }
        return AppUiSettings(darkTheme = dark, nightAccent = accent)
    }

    fun saveDarkTheme(dark: Boolean) {
        prefs.edit().putBoolean("darkTheme", dark).apply()
        _flow.value = _flow.value.copy(darkTheme = dark)
    }

    fun saveNightAccent(accent: NightAccent) {
        prefs.edit().putString("nightAccent", accent.name).apply()
        _flow.value = _flow.value.copy(nightAccent = accent)
    }
}
