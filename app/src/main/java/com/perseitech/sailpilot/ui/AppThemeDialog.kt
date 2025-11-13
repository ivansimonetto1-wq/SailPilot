package com.perseitech.sailpilot.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.settings.AppUiSettings
import com.perseitech.sailpilot.settings.NightAccent

@Composable
fun AppThemeDialog(
    current: AppUiSettings,
    onDismiss: () -> Unit,
    onSave: (Boolean, NightAccent) -> Unit
) {
    var dark by remember { mutableStateOf(current.darkTheme) }
    var accent by remember { mutableStateOf(current.nightAccent) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(dark, accent) }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        title = { Text("Tema applicazione") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Modalità schermo", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { dark = false }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = !dark, onClick = { dark = false })
                        Text("Chiaro (giorno)")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { dark = true }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(selected = dark, onClick = { dark = true })
                        Text("Scuro (notte)")
                    }
                }

                Spacer(Modifier.height(8.dp))

                Text("Colore numeri (tema notte)", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { accent = NightAccent.RED }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = accent == NightAccent.RED,
                            onClick = { accent = NightAccent.RED }
                        )
                        Text("Rosso")
                    }
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { accent = NightAccent.GREEN }
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        RadioButton(
                            selected = accent == NightAccent.GREEN,
                            onClick = { accent = NightAccent.GREEN }
                        )
                        Text("Verde")
                    }
                }
            }
        }
    )
}
