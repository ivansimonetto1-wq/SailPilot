package com.perseitech.sailpilot.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.regatta.*

@Composable
fun RegattaSimulationPanel(
    course: RegattaCourse,
    twdDeg: Double?,
    twsKn: Double?,
    onClose: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = onClose) { Text("Chiudi") }
        },
        title = { Text("Simulazione regata") },
        text = {
            if (twdDeg == null || twsKn == null) {
                Text(
                    "Vento vero non disponibile (TWD/TWS mancanti).\n" +
                            "Collega strumenti NMEA/Signal K o imposta meteo manuale."
                )
            } else {
                val res = RegattaSimulator.simulate(course, twdDeg, twsKn)
                if (res == null || res.legs.isEmpty()) {
                    Text("Campo non completo.\nImposta almeno linea di partenza e una boa.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Tempo totale stimato: ${"%.1f".format(res.totalTimeMin)} min")
                        Spacer(Modifier.height(4.dp))
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(res.legs) { leg ->
                                LegCard(leg)
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LegCard(leg: SimLeg) {
    Card {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "Distanza: ${"%.2f".format(leg.distanceNm)} nm  |  " +
                        "TWA: ${"%.0f".format(leg.twaDeg)}°"
            )

            val typeLabel = when (leg.legType) {
                LegType.UPWIND   -> "Bolina"
                LegType.REACH    -> "Traverso / Lasco"
                LegType.DOWNWIND -> "Poppa"
            }
            Text("Fase: $typeLabel")

            val sailLabel = when (leg.sail) {
                SailChoice.MAIN_JIB  -> "Randa + Fiocco"
                SailChoice.GENOA     -> "Randa + Genoa"
                SailChoice.CODE_ZERO -> "Randa + Code 0"
                SailChoice.GENNAKER  -> "Randa + Gennaker"
                SailChoice.SPI       -> "Randa + Spinnaker"
                SailChoice.STAYSAIL  -> "Randa + Staysail"
            }
            Text("Vele suggerite: $sailLabel")
            Text("Velocità stimata: ${"%.1f".format(leg.estSpeedKn)} kn")
            Text("Tempo tratta: ${"%.1f".format(leg.estTimeMin)} min")
        }
    }
}
