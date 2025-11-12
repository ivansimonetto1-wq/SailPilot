package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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

@Composable
fun InstrumentPanel(
    headingDeg: Double?,           // bearing da GPS (deg)
    sogKn: Double?,                // speed over ground (kn)
    cogDeg: Double?,               // course over ground (deg) — normalmente = heading
    latText: String?,              // “N 41.12345°”
    lonText: String?,              // “E 12.12345°”
    distNextNm: Double?,           // distanza al prossimo waypoint in NM
    brgNextDeg: Double?,           // rotta (bearing) verso prossimo waypoint
    etaNextText: String?           // es: “0h 23m”
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BigBlock(title = "Heading M", value = headingDeg?.let { "${it.toInt()}°" } ?: "—")
                BigBlock(title = "SOG", value = sogKn?.let { "%.1f kn".format(it) } ?: "—")
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BigBlock(title = "COG M", value = cogDeg?.let { "${it.toInt()}°" } ?: "—")
                BigBlock(
                    title = "Lat/Lon",
                    value = if (latText != null && lonText != null) "$latText   •   $lonText" else "—",
                    small = true
                )
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                BigBlock(title = "Dist → WP", value = distNextNm?.let { "%.2f NM".format(it) } ?: "—")
                BigBlock(title = "Course → WP", value = brgNextDeg?.let { "${it.toInt()}°" } ?: "—")
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start
            ) {
                BigBlock(title = "ETA → WP", value = etaNextText ?: "—", wide = true)
            }
        }
    }
}

@Composable
private fun BigBlock(
    title: String,
    value: String,
    small: Boolean = false,
    wide: Boolean = false
) {
    Column(
        modifier = Modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 0.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(
            text = title,
            color = Color.LightGray,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = Color.White,
            fontSize = if (small) 28.sp else 56.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
