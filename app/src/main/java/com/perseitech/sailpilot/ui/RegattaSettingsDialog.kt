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
import com.perseitech.sailpilot.regatta.RegattaPolars

@Composable
fun RegattaSettingsDialog(
    currentClass: RegattaPolars.ClassId,
    isafCountdownEnabledInit: Boolean,
    onDismiss: () -> Unit,
    onSave: (RegattaPolars.ClassId, Boolean) -> Unit
) {
    var selectedClass by remember { mutableStateOf(currentClass) }
    var isafEnabled by remember { mutableStateOf(isafCountdownEnabledInit) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(selectedClass, isafEnabled) }) {
                Text("Salva")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
        title = { Text("Impostazioni Regatta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Classe barca", style = MaterialTheme.typography.titleMedium)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    RegattaPolars.ClassId.values().forEach { c ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedClass = c }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = (selectedClass == c),
                                onClick = { selectedClass = c }
                            )
                            Text(c.label)
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Countdown ISAF 5-4-1-0", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isafEnabled = !isafEnabled }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RadioButton(
                        selected = isafEnabled,
                        onClick = { isafEnabled = !isafEnabled }
                    )
                    Text(if (isafEnabled) "Attivo" else "Disattivato")
                }
            }
        }
    )
}
