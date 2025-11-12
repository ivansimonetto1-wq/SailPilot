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
import kotlin.math.*

@Composable
fun RegattaPanel(
    sogKn: Double?,          // Speed Over Ground
    cogDeg: Double?,         // Course Over Ground
    brgToWpDeg: Double?,     // Bearing verso WP attivo
    distToWpNm: Double?,     // Distanza al WP
    etaToWpText: String?     // ETA formattata
) {
    val delta = if (cogDeg != null && brgToWpDeg != null) angleDiff(brgToWpDeg, cogDeg) else null
    val vmg = if (sogKn != null && delta != null) sogKn * cos(Math.toRadians(delta.absoluteValue)) else null
    val turn = delta?.let { if (it > 0) "Ruota ${it.toInt()}° → dritta" else if (it < 0) "Ruota ${(-it).toInt()}° → sinistra" else "In rotta" }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        color = Color.Black
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Block("SOG", sogKn?.let { "%.1f kn".format(it) } ?: "—")
                Block("COG", cogDeg?.let { "${it.toInt()}°" } ?: "—")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Block("BRG→WP", brgToWpDeg?.let { "${it.toInt()}°" } ?: "—")
                Block("Δ rotta", delta?.let { "${it.toInt()}°" } ?: "—")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Block("VMG→WP", vmg?.let { "%.1f kn".format(it) } ?: "—")
                Block("Dist→WP", distToWpNm?.let { "%.2f NM".format(it) } ?: "—")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                Block("ETA→WP", etaToWpText ?: "—", wide = true)
            }
            if (turn != null) {
                Text(turn, color = Color(0xFF00E676), fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Block(title: String, value: String, wide: Boolean = false) {
    Column(
        modifier = Modifier
            .then(if (wide) Modifier.fillMaxWidth() else Modifier.widthIn(min = 0.dp))
            .padding(6.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Text(title, color = Color.LightGray, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(value, color = Color.White, fontSize = 56.sp, fontWeight = FontWeight.Bold)
    }
}

private fun angleDiff(target: Double, current: Double): Double {
    var d = ((target - current + 540) % 360) - 180
    if (d < -180) d += 360.0
    return d
}
