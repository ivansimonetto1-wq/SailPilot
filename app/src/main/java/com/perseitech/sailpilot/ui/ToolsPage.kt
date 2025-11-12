package com.perseitech.sailpilot.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.perseitech.sailpilot.routing.GeoUtils
import com.perseitech.sailpilot.routing.LatLon
import kotlin.math.*

@Composable
fun ToolsPage(
    path: List<LatLon>,
    unitMode: UnitMode,   // usa l’enum già esistente nel progetto
    speed: Double,
    onExportGpx: () -> Unit
) {
    val legs = remember(path) { computeLegs(path) }
    val fg = Color.White

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF101010))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Tools", color = fg, style = MaterialTheme.typography.titleLarge)

        Button(onClick = onExportGpx) { Text("Esporta GPX in Download") }

        Spacer(Modifier.height(8.dp))
        Text("Dettaglio Leg", color = fg, style = MaterialTheme.typography.titleMedium)

        if (legs.isEmpty()) {
            Text("Nessuna tratta disponibile", color = fg.copy(alpha = 0.75f))
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                legs.forEachIndexed { idx, leg ->
                    val distNm = leg.distanceMeters / 1852.0
                    val distKm = leg.distanceMeters / 1000.0
                    val etaH = when (unitMode) {
                        UnitMode.NAUTICAL -> if (speed > 0) distNm / speed else Double.NaN
                        UnitMode.METRIC   -> if (speed > 0) distKm / speed else Double.NaN
                    }
                    val etaTxt = if (etaH.isFinite()) {
                        val h = etaH.toInt(); val m = ((etaH - h) * 60).roundToInt(); "${h}h ${m}m"
                    } else "—"

                    Surface(color = Color(0xFF181818), tonalElevation = 0.dp) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Leg ${idx + 1}", color = fg)
                            val distTxt = if (unitMode == UnitMode.NAUTICAL) "${fmt(distNm, 2)} NM" else "${fmt(distKm, 2)} km"
                            Text(distTxt, color = fg)
                            Text("${leg.bearingDeg.toInt()}° ${bearingCardinal(leg.bearingDeg)}  •  ETA $etaTxt", color = fg)
                        }
                    }
                }
            }
        }
    }
}

/* ---- helpers locali (niente nomi che confliggono) ---- */

private data class RouteLegUi(val a: LatLon, val b: LatLon, val distanceMeters: Double, val bearingDeg: Double)

private fun computeLegs(path: List<LatLon>): List<RouteLegUi> {
    if (path.size < 2) return emptyList()
    val out = ArrayList<RouteLegUi>(path.size - 1)
    for (i in 0 until path.lastIndex) {
        val a = path[i]
        val b = path[i + 1]
        val d = GeoUtils.distanceMeters(a, b)
        val brg = initialBearingDeg(a, b)
        out.add(RouteLegUi(a, b, d, brg))
    }
    return out
}

private fun initialBearingDeg(a: LatLon, b: LatLon): Double {
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

private fun fmt(v: Double, dec: Int) = "%.${dec}f".format(v)
