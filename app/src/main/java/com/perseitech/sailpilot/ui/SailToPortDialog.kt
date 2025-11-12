package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.ports.Port

@Composable
fun SailToPortDialog(
    ports: List<Port>,
    onDismiss: () -> Unit,
    onConfirm: (fromPort: Port?, useGpsAsStart: Boolean, toPort: Port) -> Unit
) {
    var useGps by remember { mutableStateOf(true) }
    var from by remember { mutableStateOf<Port?>(null) }
    var to by remember { mutableStateOf<Port?>(null) }
    var query by remember { mutableStateOf("") }

    val filtered = remember(ports, query) {
        if (query.isBlank()) ports else ports.filter {
            val q = query.trim().lowercase()
            it.name.lowercase().contains(q) ||
                    (it.unlocode ?: "").lowercase().contains(q) ||
                    it.country.lowercase().contains(q)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sail to Port") },
        text = {
            Column {
                Row {
                    FilterChip(selected = useGps, onClick = { useGps = true; from = null }, label = { Text("From: GPS") })
                    FilterChip(selected = !useGps, onClick = { useGps = false }, label = { Text("From: Porto") })
                }

                if (!useGps) {
                    Text("Seleziona Porto di Partenza")
                    PortPickerList(ports = filtered, selected = from, onPick = { from = it })
                }

                Divider()
                Text("Seleziona Porto di Arrivo")
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Cerca porto (nome/UNLOCODE/paese)") },
                    singleLine = true
                )
                PortPickerList(ports = filtered, selected = to, onPick = { to = it })
            }
        },
        confirmButton = {
            TextButton(
                enabled = (useGps || from != null) && to != null,
                onClick = { onConfirm(from, useGps, to!!) }
            ) { Text("Imposta rotta") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun PortPickerList(
    ports: List<Port>,
    selected: Port?,
    onPick: (Port) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 100.dp, max = 240.dp)
    ) {
        items(ports, key = { it.unlocode ?: it.name }) { p ->
            ListItem(
                headlineContent = { Text("${p.name} (${p.country})") },
                supportingContent = { Text("UNLOCODE: ${p.unlocode ?: "—"}  •  ${"%.4f".format(p.lat)}, ${"%.4f".format(p.lon)}") },
                trailingContent = { RadioButton(selected = selected == p, onClick = { onPick(p) }) },
                modifier = Modifier.padding(vertical = 2.dp)
            )
            Divider()
        }
    }
}
