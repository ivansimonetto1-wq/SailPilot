package com.perseitech.sailpilot.io.signalk

import android.util.Log
import com.perseitech.sailpilot.io.DataBus
import okhttp3.*
import okio.ByteString
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class SignalKClient(
    private val url: String,
    private val token: String? = null
) {
    private var client: OkHttpClient? = null
    private var ws: WebSocket? = null

    fun start() {
        val c = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
        client = c

        val reqBuilder = Request.Builder().url(url)
        if (!token.isNullOrBlank()) reqBuilder.addHeader("Authorization", "Bearer $token")
        val request = reqBuilder.build()

        ws = c.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "SignalK open: ${response.code}")
                // Subscribe paths (alcuni server non richiedono questo messaggio)
                val sub = JSONObject().apply {
                    put("context", "vessels.self")
                    put("subscribe", JSONArray().apply {
                        put(JSONObject(mapOf("path" to "navigation.speedOverGround", "period" to 1000)))
                        put(JSONObject(mapOf("path" to "navigation.courseOverGroundTrue", "period" to 1000)))
                        put(JSONObject(mapOf("path" to "navigation.headingTrue", "period" to 1000)))
                        put(JSONObject(mapOf("path" to "environment.wind.speedTrue", "period" to 1000)))
                        put(JSONObject(mapOf("path" to "environment.wind.directionTrue", "period" to 1000)))
                        put(JSONObject(mapOf("path" to "environment.depth.belowTransducer", "period" to 2000)))
                        put(JSONObject(mapOf("path" to "navigation.position", "period" to 1000)))
                    })
                }
                webSocket.send(sub.toString())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = parseDelta(text)
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = parseDelta(bytes.utf8())
        })
    }

    fun stop() {
        try { ws?.close(1000, "stop") } catch (_: Throwable) {}
        ws = null
        client?.dispatcher?.executorService?.shutdown()
        client?.connectionPool?.evictAll()
        client = null
    }

    private fun parseDelta(json: String) {
        runCatching {
            val root = JSONObject(json)
            val updates = root.optJSONArray("updates") ?: return
            for (i in 0 until updates.length()) {
                val u = updates.getJSONObject(i)
                val values = u.optJSONArray("values") ?: continue
                for (k in 0 until values.length()) {
                    val v = values.getJSONObject(k)
                    when (v.optString("path")) {
                        "navigation.speedOverGround" -> {
                            val mps = v.opt("value").toString().toDoubleOrNull()
                            val kn = mps?.times(1.943844)
                            if (kn != null) DataBus.mergeCogSog(null, kn)
                        }
                        "navigation.courseOverGroundTrue" -> {
                            val radOrDeg = v.opt("value")
                            val deg = when (radOrDeg) {
                                is Number -> {
                                    val n = radOrDeg.toDouble()
                                    if (n <= 2 * Math.PI) Math.toDegrees(n) else n
                                }
                                else -> radOrDeg.toString().toDoubleOrNull()
                            }?.let { ((it % 360) + 360) % 360 }
                            if (deg != null) DataBus.mergeCogSog(deg, null)
                        }
                        "navigation.headingTrue" -> {
                            val radOrDeg = v.opt("value")
                            val deg = when (radOrDeg) {
                                is Number -> {
                                    val n = radOrDeg.toDouble()
                                    if (n <= 2 * Math.PI) Math.toDegrees(n) else n
                                }
                                else -> radOrDeg.toString().toDoubleOrNull()
                            }?.let { ((it % 360) + 360) % 360 }
                            if (deg != null) DataBus.mergeHeading(deg)
                        }
                        "navigation.position" -> {
                            val obj = v.optJSONObject("value") ?: continue
                            val lat = obj.optDouble("latitude")
                            val lon = obj.optDouble("longitude")
                            if (!lat.isNaN() && !lon.isNaN()) {
                                DataBus.updatePosition(com.perseitech.sailpilot.routing.LatLon(lat, lon))
                            }
                        }
                        "environment.wind.speedTrue" -> {
                            val mps = v.opt("value").toString().toDoubleOrNull()
                            val kn = mps?.times(1.943844)
                            if (kn != null) DataBus.mergeTrueWind(kn, null, null)
                        }
                        "environment.wind.directionTrue" -> {
                            val radOrDeg = v.opt("value")
                            val deg = when (radOrDeg) {
                                is Number -> {
                                    val n = radOrDeg.toDouble()
                                    if (n <= 2 * Math.PI) Math.toDegrees(n) else n
                                }
                                else -> radOrDeg.toString().toDoubleOrNull()
                            }?.let { ((it % 360) + 360) % 360 }
                            if (deg != null) DataBus.mergeTrueWind(null, deg, null)
                        }
                        "environment.depth.belowTransducer" -> {
                            val m = v.opt("value").toString().toDoubleOrNull()
                            if (m != null) DataBus.mergeDepth(m)
                        }
                    }
                }
            }
        }
    }

    companion object { private const val TAG = "SignalKClient" }
}
