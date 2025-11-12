package com.perseitech.sailpilot.io.nmea

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.Charset
import kotlin.coroutines.coroutineContext // <-- IMPORT CORRETTO

/**
 * Connettore NMEA 0183 via TCP o UDP.
 * Esempi:
 *  - TCP: host=192.168.1.50, port=10110 (Digital Yacht WLN10/Smart, multiplexer vari)
 *  - UDP: port=2000 (broadcast)
 */
class Nmea0183Client(
    private val host: String?,
    private val port: Int,
    private val protocol: Protocol
) {
    enum class Protocol { TCP, UDP }

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = when (protocol) {
            Protocol.TCP -> scope.launch(Dispatchers.IO) { runTcp() }
            Protocol.UDP -> scope.launch(Dispatchers.IO) { runUdp() }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runTcp() {
        // loop di riconnessione finché il job è attivo
        while (coroutineContext.isActive) {
            try {
                val targetHost = host ?: return
                val sock = Socket()
                sock.connect(InetSocketAddress(targetHost, port), 4000)

                val reader = BufferedReader(
                    InputStreamReader(sock.getInputStream(), Charset.forName("US-ASCII"))
                )

                while (coroutineContext.isActive && !sock.isClosed) {
                    val line = reader.readLine() ?: break
                    Nmea0183Parser.feed(line)
                }

                try { sock.close() } catch (_: Exception) {}
            } catch (_: Exception) {
                // backoff semplice prima di ritentare
                delay(1000)
            }
        }
    }

    private suspend fun runUdp() {
        // ascolta pacchetti sulla porta finché il job è attivo
        val buf = ByteArray(2048)
        val socket = DatagramSocket(port)
        socket.soTimeout = 0
        val packet = DatagramPacket(buf, buf.size)
        try {
            while (coroutineContext.isActive) {
                socket.receive(packet)
                val s = String(packet.data, 0, packet.length, Charset.forName("US-ASCII"))
                s.lineSequence().forEach { Nmea0183Parser.feed(it) }
            }
        } catch (_: Exception) {
            // chiudi silenziosamente su cancellazione/errore
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }
}
