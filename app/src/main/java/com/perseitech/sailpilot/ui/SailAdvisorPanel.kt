package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.regatta.BoatClass
import com.perseitech.sailpilot.regatta.RegattaSettings
import com.perseitech.sailpilot.regatta.SailAdvisor

@Composable
fun SailAdvisorPanel(
    settings: RegattaSettings,
    boatClass: BoatClass?,
    twsKnFromSensors: Double?,
    twaDegFromSensors: Double?
) {
    // consentiamo override manuale se strumenti non ci sono
    var tws by remember { mutableStateOf(twsKnFromSensors?.toString() ?: "") }
    var twa by remember { mutableStateOf(twaDegFromSensors?.toString() ?: "") }

    val twsV = tws.toDoubleOrNull()
    val twaV = twa.toDoubleOrNull()

    val advice = remember(twsV, twaV, settings, boatClass) {
        SailAdvisor.suggest(twsV, twaV, settings, boatClass)
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Sail Advisor", style = MaterialTheme.typography.titleLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = tws,
                onValueChange = { tws = it },
                label = { Text("TWS (kn)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = twa,
                onValueChange = { twa = it },
                label = { Text("TWA (°)") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        Divider()
        Text(advice.headline, style = MaterialTheme.typography.titleMedium)
        advice.details.forEach { line ->
            Text("• $line")
        }
        Spacer(Modifier.weight(1f))
        Text(
            "Note: se carichi polari reali per la tua classe (via PolarRepo) i target velocità/angoli diventeranno più precisi.",
            color = Color.Gray
        )
    }
}
