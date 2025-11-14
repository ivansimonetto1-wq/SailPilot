package com.perseitech.sailpilot.weather

import android.util.Log
import com.perseitech.sailpilot.ui.WeatherSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object OpenMeteoClient {

    /**
     * Scarica un singolo snapshot meteo usando Open-Meteo (marine API).
     *
     * Nota: implementazione volutamente semplice / robusta:
     * - niente librerie esterne
     * - in caso di errore ritorna null
     */
    fun fetchSnapshot(lat: Double, lon: Double): WeatherSnapshot? {
        return try {
            // API marine: vento + onda
            val urlStr =
                "https://api.open-meteo.com/v1/marine" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current_weather=true" +
                        "&hourly=wave_height"

            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
            }

            val code = conn.responseCode
            if (code != 200) {
                conn.disconnect()
                Log.w("OpenMeteoClient", "HTTP $code")
                return null
            }

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)

            // current_weather blocco principale
            val cw = json.optJSONObject("current_weather")
            val windSpeedKmh = cw?.optDouble("windspeed", Double.NaN)
            val windDirDeg = cw?.optDouble("winddirection", Double.NaN)

            val twsKn =
                if (windSpeedKmh != null && windSpeedKmh.isFinite())
                    windSpeedKmh * 0.539957
                else null

            val twdDeg =
                if (windDirDeg != null && windDirDeg.isFinite())
                    windDirDeg
                else null

            // sea state molto semplificato da wave_height (prima ora della serie)
            val hourly = json.optJSONObject("hourly")
            val waveHeights = hourly?.optJSONArray("wave_height")
            val seaState: String? = if (waveHeights != null && waveHeights.length() > 0) {
                val h = waveHeights.optDouble(0, Double.NaN)
                if (h.isFinite()) {
                    when {
                        h < 0.5 -> "Calm / slight (H≈${"%.1f".format(h)} m)"
                        h < 1.5 -> "Moderate sea (H≈${"%.1f".format(h)} m)"
                        h < 2.5 -> "Rough sea (H≈${"%.1f".format(h)} m)"
                        else -> "Very rough (H≈${"%.1f".format(h)} m)"
                    }
                } else null
            } else null

            // Per le maree servirebbe un endpoint dedicato: per ora stringa placeholder
            val tide: String? = null

            WeatherSnapshot(
                twsKn = twsKn,
                twdDeg = twdDeg,
                sogKn = null,          // SOG/COG sono dai tuoi strumenti, qui lascio null
                cogDeg = null,
                seaState = seaState,
                tide = tide,
                providerName = "Open-Meteo",
                isForecast = false
            )
        } catch (e: Exception) {
            Log.e("OpenMeteoClient", "fetchSnapshot failed", e)
            null
        }
    }
}
