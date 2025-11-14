package com.perseitech.sailpilot.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.perseitech.sailpilot.routing.LatLon
import kotlinx.coroutines.delay
import kotlin.math.*

@Composable
fun RegattaMapPage(
    live: LatLon?,
    sogKn: Double?,
    twdDeg: Double?,
    tackAngleDeg: Double?,
    cogDeg: Double?,
    hdgDeg: Double?,
    committee: LatLon?,
    pin: LatLon?,
    isafCountdownEnabled: Boolean,
    onSetCommitteeFromGps: () -> Unit,
    onSetPinFromGps: () -> Unit,
    onClearLine: () -> Unit
) {
    // --- COUNTDOWN ISAF (5-4-1-0) ---
    var countdownTarget by remember { mutableStateOf<Long?>(null) }
    var countdownSec by remember { mutableStateOf<Double?>(null) }

    LaunchedEffect(countdownTarget) {
        while (countdownTarget != null) {
            val left = (countdownTarget!! - System.currentTimeMillis()) / 1000.0
            if (left <= 0.0) {
                countdownSec = 0.0
                countdownTarget = null
            } else {
                countdownSec = left
            }
            delay(200)
        }
    }

    fun startCountdown(totalSeconds: Int) {
        countdownTarget = System.currentTimeMillis() + totalSeconds * 1000L
    }
    fun stopCountdown() {
        countdownTarget = null
        countdownSec = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF000F17))
            .padding(12.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {

        // HEADER
        Text(
            text = "REGATTA MAP (virtual)",
            color = Color.Cyan,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        // 1) STARTING LINE
        StartingLineBox(
            committee = committee,
            pin = pin,
            onSetCommitteeFromGps = onSetCommitteeFromGps,
            onSetPinFromGps = onSetPinFromGps,
            onClear = onClearLine
        )

        // 2) COUNTDOWN ISAF
        if (isafCountdownEnabled) {
            CountdownBox(
                countdownSec = countdownSec,
                onStart5m = { startCountdown(5 * 60) },
                onStart4m = { startCountdown(4 * 60) },
                onStart1m = { startCountdown(60) },
                onStop = { stopCountdown() }
            )
        }

        // 3) TURN INSTRUCTION
        TurnInstructionSection(
            twdDeg = twdDeg,
            tackAngleDeg = tackAngleDeg,
            cogDeg = cogDeg,
            hdgDeg = hdgDeg
        )

        // 4) BURN / TTL legati al countdown
        BurnTtlSection(
            live = live,
            sogKn = sogKn,
            committee = committee,
            pin = pin,
            countdownSec = countdownSec
        )

        // 5) REGATTA OVERVIEW (circuito virtuale + vele suggerite)
        RegattaOverviewSection()
    }
}

// --------------------------------------------------------
// STARTING LINE
// --------------------------------------------------------

@Composable
private fun StartingLineBox(
    committee: LatLon?,
    pin: LatLon?,
    onSetCommitteeFromGps: () -> Unit,
    onSetPinFromGps: () -> Unit,
    onClear: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF002130))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Starting line",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "COM: ${committee?.lat?.format4() ?: "--"} / ${committee?.lon?.format4() ?: "--"}",
            color = Color.LightGray,
            fontSize = 13.sp
        )
        Text(
            "PIN: ${pin?.lat?.format4() ?: "--"} / ${pin?.lon?.format4() ?: "--"}",
            color = Color.LightGray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RegButton("COM @ GPS", enabled = true, onClick = onSetCommitteeFromGps)
            RegButton("PIN @ GPS", enabled = true, onClick = onSetPinFromGps)
            RegButton(
                "Clear",
                enabled = (committee != null || pin != null),
                onClick = onClear
            )
        }
    }
}

@Composable
private fun RegButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val bg = if (enabled) Color(0xFF005066) else Color(0xFF00343F)
    val fg = if (enabled) Color.White else Color.LightGray.copy(alpha = 0.5f)

    Box(
        modifier = Modifier
            .background(bg, shape = MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(label, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

// --------------------------------------------------------
// COUNTDOWN ISAF (semplice, senza beep per ora)
// --------------------------------------------------------

@Composable
private fun CountdownBox(
    countdownSec: Double?,
    onStart5m: () -> Unit,
    onStart4m: () -> Unit,
    onStart1m: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF001822))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "ISAF countdown",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RegButton("5:00", enabled = true, onClick = onStart5m)
            RegButton("4:00", enabled = true, onClick = onStart4m)
            RegButton("1:00", enabled = true, onClick = onStart1m)
            RegButton("Stop", enabled = true, onClick = onStop)
        }

        val text = countdownSec?.let { formatSeconds(it) } ?: "--:--"

        Text(
            "Start in: $text",
            color = Color.Cyan,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --------------------------------------------------------
// TURN INSTRUCTION (gauge circolare)
// --------------------------------------------------------

@Composable
private fun TurnInstructionSection(
    twdDeg: Double?,
    tackAngleDeg: Double?,
    cogDeg: Double?,
    hdgDeg: Double?
) {
    val headingRef = hdgDeg ?: cogDeg

    val targetUpwindHeading: Double? =
        if (twdDeg != null && tackAngleDeg != null && headingRef != null) {
            val starboard = normalizeDeg(twdDeg - tackAngleDeg / 2.0)
            val port = normalizeDeg(twdDeg + tackAngleDeg / 2.0)
            val dStar = abs(signedAngleDeg(starboard - headingRef))
            val dPort = abs(signedAngleDeg(port - headingRef))
            if (dStar <= dPort) starboard else port
        } else null

    val deltaDeg: Double? =
        if (headingRef != null && targetUpwindHeading != null) {
            signedAngleDeg(targetUpwindHeading - headingRef)
        } else null

    val deltaText = deltaDeg?.let { "${it.roundToInt()}°" } ?: "—"

    val color = when {
        deltaDeg == null -> Color.Gray
        deltaDeg > 2 -> Color(0xFF00E676)    // vira a destra → verde
        deltaDeg < -2 -> Color(0xFFFF5252)   // vira a sinistra → rosso
        else -> Color.Yellow                 // ok / on target
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF001822))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Turn instruction",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        TurnInstructionGauge(deltaDeg = deltaDeg, color = color)

        Spacer(Modifier.height(4.dp))
        Text(
            "Δ heading: $deltaText",
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun TurnInstructionGauge(
    deltaDeg: Double?,
    color: Color
) {
    Box(
        modifier = Modifier
            .size(220.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val radius = min(cx, cy) * 0.85f

            // cerchio base
            drawCircle(
                color = Color.DarkGray,
                radius = radius,
                style = Stroke(width = 6f)
            )

            // ticks principali (-60, -30, 0, 30, 60)
            val ticks = listOf(-60f, -30f, 0f, 30f, 60f)
            ticks.forEach { a ->
                val rad = Math.toRadians(a.toDouble() - 90.0)
                val inner = radius * 0.75f
                val outer = radius
                val sx = cx + inner * cos(rad).toFloat()
                val sy = cy + inner * sin(rad).toFloat()
                val ex = cx + outer * cos(rad).toFloat()
                val ey = cy + outer * sin(rad).toFloat()

                drawLine(
                    color = if (a == 0f) Color.White else Color.LightGray,
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = if (a == 0f) 5f else 3f,
                    cap = StrokeCap.Round
                )
            }

            // arco direzione turn
            val d = deltaDeg ?: 0.0
            val maxArc = 60f
            val clamped = d.coerceIn(-maxArc.toDouble(), maxArc.toDouble())
            if (deltaDeg != null && abs(clamped) > 1.0) {
                val sweep = clamped.toFloat()
                val startAngle = -90f
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(cx - radius, cy - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 10f, cap = StrokeCap.Round)
                )
            }
        }

        // testo al centro (usiamo Text sopra il Canvas)
        val text = deltaDeg?.let { "${it.roundToInt()}°" } ?: "—"
        Text(
            text = text,
            color = color,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --------------------------------------------------------
// BURN / TTL legati al countdown
// --------------------------------------------------------

@Composable
private fun BurnTtlSection(
    live: LatLon?,
    sogKn: Double?,
    committee: LatLon?,
    pin: LatLon?,
    countdownSec: Double?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF002230))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "Pre-start timing",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        if (live == null || committee == null || pin == null || sogKn == null || sogKn <= 0.3) {
            Text(
                "Serve posizione barca, linea e SOG > 0.3 kn",
                color = Color.LightGray,
                fontSize = 12.sp
            )
            return
        }

        // distanza perpendicolare stimata dalla linea COM–PIN
        val crossTrackM = abs(signedDistanceToLineMeters(live, committee, pin))

        val mps = sogKn * 0.514444
        val ttlSec = crossTrackM / mps

        val burnSec: Double? =
            if (countdownSec != null) ttlSec - countdownSec else null

        val ttlText = formatSeconds(ttlSec)
        val burnText = burnSec?.let { formatSignedSeconds(it) } ?: "--:--"

        val burnColor = when {
            burnSec == null -> Color.Gray
            burnSec > 5.0 -> Color(0xFF00E676)   // in anticipo → puoi “bruciare” tempo
            burnSec < -5.0 -> Color(0xFFFF5252)  // in ritardo
            else -> Color.Yellow
        }

        Text(
            "TTL (time to line): $ttlText",
            color = Color.Cyan,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Burn: $burnText",
            color = burnColor,
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

// --------------------------------------------------------
// REGATTA OVERVIEW (circuito virtuale + vele)
// --------------------------------------------------------

@Composable
private fun RegattaOverviewSection() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF001822))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "Regatta overview",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Circuito virtuale (Upwind • Reach • Downwind)",
            color = Color.LightGray,
            fontSize = 13.sp
        )

        Spacer(Modifier.height(8.dp))

        // Semplice layout a colonne con 3 leg
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LegColumn(
                title = "UPWIND",
                color = Color(0xFF00E676),
                sailHint = "Main + Jib/Genoa\nTWA ~35–45°"
            )
            LegColumn(
                title = "REACH",
                color = Color(0xFFFFFF00),
                sailHint = "Main + Code 0 / A3\nTWA ~70–110°"
            )
            LegColumn(
                title = "DOWNWIND",
                color = Color(0xFFFF5252),
                sailHint = "Main + Gennaker/Spin\nTWA ~135–180°"
            )
        }
    }
}

@Composable
private fun LegColumn(
    title: String,
    color: Color,
    sailHint: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            title,
            color = color,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold
        )
        Canvas(
            modifier = Modifier
                .width(60.dp)
                .height(80.dp)
        ) {
            val cx = size.width / 2f
            val top = 10f
            val bottom = size.height - 10f

            // freccia semplice verticale
            drawLine(
                color = color,
                start = Offset(cx, bottom),
                end = Offset(cx, top),
                strokeWidth = 5f,
                cap = StrokeCap.Round
            )
            // punta freccia
            drawLine(
                color = color,
                start = Offset(cx, top),
                end = Offset(cx - 10f, top + 15f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
            drawLine(
                color = color,
                start = Offset(cx, top),
                end = Offset(cx + 10f, top + 15f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )
        }
        Text(
            sailHint,
            color = Color.LightGray,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// --------------------------------------------------------
// GEO / MATH HELPERS
// --------------------------------------------------------

private fun normalizeDeg(a: Double): Double {
    var x = a % 360.0
    if (x < 0) x += 360.0
    return x
}

/** restituisce angolo fra -180..+180 */
private fun signedAngleDeg(a: Double): Double {
    var x = normalizeDeg(a)
    if (x > 180.0) x -= 360.0
    return x
}

/**
 * Distanza firmata dalla retta COM–PIN (positiva da un lato, negativa dall'altro).
 * Approssimazione planare locale (ok per campo di regata).
 */
private fun signedDistanceToLineMeters(p: LatLon, a: LatLon, b: LatLon): Double {
    val lat0 = Math.toRadians((a.lat + b.lat + p.lat) / 3.0)
    val mPerDegLat = 111_132.0
    val mPerDegLon = 111_320.0 * cos(lat0)

    val ax = a.lon * mPerDegLon
    val ay = a.lat * mPerDegLat
    val bx = b.lon * mPerDegLon
    val by = b.lat * mPerDegLat
    val px = p.lon * mPerDegLon
    val py = p.lat * mPerDegLat

    val vx = bx - ax
    val vy = by - ay
    val wx = px - ax
    val wy = py - ay

    val cross = vx * wy - vy * wx
    val norm = sqrt(vx * vx + vy * vy)
    if (norm == 0.0) return 0.0
    return cross / norm
}

private fun formatSeconds(sec: Double): String {
    if (!sec.isFinite()) return "--:--"
    val total = sec.roundToInt().coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "%d:%02d".format(m, s)
}

private fun formatSignedSeconds(sec: Double): String {
    if (!sec.isFinite()) return "--:--"
    val sign = if (sec >= 0) "+" else "-"
    val abs = kotlin.math.abs(sec)
    val total = abs.roundToInt()
    val m = total / 60
    val s = total % 60
    return "$sign${"%d:%02d".format(m, s)}"
}

// --------------------------------------------------------
// Misc helpers
// --------------------------------------------------------

private fun Double.format4(): String = String.format("%.4f", this)
