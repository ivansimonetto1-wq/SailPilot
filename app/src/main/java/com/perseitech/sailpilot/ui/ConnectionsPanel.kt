package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import com.perseitech.sailpilot.io.nmea.Nmea0183Client
import com.perseitech.sailpilot.io.signalk.SignalKClient

@Composable
fun ConnectionsPanel(
    appScope: CoroutineScope
) {
    // NMEA TCP
    val nmeaHost = remember { mutableStateOf("192.168.0.10") }
    val nmeaPort = remember { mutableStateOf("10110") }
    val nmeaStatus = remember { mutableStateOf("disconnected") }
    val nmeaClient = remember { mutableStateOf<Nmea0183Client?>(null) }

    // Signal K WS
    val skUrl = remember { mutableStateOf("ws://192.168.0.10:3000/signalk/v1/stream?subscribe=delta") }
    val skToken = remember { mutableStateOf("") }
    val skStatus = remember { mutableStateOf("disconnected") }
    val skClient = remember { mutableStateOf<SignalKClient?>(null) }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("NMEA 0183 (TCP)")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(nmeaHost.value, onValueChange = { nmeaHost.value = it }, label = { Text("Host") }, modifier = Modifier.weight(1f))
            OutlinedTextField(nmeaPort.value, onValueChange = { nmeaPort.value = it }, label = { Text("Porta") }, modifier = Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                nmeaClient.value?.stop()
                val c = Nmea0183Client(nmeaHost.value, nmeaPort.value.toIntOrNull() ?: 10110)
                nmeaClient.value = c
                nmeaStatus.value = "connecting…"
                c.start(appScope) // <- NMEA vuole lo scope
                nmeaStatus.value = "connected"
            }) { Text("Connetti NMEA") }
            Button(onClick = {
                nmeaClient.value?.stop()
                nmeaClient.value = null
                nmeaStatus.value = "disconnected"
            }) { Text("Disconnetti NMEA") }
        }
        Text("Stato NMEA: ${nmeaStatus.value}")

        Spacer(Modifier.height(16.dp))
        Text("Signal K (WebSocket)")
        OutlinedTextField(skUrl.value, onValueChange = { skUrl.value = it }, label = { Text("URL ws://…/signalk/v1/stream?subscribe=delta") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(skToken.value, onValueChange = { skToken.value = it }, label = { Text("Token (opzionale)") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                skClient.value?.stop()
                val c = SignalKClient(skUrl.value, token = skToken.value.ifBlank { null })
                skClient.value = c
                skStatus.value = "connecting…"
                c.start() // <- Signal K ora ha start() senza argomenti
                skStatus.value = "connected"
            }) { Text("Connetti Signal K") }
            Button(onClick = {
                skClient.value?.stop()
                skClient.value = null
                skStatus.value = "disconnected"
            }) { Text("Disconnetti Signal K") }
        }
        Text("Stato Signal K: ${skStatus.value}")
    }
}
