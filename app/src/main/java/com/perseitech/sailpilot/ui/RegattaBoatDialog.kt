package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.regatta.BoatClass
import com.perseitech.sailpilot.regatta.HullType
import com.perseitech.sailpilot.regatta.RegattaSettings
import com.perseitech.sailpilot.regatta.SailMaterial

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RegattaBoatDialog(
    classes: List<BoatClass>,
    current: RegattaSettings,
    onDismiss: () -> Unit,
    onSave: (RegattaSettings) -> Unit
) {
    var selClass by remember { mutableStateOf(current.classId) }
    var hull by remember { mutableStateOf(current.hull) }
    var material by remember { mutableStateOf(current.sailMaterial) }
    var inv by remember { mutableStateOf(current.inventory.ifEmpty { setOf("J1","J2","J3","Code0","A2","A3","A4","Staysail") }) }
    var query by remember { mutableStateOf("") }

    val list = remember(classes, query) {
        if (query.isBlank()) classes else classes.filter {
            it.name.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Impostazioni Regatta") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

                Text("Classe barca")
                OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Cerca classe") }, singleLine = true)
                LazyColumn(modifier = Modifier.heightIn(120.dp, 220.dp)) {
                    items(list, key = { it.id }) { bc ->
                        ListItem(
                            headlineContent = { Text(bc.name) },
                            supportingContent = {
                                val extras = buildString {
                                    bc.loa?.let { append("LOA $it  ") }
                                    bc.draft?.let { append("Draft $it  ") }
                                    bc.rig?.let { append("• $it") }
                                }
                                if (extras.isNotBlank()) Text(extras)
                            },
                            trailingContent = {
                                RadioButton(selected = selClass == bc.id, onClick = { selClass = bc.id })
                            }
                        )
                        Divider()
                    }
                }

                Text("Tipo scafo & materiale vele")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownMenuField("Hull", hull.name, HullType.values().map { it.name }) { sel ->
                        hull = HullType.valueOf(sel)
                    }
                    DropdownMenuField("Sails", material.name, SailMaterial.values().map { it.name }) { sel ->
                        material = SailMaterial.valueOf(sel)
                    }
                }

                Text("Inventario vele")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("J1","J2","J3","Code0","A1","A2","A3","A4","S2","S4","Staysail").forEach { tag ->
                        FilterChip(
                            selected = inv.contains(tag),
                            onClick = { inv = if (inv.contains(tag)) inv - tag else inv + tag },
                            label = { Text(tag) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(RegattaSettings(classId = selClass, hull = hull, sailMaterial = material, inventory = inv))
            }) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DropdownMenuField(
    label: String,
    value: String,
    options: List<String>,
    onPick: (String) -> Unit
) {
    var open by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = open, onExpandedChange = { open = it }) {
        OutlinedTextField(
            value = value, onValueChange = {}, label = { Text(label) },
            readOnly = true, modifier = Modifier.menuAnchor().width(160.dp)
        )
        ExposedDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onPick(opt); open = false })
            }
        }
    }
}
