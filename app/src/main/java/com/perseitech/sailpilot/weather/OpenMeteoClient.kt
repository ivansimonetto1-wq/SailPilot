package com.perseitech.sailpilot.weather

import android.util.Log
import com.perseitech.sailpilot.ui.WeatherSnapshot
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

object OpenMeteoClient {

    /**
     * Scarica un singolo snapshot meteo usando l'API Marine di Open-Meteo.
     *
     * - Vento: da current_weather (km/h → kn)
     * - Onda: wave_height (prima ora)
     * - Maree: sea_level_height_msl (prima ora) → stringa descrittiva
     *
     * ATTENZIONE: Open-Meteo stesso dice che sea_level_height_msl
     * NON è adatto per navigazione costiera di precisione. Qui lo
     * usiamo solo come indicazione qualitativa (alta/bassa/prossimo
     * a livello medio).
     */
    fun fetchSnapshot(lat: Double, lon: Double): WeatherSnapshot? {
        return try {
            val urlStr =
                "https://api.open-meteo.com/v1/marine" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current_weather=true" +
                        "&hourly=wave_height,sea_level_height_msl"

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

            // --- VENTO ---
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

            // --- ONDA + MAREE ---
            val hourly = json.optJSONObject("hourly")

            // Onda (già usata prima, ma ora la rifacciamo pulita)
            val waveHeights = hourly?.optJSONArray("wave_height")
            val seaState: String? = if (waveHeights != null && waveHeights.length() > 0) {
                val h = waveHeights.optDouble(0, Double.NaN)
                if (h.isFinite()) {
                    when {
                        h < 0.5 -> "Calm / slight (H≈${"%.1f".format(h)} m)"
                        h < 1.5 -> "Moderate sea (H≈${"%.1f".format(h)} m)"
                        h < 2.5 -> "Rough sea (H≈${"%.1f".format(h)} m)"
                        else    -> "Very rough (H≈${"%.1f".format(h)} m)"
                    }
                } else null
            } else null

            // Maree (sea_level_height_msl)
            val seaLevelArr = hourly?.optJSONArray("sea_level_height_msl")
            val tide: String? = if (seaLevelArr != null && seaLevelArr.length() > 0) {
                val sl = seaLevelArr.optDouble(0, Double.NaN)
                if (sl.isFinite()) {
                    when {
                        sl > 0.5  -> "Sea level +${"%.2f".format(sl)} m (above mean)"
                        sl < -0.5 -> "Sea level ${"%.2f".format(sl)} m (below mean)"
                        abs(sl) <= 0.5 -> "Sea level near mean (Δ≈${"%.2f".format(sl)} m)"
                        else -> null
                    }
                } else null
            } else null

            WeatherSnapshot(
                twsKn = twsKn,
                twdDeg = twdDeg,
                sogKn = null,          // SOG/COG → strumenti di bordo
                cogDeg = null,
                seaState = seaState,
                tide = tide,
                providerName = "Open-Meteo Marine",
                isForecast = false
            )
        } catch (e: Exception) {
            Log.e("OpenMeteoClient", "fetchSnapshot failed", e)
            null
        }
    }
}
