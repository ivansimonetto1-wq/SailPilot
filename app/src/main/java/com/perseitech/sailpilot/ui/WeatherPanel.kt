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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Snapshot meteo unificato (strumenti di bordo + Open-Meteo fallback)
 */
data class WeatherSnapshot(
    val twsKn: Double?,      // True Wind Speed
    val twdDeg: Double?,     // True Wind Direction
    val sogKn: Double?,      // Speed over ground
    val cogDeg: Double?,     // Course over ground
    val seaState: String?,   // descrizione onda/sea state
    val tide: String?,       // descrizione maree
    val providerName: String,
    val isForecast: Boolean
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
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Weather – $modeLabel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Text(
                text = "Source: ${snapshot.providerName}" +
                        if (snapshot.isForecast) " (forecast)" else " (live)",
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                fontSize = 12.sp
            )

            Spacer(Modifier.height(8.dp))

            // GRID 2x2 stile strumento
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherTile(
                        modifier = Modifier.weight(1f),
                        title = "TWS",
                        value = snapshot.twsKn?.let { "${it.roundToInt()} kn" } ?: "--",
                        accent = Color(0xFF00E676)
                    )
                    WeatherTile(
                        modifier = Modifier.weight(1f),
                        title = "TWD",
                        value = snapshot.twdDeg?.let { "${it.roundToInt()}°" } ?: "--",
                        accent = Color(0xFF29B6F6)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    WeatherTile(
                        modifier = Modifier.weight(1f),
                        title = "SOG",
                        value = snapshot.sogKn?.let { "${it.roundToInt()} kn" } ?: "--",
                        accent = Color(0xFFFFC107)
                    )
                    WeatherTile(
                        modifier = Modifier.weight(1f),
                        title = "COG",
                        value = snapshot.cogDeg?.let { "${it.roundToInt()}°" } ?: "--",
                        accent = Color(0xFFFF5252)
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Mare e maree
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    "Sea state",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    snapshot.seaState ?: "N/D",
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    "Tide",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    snapshot.tide ?: "N/D",
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun WeatherTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .heightIn(min = 90.dp)
            .background(
                color = Color(0xFF001822),
                shape = MaterialTheme.shapes.medium
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title,
            color = accent,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            value,
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
