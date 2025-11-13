package com.perseitech.sailpilot.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.perseitech.sailpilot.regatta.computeLaylines
import com.perseitech.sailpilot.routing.LatLon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Regatta Map:
 * - mappa osmdroid con barca, starting line, laylines
 * - TTL (time to line), Burn (TTL - tempo residuo)
 * - lato favorito (port / starboard)
 * - countdown ISAF 5-4-1-0 con beep
 * - turn-instruction testuale + barra visiva
 */
@Composable
fun RegattaMapPage(
    live: LatLon?,
    sogKn: Double?,
    twdDeg: Double?,
    tackAngleDeg: Double,
    cogDeg: Double?,
    hdgDeg: Double?,
    committee: LatLon?,
    pin: LatLon?,
    isafCountdownEnabled: Boolean,
    onSetCommitteeFromGps: (() -> Unit)?,
    onSetPinFromGps:   (() -> Unit)?,
    onClearLine:       (() -> Unit)?
) {
    // ---- STATE REGATTA ----
    var ttlSeconds by remember { mutableStateOf<Double?>(null) }      // time to line (s)
    var burnSeconds by remember { mutableStateOf<Double?>(null) }     // TTL - countdown
    var remainingSec by remember { mutableStateOf<Int?>(null) }       // countdown corrente
    var favouredSide by remember { mutableStateOf("—") }              // Port / Starboard
    var turnDir by remember { mutableStateOf<String?>(null) }         // "L" / "R"
    var turnDeg by remember { mutableStateOf<Double?>(null) }         // 0..60
    var targetAngleDeg by remember { mutableStateOf<Double?>(null) }  // bearing layline target

    val scope = rememberCoroutineScope()
    var countdownJob by remember { mutableStateOf<Job?>(null) }

    // Tone generator per beep countdown
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }

    fun startCountdown(totalSeconds: Int) {
        if (!isafCountdownEnabled) return
        countdownJob?.cancel()
        countdownJob = scope.launch {
            var rem = totalSeconds
            var prev = rem
            val marks = listOf(300, 240, 60, 0) // 5m, 4m, 1m, 0

            while (rem >= -30) {
                remainingSec = rem
                ttlSeconds?.let { ttl -> burnSeconds = ttl - rem.toDouble() }

                // Beep quando attraversiamo le soglie canoniche
                marks.forEach { m ->
                    if (prev >= m && rem < m) {
                        toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 350)
                    }
                }

                prev = rem
                delay(1000L)
                rem--
            }
        }
    }

    fun stopCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        remainingSec = null
        burnSeconds = null
    }

    // ================= CALCOLI GEOMETRICI (fuori da AndroidView) =================

    // TTL approssimato: distanza ortogonale alla linea / velocità
    ttlSeconds = if (live != null && committee != null && pin != null && sogKn != null && sogKn > 0.1) {
        val dist = distanceToSegmentMeters(live, committee, pin) // m
        dist / (sogKn * 0.514444) // s
    } else null

    // calcolo lato favorito, target angle e turn-instruction
    run {
        if (live != null && twdDeg != null) {
            val (pPort, pStar) = computeLaylines(live, twdDeg, tackAngleDeg, lenM = 2500.0)

            val baseDir = hdgDeg ?: cogDeg
            val portFavoured = if (baseDir != null) {
                val brgPort = bearing(live, pPort)
                val brgStar = bearing(live, pStar)
                val dPort = abs(normDelta(brgPort - baseDir))
                val dStar = abs(normDelta(brgStar - baseDir))
                dPort <= dStar
            } else {
                true
            }

            favouredSide = if (portFavoured) "Port tack favoured" else "Starboard tack favoured"

            if (cogDeg != null) {
                val targetBrg = if (portFavoured) bearing(live, pPort) else bearing(live, pStar)
                targetAngleDeg = targetBrg
                val delta = normDelta(targetBrg - cogDeg)
                val dir = if (delta >= 0) "R" else "L"
                val ang = abs(delta).coerceIn(0.0, 60.0)

                turnDir = dir
                turnDeg = ang
            } else {
                targetAngleDeg = null
                turnDir = null
                turnDeg = null
            }
        } else {
            favouredSide = "—"
            targetAngleDeg = null
            turnDir = null
            turnDeg = null
        }
    }

    // ============================ UI ============================

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {

        // MAPPA OSMDROID
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            factory = { ctx ->
                MapView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    controller.setZoom(13.0)
                    live?.let { controller.setCenter(GeoPoint(it.lat, it.lon)) }
                }
            },
            update = { mv ->
                mv.overlays.clear()

                fun marker(pt: LatLon, title: String, icon: Bitmap? = null, rotation: Float? = null): Marker =
                    Marker(mv).apply {
                        position = GeoPoint(pt.lat, pt.lon)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        this.title = title
                        icon?.let {
                            this.icon = android.graphics.drawable.BitmapDrawable(mv.resources, it)
                        }
                        rotation?.let { this.rotation = it }
                    }

                fun line(a: LatLon, b: LatLon, color: Int, width: Float = 6f): Polyline =
                    Polyline().apply {
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = width
                        setPoints(listOf(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon)))
                    }

                // BARCA + freccia heading/COG
                live?.let { boat ->
                    val heading = (hdgDeg ?: cogDeg)?.toFloat()
                    val bmp = makeArrowBitmap(size = 52, color = Color.CYAN)
                    mv.overlays.add(
                        marker(
                            boat,
                            "Boat",
                            bmp,
                            rotation = heading ?: 0f
                        )
                    )
                    mv.controller.setCenter(GeoPoint(boat.lat, boat.lon))
                }

                // starting line
                if (committee != null && pin != null) {
                    mv.overlays.add(line(committee, pin, Color.YELLOW, 8f))
                    mv.overlays.add(marker(committee, "Committee"))
                    mv.overlays.add(marker(pin, "Pin"))
                }

                // laylines solo grafica (i calcoli li abbiamo già fatti sopra)
                if (live != null && twdDeg != null) {
                    val (pPort, pStar) = computeLaylines(live, twdDeg, tackAngleDeg, lenM = 2500.0)
                    val portFavouredNow = favouredSide.startsWith("Port")

                    val colTarget = Color.GREEN
                    val colOther  = Color.LTGRAY

                    mv.overlays.add(
                        line(
                            live,
                            pPort,
                            if (portFavouredNow) colTarget else colOther,
                            width = 7f
                        )
                    )
                    mv.overlays.add(
                        line(
                            live,
                            pStar,
                            if (!portFavouredNow) colTarget else colOther,
                            width = 7f
                        )
                    )
                }

                mv.invalidate()
            }
        )

        // COMANDI COM/PIN/CLEAR + COUNTDOWN
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { onSetCommitteeFromGps?.invoke() }, enabled = onSetCommitteeFromGps != null) {
                Text("Set COM GPS")
            }
            Button(onClick = { onSetPinFromGps?.invoke() }, enabled = onSetPinFromGps != null) {
                Text("Set PIN GPS")
            }
            Button(
                onClick = {
                    onClearLine?.invoke()
                },
                enabled = onClearLine != null
            ) {
                Text("Clear")
            }

            Spacer(Modifier.weight(1f))

            if (isafCountdownEnabled) {
                Button(onClick = { startCountdown(300) }) { Text("5-4-1-0") }
                Button(onClick = { startCountdown(240) }) { Text("@4m") }
                Button(onClick = { startCountdown(60) })  { Text("@1m") }
                Button(onClick = { stopCountdown() })     { Text("Stop") }
            }
        }

        // OVERLAY INFO + TURN BAR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Regatta prestart", style = MaterialTheme.typography.titleMedium)

                Text("TTL (time to line): " + (ttlSeconds?.let { formatTime(it) } ?: "—"))
                Text("BURN: " + (burnSeconds?.let { formatSignedTime(it) } ?: "—"))
                Text("Countdown: " + (remainingSec?.let { formatTime(it.toDouble()) } ?: "—"))

                Text("Favoured side: $favouredSide")
                val targetTxt = targetAngleDeg?.let { "%.0f°".format(it) } ?: "—"
                Text("Target angle: $targetTxt")

                Spacer(Modifier.height(8.dp))
                TurnBar(turnDeg = turnDeg, dir = turnDir)
            }
        }
    }
}

/**
 * Turn-instruction semplificato:
 * - due barre orizzontali, una per L e una per R
 * - la lunghezza è proporzionale all’angolo (max 60°)
 */
@Composable
private fun TurnBar(turnDeg: Double?, dir: String?) {
    val value = turnDeg ?: 0.0
    val clamped = value.coerceIn(0.0, 60.0)
    val frac = (clamped / 60.0).toFloat()

    val isLeft = dir == "L"
    val isRight = dir == "R"

    val label = if (turnDeg == null || dir == null) {
        "—"
    } else {
        "${if (isLeft) "L" else "R"} ${"%.0f".format(value)}°"
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // Barra L
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("L", modifier = Modifier.width(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (isLeft && frac > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        // Barra R
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("R", modifier = Modifier.width(18.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (isRight && frac > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(frac.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }
            }
        }

        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Text("Turn instruction: $label")
        }
    }
}

/* ==================== HELPER NON-COMPOSABLE ==================== */

/** bitmap freccia “su” da ruotare col heading/COG */
private fun makeArrowBitmap(size: Int, color: Int): Bitmap {
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }
    val w = size.toFloat()
    val h = size.toFloat()
    val path = Path().apply {
        moveTo(w * 0.50f, h * 0.10f)
        lineTo(w * 0.78f, h * 0.50f)
        lineTo(w * 0.62f, h * 0.50f)
        lineTo(w * 0.62f, h * 0.85f)
        lineTo(w * 0.38f, h * 0.85f)
        lineTo(w * 0.38f, h * 0.50f)
        lineTo(w * 0.22f, h * 0.50f)
        close()
    }
    c.drawPath(path, p)
    return bmp
}

/** bearing iniziale da A a B in gradi [0..360) */
private fun bearing(a: LatLon, b: LatLon): Double {
    val φ1 = Math.toRadians(a.lat)
    val φ2 = Math.toRadians(b.lat)
    val λ1 = Math.toRadians(a.lon)
    val λ2 = Math.toRadians(b.lon)
    val dλ = λ2 - λ1
    val y = sin(dλ) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
    var deg = Math.toDegrees(kotlin.math.atan2(y, x))
    if (deg < 0) deg += 360.0
    return deg
}

/** normalizza angolo in intervallo [-180, +180] */
private fun normDelta(d: Double): Double {
    var x = (d + 540.0) % 360.0 - 180.0
    if (x > 180) x -= 360.0
    if (x < -180) x += 360.0
    return x
}

/** distanza punto -> segmento in metri (approssimazione planare locale) */
private fun distanceToSegmentMeters(p: LatLon, a: LatLon, b: LatLon): Double {
    if (a.lat == b.lat && a.lon == b.lon) {
        return geoApproxMeters(p, a)
    }

    val mPerDegLat = 111_132.0
    val mPerDegLon =
        111_320.0 * cos(Math.toRadians((a.lat + b.lat + p.lat) / 3.0))

    data class P(val x: Double, val y: Double)
    fun toXY(ll: LatLon) = P(
        (ll.lon - a.lon) * mPerDegLon,
        (ll.lat - a.lat) * mPerDegLat
    )

    val A = P(0.0, 0.0)
    val B = toXY(b)
    val Pp = toXY(p)

    val ABx = B.x - A.x
    val ABy = B.y - A.y
    val APx = Pp.x - A.x
    val APy = Pp.y - A.y

    val ab2 = ABx * ABx + ABy * ABy
    val t = if (ab2 <= 1e-9) 0.0 else ((APx * ABx + APy * ABy) / ab2).coerceIn(0.0, 1.0)

    val Hx = A.x + ABx * t
    val Hy = A.y + ABy * t
    val dx = Pp.x - Hx
    val dy = Pp.y - Hy
    return sqrt(dx * dx + dy * dy)
}

/** distanza approssimata (metri) tra due LatLon vicini */
private fun geoApproxMeters(a: LatLon, b: LatLon): Double {
    val mPerDegLat = 111_132.0
    val mPerDegLon =
        111_320.0 * cos(Math.toRadians((a.lat + b.lat) / 2.0))
    val dx = (b.lon - a.lon) * mPerDegLon
    val dy = (b.lat - a.lat) * mPerDegLat
    return sqrt(dx * dx + dy * dy)
}

/** t in secondi → mm:ss */
private fun formatTime(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val m = s / 60
    val ss = s % 60
    return "%d:%02d".format(m, ss)
}

/** t in secondi con segno → +mm:ss / -mm:ss */
private fun formatSignedTime(seconds: Double): String {
    val sign = if (seconds >= 0) "+" else "-"
    val s = abs(seconds).toInt()
    val m = s / 60
    val ss = s % 60
    return "$sign%02d:%02d".format(m, ss)
}
