package com.perseitech.sailpilot.io.nmea

import com.perseitech.sailpilot.io.DataBus
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Socket

/**
 * Client TCP NMEA 0183 che alimenta il DataBus.
 */
class Nmea0183Client(
    private val host: String,
    private val port: Int
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch(Dispatchers.IO) {
            try {
                Socket(host, port).use { socket ->
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        val r = Nmea0183Parser.parse(line)
                        r.position?.let { DataBus.updatePosition(it) }
                        if (r.cogDeg != null || r.sogKn != null) DataBus.mergeCogSog(r.cogDeg, r.sogKn)
                        r.headingDeg?.let { DataBus.mergeHeading(it) }
                        if (r.twsKn != null || r.twdDeg != null || r.twaDeg != null) DataBus.mergeTrueWind(r.twsKn, r.twdDeg, r.twaDeg)
                        r.depthM?.let { DataBus.mergeDepth(it) }
                    }
                }
            } catch (_: Exception) {
                // silenzioso
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
