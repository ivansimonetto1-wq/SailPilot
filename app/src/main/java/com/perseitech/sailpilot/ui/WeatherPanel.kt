package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class WeatherSnapshot(
    val twsKn: Double?,
    val twdDeg: Double?,
    val sogKn: Double?,
    val cogDeg: Double?,
    val seaState: SeaStateSnapshot? = null,
    val tide: TideSnapshot? = null,
    val providerName: String = "",
    val isForecast: Boolean = false
)

data class SeaStateSnapshot(
    val hsMeters: Double?,
    val dirDeg: Double?,
    val periodSec: Double?
)

data class TideSnapshot(
    val levelMeters: Double?,
    val stationName: String?
)

@Composable
fun WeatherPanel(
    modeLabel: String,
    snapshot: WeatherSnapshot
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
                text = "Weather · $modeLabel",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold
            )

            // Prima riga: TWS / TWD
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "TWS",
                    value = snapshot.twsKn?.let { String.format("%.1f", it) } ?: "–",
                    unit = "kn",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "TWD",
                    value = snapshot.twdDeg?.let { String.format("%.0f", it) } ?: "–",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
            }

            // Seconda riga: SOG / COG
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    title = "SOG",
                    value = snapshot.sogKn?.let { String.format("%.1f", it) } ?: "–",
                    unit = "kn",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    title = "COG",
                    value = snapshot.cogDeg?.let { String.format("%.0f", it) } ?: "–",
                    unit = "°",
                    modifier = Modifier.weight(1f)
                )
            }

            // Terza riga: Sea state / Tide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SeaStateCard(
                    snapshot = snapshot.seaState,
                    modifier = Modifier.weight(1f)
                )
                TideCard(
                    snapshot = snapshot.tide,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = buildString {
                    append("Source: ${snapshot.providerName.ifBlank { "—" }}")
                    if (snapshot.isForecast) append(" (forecast)")
                },
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    unit: String? = null,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = value + (unit?.let { " $it" } ?: ""),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun SeaStateCard(
    snapshot: SeaStateSnapshot?,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Sea state",
                style = MaterialTheme.typography.labelMedium
            )
            Text("Hₛ: " + (snapshot?.hsMeters?.let { String.format("%.1f m", it) } ?: "–"))
            Text("Dir: " + (snapshot?.dirDeg?.let { String.format("%.0f°", it) } ?: "–"))
            Text("Period: " + (snapshot?.periodSec?.let { String.format("%.0f s", it) } ?: "–"))
        }
    }
}

@Composable
private fun TideCard(
    snapshot: TideSnapshot?,
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
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = "Tide",
                style = MaterialTheme.typography.labelMedium
            )
            Text("Level: " + (snapshot?.levelMeters?.let { String.format("%.2f m", it) } ?: "–"))
            Text("Station: " + (snapshot?.stationName ?: "–"))
        }
    }
}
