package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.io.nmea.Nmea0183Client
import com.perseitech.sailpilot.io.signalk.SignalKClient
import com.perseitech.sailpilot.settings.SettingsRepository
import com.perseitech.sailpilot.settings.SettingsRepository.Connection
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun ConnectionsPanel(appScope: CoroutineScope) {
    val ctx = LocalContext.current
    val repo = remember { SettingsRepository(ctx) }

    // Stato: lista persistita
    val savedConnections by repo.connections().collectAsState(initial = emptyList())
    var editList by remember(savedConnections) { mutableStateOf(savedConnections) }

    // runtime: connessioni attive
    val nmeaClients = remember { mutableStateMapOf<String, Nmea0183Client>() }
    val skClients   = remember { mutableStateMapOf<String, SignalKClient>() }

    fun connect(c: Connection) {
        when (c.kind) {
            Connection.Kind.NMEA0183 -> {
                val proto = c.protocol ?: Connection.NmeaProtocol.TCP
                val port = c.port ?: return
                val client = Nmea0183Client(
                    host = if (proto == Connection.NmeaProtocol.TCP) c.host else null,
                    port = port,
                    protocol = if (proto == Connection.NmeaProtocol.TCP) Nmea0183Client.Protocol.TCP else Nmea0183Client.Protocol.UDP
                )
                client.start(appScope)
                nmeaClients[c.id] = client
            }
            Connection.Kind.SIGNALK -> {
                val url = c.wsUrl ?: return
                val client = SignalKClient(url, bearerToken = c.token)
                client.start(appScope)
                skClients[c.id] = client
            }
            Connection.Kind.NMEA2000 -> {
                when (c.bridge) {
                    Connection.Bridge.SIGNALK -> {
                        val url = c.wsUrl ?: return
                        val client = SignalKClient(url, bearerToken = c.token)
                        client.start(appScope)
                        skClients[c.id] = client
                    }
                    Connection.Bridge.NMEA0183 -> {
                        val proto = c.protocol ?: Connection.NmeaProtocol.TCP
                        val port = c.port ?: return
                        val client = Nmea0183Client(
                            host = if (proto == Connection.NmeaProtocol.TCP) c.host else null,
                            port = port,
                            protocol = if (proto == Connection.NmeaProtocol.TCP) Nmea0183Client.Protocol.TCP else Nmea0183Client.Protocol.UDP
                        )
                        client.start(appScope)
                        nmeaClients[c.id] = client
                    }
                    null -> { /* non configurato */ }
                }
            }
        }
    }

    fun disconnect(c: Connection) {
        when (c.kind) {
            Connection.Kind.NMEA0183 -> nmeaClients.remove(c.id)?.stop()
            Connection.Kind.SIGNALK  -> skClients.remove(c.id)?.stop()
            Connection.Kind.NMEA2000 -> {
                // potrebbe aver creato un client SK o NMEA0183 a seconda del bridge
                skClients.remove(c.id)?.stop()
                nmeaClients.remove(c.id)?.stop()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(16.dp)
    ) {
        Text("Data Connections", color = Color.White, style = MaterialTheme.typography.titleLarge)

        Spacer(Modifier.height(12.dp))

        // Lista connessioni
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(editList, key = { it.id }) { c ->
                ConnectionCard(
                    c = c,
                    nmeaRunning = nmeaClients.containsKey(c.id),
                    skRunning = skClients.containsKey(c.id),
                    onConnect = { updated -> connect(updated) },
                    onDisconnect = { target -> disconnect(target) },
                    onUpdate = { updated ->
                        editList = editList.map { if (it.id == updated.id) updated else it }
                    },
                    onDelete = {
                        disconnect(c)
                        editList = editList.filterNot { it.id == c.id }
                    }
                )
            }
        }

        // Pulsanti aggiunta/salvataggio
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = {
                val id = UUID.randomUUID().toString()
                editList = editList + Connection(
                    id = id,
                    kind = Connection.Kind.NMEA0183,
                    name = "NMEA ${id.take(4)}",
                    host = "192.168.1.50",
                    port = 10110,
                    protocol = Connection.NmeaProtocol.TCP
                )
            }) { Text("Aggiungi NMEA 0183") }

            OutlinedButton(onClick = {
                val id = UUID.randomUUID().toString()
                editList = editList + Connection(
                    id = id,
                    kind = Connection.Kind.SIGNALK,
                    name = "SK ${id.take(4)}",
                    wsUrl = "ws://10.10.10.1:3000/signalk/v1/stream?subscribe=self",
                    token = ""
                )
            }) { Text("Aggiungi Signal K") }

            OutlinedButton(onClick = {
                val id = UUID.randomUUID().toString()
                // default: N2K via Signal K
                editList = editList + Connection(
                    id = id,
                    kind = Connection.Kind.NMEA2000,
                    name = "N2K ${id.take(4)}",
                    bridge = Connection.Bridge.SIGNALK,
                    wsUrl = "ws://10.10.10.1:3000/signalk/v1/stream?subscribe=self",
                    token = ""
                )
            }) { Text("Aggiungi NMEA 2000") }

            Spacer(Modifier.weight(1f))

            val scope = rememberCoroutineScope()
            Button(onClick = { scope.launch { repo.saveConnections(editList) } }) {
                Text("Salva")
            }
        }
    }
}

/* ---------------- UI di una singola scheda ---------------- */

@Composable
private fun ConnectionCard(
    c: Connection,
    nmeaRunning: Boolean,
    skRunning: Boolean,
    onConnect: (Connection) -> Unit,
    onDisconnect: (Connection) -> Unit,
    onUpdate: (Connection) -> Unit,
    onDelete: () -> Unit
) {
    val running = when (c.kind) {
        Connection.Kind.NMEA0183 -> nmeaRunning
        Connection.Kind.SIGNALK  -> skRunning
        Connection.Kind.NMEA2000 -> nmeaRunning || skRunning
    }

    Surface(color = Color(0xFF181818)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val sub = when (c.kind) {
                    Connection.Kind.NMEA0183 -> "NMEA 0183"
                    Connection.Kind.SIGNALK  -> "Signal K"
                    Connection.Kind.NMEA2000 -> "NMEA 2000 (via ${c.bridge ?: "?"})"
                }
                Text("$sub • ${c.name}", color = Color.White)
                Text(if (running) "Connesso" else "Fermo", color = if (running) Color(0xFF00E676) else Color.LightGray)
            }

            when (c.kind) {
                Connection.Kind.NMEA0183 -> {
                    var host by remember(c.id) { mutableStateOf(c.host.orEmpty()) }
                    var port by remember(c.id) { mutableStateOf((c.port ?: 10110).toString()) }
                    var isTcp by remember(c.id) { mutableStateOf(c.protocol != Connection.NmeaProtocol.UDP) }

                    OutlinedTextField(host, { host = it }, label = { Text("Host/IP") }, singleLine = true)
                    OutlinedTextField(port, { port = it }, label = { Text("Porta") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = isTcp, onClick = { isTcp = true }, label = { Text("TCP") })
                        FilterChip(selected = !isTcp, onClick = { isTcp = false }, label = { Text("UDP") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { if (!running) onConnect(c.copy(host = host.ifBlank { null }, port = port.toIntOrNull(), protocol = if (isTcp) Connection.NmeaProtocol.TCP else Connection.NmeaProtocol.UDP)) }) { Text("Connetti") }
                        OutlinedButton(onClick = { onDisconnect(c) }) { Text("Disconnetti") }
                        OutlinedButton(onClick = {
                            onUpdate(c.copy(
                                host = host.ifBlank { null },
                                port = port.toIntOrNull(),
                                protocol = if (isTcp) Connection.NmeaProtocol.TCP else Connection.NmeaProtocol.UDP
                            ))
                        }) { Text("Aggiorna") }
                        OutlinedButton(onClick = onDelete) { Text("Elimina") }
                    }
                }
                Connection.Kind.SIGNALK -> {
                    var url by remember(c.id) { mutableStateOf(c.wsUrl.orEmpty()) }
                    var token by remember(c.id) { mutableStateOf(c.token.orEmpty()) }

                    OutlinedTextField(url, { url = it }, label = { Text("WS URL") })
                    OutlinedTextField(token, { token = it }, label = { Text("Token (Bearer)") }, visualTransformation = PasswordVisualTransformation())

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { if (!running) onConnect(c.copy(wsUrl = url, token = token.ifBlank { null })) }) { Text("Connetti") }
                        OutlinedButton(onClick = { onDisconnect(c) }) { Text("Disconnetti") }
                        OutlinedButton(onClick = { onUpdate(c.copy(wsUrl = url, token = token.ifBlank { null })) }) { Text("Aggiorna") }
                        OutlinedButton(onClick = onDelete) { Text("Elimina") }
                    }
                }
                Connection.Kind.NMEA2000 -> {
                    var bridge by remember(c.id) { mutableStateOf(c.bridge ?: Connection.Bridge.SIGNALK) }

                    // Selezione bridge
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilterChip(selected = bridge == Connection.Bridge.SIGNALK, onClick = { bridge = Connection.Bridge.SIGNALK }, label = { Text("via Signal K") })
                        FilterChip(selected = bridge == Connection.Bridge.NMEA0183, onClick = { bridge = Connection.Bridge.NMEA0183 }, label = { Text("via NMEA 0183") })
                    }

                    if (bridge == Connection.Bridge.SIGNALK) {
                        var url by remember(c.id) { mutableStateOf(c.wsUrl.orEmpty()) }
                        var token by remember(c.id) { mutableStateOf(c.token.orEmpty()) }
                        OutlinedTextField(url, { url = it }, label = { Text("Signal K WS URL") })
                        OutlinedTextField(token, { token = it }, label = { Text("Token (Bearer)") }, visualTransformation = PasswordVisualTransformation())

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = { if (!running) onConnect(c.copy(bridge = bridge, wsUrl = url, token = token.ifBlank { null })) }) { Text("Connetti") }
                            OutlinedButton(onClick = { onDisconnect(c) }) { Text("Disconnetti") }
                            OutlinedButton(onClick = { onUpdate(c.copy(bridge = bridge, wsUrl = url, token = token.ifBlank { null })) }) { Text("Aggiorna") }
                            OutlinedButton(onClick = onDelete) { Text("Elimina") }
                        }
                    } else {
                        // bridge = NMEA0183
                        var host by remember(c.id) { mutableStateOf(c.host.orEmpty()) }
                        var port by remember(c.id) { mutableStateOf((c.port ?: 10110).toString()) }
                        var isTcp by remember(c.id) { mutableStateOf(c.protocol != Connection.NmeaProtocol.UDP) }

                        OutlinedTextField(host, { host = it }, label = { Text("Gateway Host/IP") }, singleLine = true)
                        OutlinedTextField(port, { port = it }, label = { Text("Porta") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            FilterChip(selected = isTcp, onClick = { isTcp = true }, label = { Text("TCP") })
                            FilterChip(selected = !isTcp, onClick = { isTcp = false }, label = { Text("UDP") })
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(onClick = {
                                if (!running) onConnect(c.copy(
                                    bridge = bridge,
                                    host = host.ifBlank { null },
                                    port = port.toIntOrNull(),
                                    protocol = if (isTcp) Connection.NmeaProtocol.TCP else Connection.NmeaProtocol.UDP
                                ))
                            }) { Text("Connetti") }
                            OutlinedButton(onClick = { onDisconnect(c) }) { Text("Disconnetti") }
                            OutlinedButton(onClick = {
                                onUpdate(c.copy(
                                    bridge = bridge,
                                    host = host.ifBlank { null },
                                    port = port.toIntOrNull(),
                                    protocol = if (isTcp) Connection.NmeaProtocol.TCP else Connection.NmeaProtocol.UDP
                                ))
                            }) { Text("Aggiorna") }
                            OutlinedButton(onClick = onDelete) { Text("Elimina") }
                        }
                    }

                    Text(
                        "Nota: Android non si collega direttamente al bus CAN. Per NMEA 2000 usa un gateway (es. iKommunicate → Signal K; WLN10/Smart → NMEA 0183).",
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
