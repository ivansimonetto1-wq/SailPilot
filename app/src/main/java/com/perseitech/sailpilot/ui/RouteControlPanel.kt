package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.routing.GeoUtils
import com.perseitech.sailpilot.routing.LatLon
import kotlin.math.*

enum class UnitMode { NAUTICAL, METRIC }

/** Pannello nero ad alta visibilità con info rotta, ETA e legs. */
@Composable
fun RouteControlPanel(
    start: LatLon?,
    goal: LatLon?,
    path: List<LatLon>,
    speed: Double,               // velocità corrente (kn se NAUTICAL, km/h se METRIC)
    unitMode: UnitMode,          // modalità unità
    onUnitToggle: (UnitMode) -> Unit,
    onSpeedChange: (Double) -> Unit,
) {
    val bg = Color.Black
    val fg = Color.White

    val legs = remember(path) { computeLegs(path) }
    val totalMeters = legs.sumOf { it.distanceMeters }
    val totalNm = totalMeters / 1852.0
    val totalKm = totalMeters / 1000.0

    val etaHours = when (unitMode) {
        UnitMode.NAUTICAL -> if (speed > 0) totalNm / speed else Double.NaN
        UnitMode.METRIC   -> if (speed > 0) totalKm / speed else Double.NaN
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {

        // Header / Toggle unità
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Control Panel", color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SegmentedUnits(mode = unitMode, onChange = onUnitToggle)
        }

        // Riepilogo rotta
        SummaryRow(
            start = start,
            goal = goal,
            totalMeters = totalMeters,
            etaHours = etaHours,
            speed = speed,
            unitMode = unitMode,
            fg = fg
        )

        // Velocità: step +/- ad alta visibilità
        SpeedRow(speed = speed, unitMode = unitMode, fg = fg, onSpeedChange = onSpeedChange)

        // Lista tratte (leg)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
        ) {
            Column(Modifier.padding(12.dp)) {
                Text("Tratte (Legs)", color = fg, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                if (legs.isEmpty()) {
                    Text("Nessuna tratta", color = fg.copy(alpha = 0.7f))
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(legs) { idx, leg ->
                            LegRow(
                                index = idx + 1,
                                leg = leg,
                                unitMode = unitMode,
                                fg = fg
                            )
                        }
                    }
                }
            }
        }
    }
}

/* -------------------- UI components -------------------- */

@Composable
private fun SegmentedUnits(mode: UnitMode, onChange: (UnitMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color(0xFF1B1B1B), RoundedCornerShape(24.dp))
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UnitChip("Nautical", selected = mode == UnitMode.NAUTICAL) { onChange(UnitMode.NAUTICAL) }
        Spacer(Modifier.width(4.dp))
        UnitChip("Metric", selected = mode == UnitMode.METRIC) { onChange(UnitMode.METRIC) }
    }
}

@Composable
private fun UnitChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.White else Color(0xFF1B1B1B),
        tonalElevation = 0.dp
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = if (selected) Color.Black else Color.White
        )
    }
}

@Composable
private fun SummaryRow(
    start: LatLon?, goal: LatLon?, totalMeters: Double, etaHours: Double, speed: Double, unitMode: UnitMode, fg: Color
) {
    val distText = when (unitMode) {
        UnitMode.NAUTICAL -> "${fmt(totalMeters / 1852.0, 1)} NM"
        UnitMode.METRIC   -> "${fmt(totalMeters / 1000.0, 1)} km"
    }
    val speedText = when (unitMode) {
        UnitMode.NAUTICAL -> "${fmt(speed, 1)} kn"
        UnitMode.METRIC   -> "${fmt(speed, 1)} km/h"
    }
    val etaText = if (etaHours.isFinite()) {
        val h = etaHours.toInt()
        val m = ((etaHours - h) * 60).roundToInt()
        "${h}h ${m}m"
    } else "—"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Riepilogo", color = fg, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Start", color = fg.copy(alpha = 0.7f))
                    Text(start?.let { "${fmtLat(it.lat)}  ${fmtLon(it.lon)}" } ?: "—", color = fg, fontWeight = FontWeight.Medium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Arrivo", color = fg.copy(alpha = 0.7f))
                    Text(goal?.let { "${fmtLat(it.lat)}  ${fmtLon(it.lon)}" } ?: "—", color = fg, fontWeight = FontWeight.Medium)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Distanza: $distText", color = fg, fontWeight = FontWeight.Medium)
                Text("ETA: $etaText", color = fg, fontWeight = FontWeight.Medium)
                Text("Vel.: $speedText", color = fg, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun SpeedRow(speed: Double, unitMode: UnitMode, fg: Color, onSpeedChange: (Double) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val label = if (unitMode == UnitMode.NAUTICAL) "Velocità (kn)" else "Velocità (km/h)"
            Text(label, color = fg, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onSpeedChange((speed - 0.5).coerceAtLeast(0.0)) }) {
                    Icon(Icons.Filled.Remove, contentDescription = "meno", tint = Color.White)
                }
                Text(fmt(speed, 1), color = fg, modifier = Modifier.width(56.dp), fontWeight = FontWeight.Medium)
                IconButton(onClick = { onSpeedChange(speed + 0.5) }) {
                    Icon(Icons.Filled.Add, contentDescription = "più", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun LegRow(index: Int, leg: Leg, unitMode: UnitMode, fg: Color) {
    val distText = when (unitMode) {
        UnitMode.NAUTICAL -> "${fmt(leg.distanceMeters / 1852.0, 2)} NM"
        UnitMode.METRIC   -> "${fmt(leg.distanceMeters / 1000.0, 2)} km"
    }
    val courseText = "${leg.bearingDeg.toInt()}° ${bearingCardinal(leg.bearingDeg)}"

    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFF181818), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("Leg $index", color = fg, fontWeight = FontWeight.SemiBold)
        Text(distText, color = fg)
        Text(courseText, color = fg)
    }
}

/* -------------------- Calcoli rotta -------------------- */

data class Leg(val a: LatLon, val b: LatLon, val distanceMeters: Double, val bearingDeg: Double)

private fun computeLegs(path: List<LatLon>): List<Leg> {
    if (path.size < 2) return emptyList()
    val out = ArrayList<Leg>(path.size - 1)
    for (i in 0 until path.lastIndex) {
        val a = path[i]
        val b = path[i + 1]
        val d = GeoUtils.distanceMeters(a, b)
        val brg = initialBearingDeg(a, b)
        out.add(Leg(a, b, d, brg))
    }
    return out
}

private fun initialBearingDeg(a: LatLon, b: LatLon): Double {
    // Great-circle initial bearing
    val φ1 = Math.toRadians(a.lat)
    val φ2 = Math.toRadians(b.lat)
    val λ1 = Math.toRadians(a.lon)
    val λ2 = Math.toRadians(b.lon)
    val dλ = λ2 - λ1
    val y = sin(dλ) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
    var deg = Math.toDegrees(atan2(y, x))
    if (deg < 0) deg += 360.0
    return deg
}

private fun bearingCardinal(deg: Double): String {
    val dirs = arrayOf("N","NNE","NE","ENE","E","ESE","SE","SSE","S","SSW","SW","WSW","W","WNW","NW","NNW")
    val idx = ((deg / 22.5).roundToInt()) % 16
    return dirs[idx]
}

/* -------------------- formattazioni -------------------- */

private fun fmt(v: Double, dec: Int) = "%.${dec}f".format(v)
private fun fmtLat(v: Double): String = (if (v >= 0) "N " else "S ") + String.format("%.5f°", abs(v))
private fun fmtLon(v: Double): String = (if (v >= 0) "E " else "W ") + String.format("%.5f°", abs(v))
