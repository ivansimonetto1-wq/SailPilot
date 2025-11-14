package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun RegattaOverviewPanel(
    sogKn: Double?,
    cogDeg: Double?,
    twsKn: Double?,
    twdDeg: Double?,
    twaDeg: Double?,
    classLabel: String,
    onOpenRegattaMap: () -> Unit,
    onOpenSailAdvisor: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Regatta · $classLabel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            // riga 1: Speed / Wind
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BigValueCard(
                    title = "Boat speed",
                    value = sogKn?.let { String.format("%.1f", it) } ?: "–",
                    unit = "kn",
                    modifier = Modifier.weight(1f)
                )
                BigValueCard(
                    title = "TWS",
                    value = twsKn?.let { String.format("%.1f", it) } ?: "–",
                    unit = "kn",
                    modifier = Modifier.weight(1f)
                )
            }

            // riga 2: COG / TWA
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BigValueCard(
                    title = "COG",
                    value = cogDeg?.let { String.format("%.0f", it) } ?: "–",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
                BigValueCard(
                    title = "TWA",
                    value = twaDeg?.let { String.format("%.0f", it) } ?: "–",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
            }

            // riga 3: Wind shift (semplice diff COG vs TWD, solo come indicazione)
            val shift = if (cogDeg != null && twdDeg != null) {
                var d = cogDeg - twdDeg
                while (d > 180) d -= 360
                while (d < -180) d += 360
                d
            } else null

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                BigValueCard(
                    title = "TWD",
                    value = twdDeg?.let { String.format("%.0f", it) } ?: "–",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
                BigValueCard(
                    title = "Shift",
                    value = shift?.let { "${if (it > 0) "+" else ""}${it.roundToInt()}°" } ?: "–",
                    unit = "",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // sezione "virtual circuit"
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Virtual race course",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Imposta la starting line, le boe e il percorso nella schermata “Regatta Map”. " +
                                "Qui verranno mostrate solo le metriche principali di gara."
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenRegattaMap
                        ) {
                            Text("Edit campo (Regatta Map)")
                        }
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onOpenSailAdvisor
                        ) {
                            Text("Sail advisor")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BigValueCard(
    title: String,
    value: String,
    unit: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold
                )
                if (unit.isNotBlank()) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = unit,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }
}
