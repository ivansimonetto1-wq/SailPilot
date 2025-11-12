package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsDialog(
    draftMInit: Double,
    lengthMInit: Double,
    beamMInit: Double,
    seaRatesApiKeyInit: String?,
    onDismiss: () -> Unit,
    onSave: (draftM: Double, lengthM: Double, beamM: Double, apiKey: String?) -> Unit
) {
    var draft by remember { mutableStateOf(draftMInit.toString()) }
    var length by remember { mutableStateOf(lengthMInit.toString()) }
    var beam by remember { mutableStateOf(beamMInit.toString()) }
    var apiKey by remember { mutableStateOf(seaRatesApiKeyInit.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni Barca") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("Pescaggio (m)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = length,
                    onValueChange = { length = it },
                    label = { Text("Lunghezza LOA (m)") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = beam,
                    onValueChange = { beam = it },
                    label = { Text("Larghezza (m)") },
                    singleLine = true
                )
                Divider(Modifier.padding(top = 4.dp))
                Text("SeaRates API", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key (World Sea Ports)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val d = draft.toDoubleOrNull() ?: draftMInit
                val l = length.toDoubleOrNull() ?: lengthMInit
                val b = beam.toDoubleOrNull() ?: beamMInit
                onSave(d, l, b, apiKey.ifBlank { null })
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
