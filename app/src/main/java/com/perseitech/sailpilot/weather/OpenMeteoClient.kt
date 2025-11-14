package com.perseitech.sailpilot.weather

import com.perseitech.sailpilot.ui.SeaStateSnapshot
import com.perseitech.sailpilot.ui.TideSnapshot
import com.perseitech.sailpilot.ui.WeatherSnapshot
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection

object OpenMeteoClient {

    /**
     * Semplice client Open-Meteo.
     * Ritorna uno snapshot con TWS/TWD (in kn/deg) + onda (se disponibile).
     * Viene chiamato già in un Dispatcher.IO dal chiamante.
     */
    fun fetchSnapshot(lat: Double, lon: Double): WeatherSnapshot? {
        return try {
            val urlStr =
                "https://api.open-meteo.com/v1/forecast" +
                        "?latitude=$lat&longitude=$lon" +
                        "&current_weather=true" +
                        "&hourly=wave_height,wave_direction,wave_period" +
                        "&timezone=auto"

            val conn = URL(urlStr).openConnection() as HttpsURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val json = JSONObject(body)
            val current = json.optJSONObject("current_weather")

            val windSpeed = current?.optDouble("windspeed", Double.NaN)
            val windDir = current?.optDouble("winddirection", Double.NaN)

            val hourly = json.optJSONObject("hourly")
            val seaState = hourly?.let { h ->
                val hArr = h.optJSONArray("wave_height")
                val dArr = h.optJSONArray("wave_direction")
                val pArr = h.optJSONArray("wave_period")
                if (hArr != null && hArr.length() > 0) {
                    val hs = hArr.optDouble(0, Double.NaN)
                    val dir = dArr?.optDouble(0, Double.NaN)
                    val per = pArr?.optDouble(0, Double.NaN)
                    SeaStateSnapshot(
                        hsMeters = hs.takeIf { !it.isNaN() },
                        dirDeg = dir?.takeIf { !it.isNaN() },
                        periodSec = per?.takeIf { !it.isNaN() }
                    )
                } else null
            }

            val tide: TideSnapshot? = null // per ora niente maree reali

            WeatherSnapshot(
                twsKn = windSpeed?.takeIf { !it.isNaN() }?.let { it * 0.539957 }, // m/s -> kn se necessario
                twdDeg = windDir?.takeIf { !it.isNaN() },
                sogKn = null,
                cogDeg = null,
                seaState = seaState,
                tide = tide,
                providerName = "Open-Meteo",
                isForecast = true
            )
        } catch (e: Exception) {
            null
        }
    }
}
