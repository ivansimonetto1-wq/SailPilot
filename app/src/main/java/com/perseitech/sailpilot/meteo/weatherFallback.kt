package com.perseitech.sailpilot.meteo

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import kotlin.math.abs

data class WindNow(val twsKn: Double, val twdDeg: Double)

/**
 * Fallback meteo: prende vento "vero" 10m (direzione e velocità)
 * da Open-Meteo (gratuito, globale). Usiamo questo solo se NMEA/SignalK non forniscono vento.
 */
object WeatherFallback {
    fun fetchWind(lat: Double, lon: Double): WindNow? {
        val qs = "latitude=${lat}&longitude=${lon}&current=wind_speed_10m,wind_direction_10m"
        val url = "https://api.open-meteo.com/v1/forecast?$qs"
        val conn = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000; readTimeout = 8000
        }
        return try {
            conn.inputStream.bufferedReader().use { br ->
                val text = br.readText()
                val obj = JSONObject(text).optJSONObject("current") ?: return null
                val ws = obj.optDouble("wind_speed_10m", Double.NaN)
                val wd = obj.optDouble("wind_direction_10m", Double.NaN)
                if (ws.isNaN() || wd.isNaN()) null
                else WindNow(twsKn = ws * 0.539957, twdDeg = ((wd % 360) + 360) % 360)
            }
        } catch (_: Throwable) {
            null
        } finally {
            conn.disconnect()
        }
    }
}
