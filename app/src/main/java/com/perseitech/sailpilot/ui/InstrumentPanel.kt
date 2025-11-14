package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Pannello strumenti semplice, tutto in colonna:
 * - Heading M
 * - COG
 * - SOG
 * - Lat/Lon
 * - Dist → WP
 * - Course → WP
 * - ETA → WP
 *
 * NESSUN uso di weight, solo padding e allineamenti.
 */
@Composable
fun InstrumentPanel(
    headingDeg: Double?,
    sogKn: Double?,
    cogDeg: Double?,
    latText: String?,
    lonText: String?,
    distNextNm: Double?,
    brgNextDeg: Double?,
    etaNextText: String?
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            InstrumentRow(
                label = "Heading M",
                value = headingDeg?.roundToInt()?.let { "$it°" } ?: "—"
            )
            InstrumentRow(
                label = "COG M",
                value = cogDeg?.roundToInt()?.let { "$it°" } ?: "—"
            )
            InstrumentRow(
                label = "SOG",
                value = sogKn?.let { String.format("%.1f kn", it) } ?: "—"
            )
            InstrumentRow(
                label = "Lat / Lon",
                value = if (latText != null && lonText != null)
                    "$latText   $lonText"
                else
                    "—",
                small = true
            )
            InstrumentRow(
                label = "Dist → WP",
                value = distNextNm?.let { String.format("%.2f NM", it) } ?: "—"
            )
            InstrumentRow(
                label = "Course → WP",
                value = brgNextDeg?.roundToInt()?.let { "$it°" } ?: "—"
            )
            InstrumentRow(
                label = "ETA → WP",
                value = etaNextText ?: "—"
            )
        }
    }
}

@Composable
private fun InstrumentRow(
    label: String,
    value: String,
    small: Boolean = false
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            textAlign = TextAlign.Left,
            fontSize = if (small) 22.sp else 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            lineHeight = if (small) 24.sp else 30.sp
        )
    }
}
