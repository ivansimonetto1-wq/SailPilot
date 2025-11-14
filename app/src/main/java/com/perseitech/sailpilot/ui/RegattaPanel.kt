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

@Composable
fun RegattaPanel(
    sogKn: Double?,
    cogDeg: Double?,
    brgToWpDeg: Double?,
    distToWpNm: Double?,
    etaToWpText: String?
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
            // HEADER
            Text(
                "Regatta dashboard",
                color = MaterialTheme.colorScheme.primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            // GRID ESA-STYLE
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RegattaTile(
                        modifier = Modifier.weight(1f),
                        title = "SOG",
                        value = sogKn?.let { "${it.roundToInt()} kn" } ?: "--",
                        accent = MaterialTheme.colorScheme.primary
                    )
                    RegattaTile(
                        modifier = Modifier.weight(1f),
                        title = "COG",
                        value = cogDeg?.let { "${it.roundToInt()}°" } ?: "--",
                        accent = MaterialTheme.colorScheme.secondary
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    RegattaTile(
                        modifier = Modifier.weight(1f),
                        title = "BRG→WP",
                        value = brgToWpDeg?.let { "${it.roundToInt()}°" } ?: "--",
                        accent = Color(0xFF00E676)
                    )
                    RegattaTile(
                        modifier = Modifier.weight(1f),
                        title = "DIST WP",
                        value = distToWpNm?.let { String.format("%.2f nm", it) } ?: "--",
                        accent = Color(0xFFFF5252)
                    )
                }
            }

            // ETA
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.medium
                    )
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "ETA: ${etaToWpText ?: "--"}",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RegattaTile(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .heightIn(min = 90.dp)
            .background(
                color = MaterialTheme.colorScheme.surface,
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
            // NUMERI: usano il primary → in tema notte saranno ROSSI o VERDI
            color = MaterialTheme.colorScheme.primary,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
