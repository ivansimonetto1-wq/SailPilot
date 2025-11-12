package com.perseitech.sailpilot.io.signalk

import com.perseitech.sailpilot.io.DataBus
import com.perseitech.sailpilot.io.DataBus.Source
import com.perseitech.sailpilot.io.NavData
import com.perseitech.sailpilot.routing.LatLon
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.*
import okio.ByteString
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Client Signal K via WebSocket.
 * Esempi URL: ws://host:3000/signalk/v1/stream?subscribe=self
 * Auth opzionale: token Bearer (Signal K access token).
 */
class SignalKClient(
    private val wsUrl: String,
    private val bearerToken: String? = null
) : WebSocketListener() {

    private var ws: WebSocket? = null
    private var client: OkHttpClient? = null
    private var scope: CoroutineScope? = null

    fun start(scope: CoroutineScope) {
        stop()
        this.scope = scope
        client = OkHttpClient.Builder()
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
        val reqBuilder = Request.Builder().url(wsUrl)
        bearerToken?.let { reqBuilder.addHeader("Authorization", "Bearer $it") }
        val req = reqBuilder.build()
        ws = client!!.newWebSocket(req, this)
    }

    fun stop() {
        ws?.close(1000, "bye"); ws = null
        client?.dispatcher?.executorService?.shutdown()
        client = null
        scope?.cancel(); scope = null
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        try {
            val js = JSONObject(text)
            val updates = js.optJSONArray("updates") ?: return
            for (i in 0 until updates.length()) {
                val u = updates.getJSONObject(i)
                val values = u.optJSONArray("values") ?: continue
                var pos: LatLon? = null
                var sogKn: Double? = null
                var cogDeg: Double? = null
                var hdgDeg: Double? = null
                var depthM: Double? = null
                for (k in 0 until values.length()) {
                    val v = values.getJSONObject(k)
                    val path = v.optString("path", "")
                    val value = v.opt("value")
                    when (path) {
                        "navigation.position" -> {
                            val p = value as? JSONObject ?: continue
                            val lat = p.optDouble("latitude", Double.NaN)
                            val lon = p.optDouble("longitude", Double.NaN)
                            if (!lat.isNaN() && !lon.isNaN()) pos = LatLon(lat, lon)
                        }
                        "navigation.speedOverGround" -> {
                            val mps = (value as? Number)?.toDouble()
                            if (mps != null) sogKn = mps * 1.9438445
                        }
                        "navigation.courseOverGroundTrue" -> {
                            val rad = (value as? Number)?.toDouble()
                            if (rad != null) cogDeg = Math.toDegrees(rad)
                        }
                        "navigation.headingTrue" -> {
                            val rad = (value as? Number)?.toDouble()
                            if (rad != null) hdgDeg = Math.toDegrees(rad)
                        }
                        "environment.depth.belowTransducer" -> {
                            val m = (value as? Number)?.toDouble()
                            if (m != null) depthM = m
                        }
                    }
                }
                if (pos != null || sogKn != null || cogDeg != null || hdgDeg != null || depthM != null) {
                    DataBus.apply(Source.SIGNAL_K, NavData(position = pos, sogKn = sogKn, cogDeg = cogDeg, headingDeg = hdgDeg, depthM = depthM))
                }
            }
        } catch (_: Exception) { /* ignora delta non parsabili */ }
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        onMessage(webSocket, bytes.utf8())
    }
}
