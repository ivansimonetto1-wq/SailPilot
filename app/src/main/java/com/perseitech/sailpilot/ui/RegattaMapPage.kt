package com.perseitech.sailpilot.ui

import android.graphics.*
import android.media.AudioManager
import android.media.ToneGenerator
import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.perseitech.sailpilot.regatta.computeLaylines
import com.perseitech.sailpilot.routing.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import kotlin.math.*

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
    var ttl by remember { mutableStateOf<Double?>(null) }
    var burn by remember { mutableStateOf<Double?>(null) }
    var startAt by remember { mutableStateOf<Long?>(null) }
    var turn by remember { mutableStateOf<Double?>(null) }
    var turnDir by remember { mutableStateOf<String?>(null) }
    var targetIsPort by remember { mutableStateOf<Boolean?>(null) }
    var remainingSec by remember { mutableStateOf<Int?>(null) }

    // Tone generator per beep countdown
    val toneGen = remember { ToneGenerator(AudioManager.STREAM_MUSIC, 80) }
    DisposableEffect(Unit) {
        onDispose { toneGen.release() }
    }

    // Countdown ISAF 5-4-1-0
    LaunchedEffect(startAt, isafCountdownEnabled) {
        if (!isafCountdownEnabled || startAt == null) {
            remainingSec = null
            return@LaunchedEffect
        }
        var prev = ((startAt!! - System.currentTimeMillis()) / 1000.0).toInt()
        val marks = listOf(300, 240, 60, 0)
        while (true) {
            val now = System.currentTimeMillis()
            val rem = ((startAt!! - now) / 1000.0).toInt()
            remainingSec = rem
            if (ttl != null) {
                burn = ttl!! - rem.toDouble()
            }
            marks.forEach { t ->
                if (prev >= t && rem < t) {
                    toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 400)
                }
            }
            prev = rem
            if (rem < -30) break
            kotlinx.coroutines.delay(200L)
        }
    }

    Column(Modifier.fillMaxSize()) {

        AndroidView(
            modifier = Modifier.weight(1f),
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
                        icon?.let { this.icon = android.graphics.drawable.BitmapDrawable(mv.resources, it) }
                        rotation?.let { this.rotation = it }
                    }

                fun line(a: LatLon, b: LatLon, color: Int, width: Float = 6f): Polyline =
                    Polyline().apply {
                        outlinePaint.color = color
                        outlinePaint.strokeWidth = width
                        setPoints(listOf(GeoPoint(a.lat, a.lon), GeoPoint(b.lat, b.lon)))
                    }

                // boat marker + freccia heading
                live?.let {
                    val heading = (hdgDeg ?: cogDeg)?.toFloat()
                    val bmp = makeArrowBitmap(size = 48, color = android.graphics.Color.CYAN)
                    mv.overlays.add(marker(it, "Boat", bmp, rotation = heading ?: 0f))
                }

                // starting line
                if (committee != null && pin != null) {
                    mv.overlays.add(line(committee, pin, android.graphics.Color.YELLOW, 8f))
                    mv.overlays.add(marker(committee, "Committee"))
                    mv.overlays.add(marker(pin, "Pin"))
                }

                // laylines + target side
                if (live != null && twdDeg != null) {
                    val (pPort, pStar) = computeLaylines(live, twdDeg, tackAngleDeg, lenM = 2500.0)
                    val dPort = distanceToSegmentMeters(live, live, pPort)
                    val dStar = distanceToSegmentMeters(live, live, pStar)
                    val portTarget = dPort <= dStar
                    targetIsPort = portTarget

                    val colorTarget = android.graphics.Color.GREEN
                    val colorOther  = android.graphics.Color.LTGRAY

                    mv.overlays.add(line(live, pPort, if (portTarget) colorTarget else colorOther, width = 7f))
                    mv.overlays.add(line(live, pStar, if (!portTarget) colorTarget else colorOther, width = 7f))
                }

                // TTL (tempo per arrivare sulla linea con SOG attuale)
                ttl = if (live != null && committee != null && pin != null && sogKn != null && sogKn > 0.1) {
                    val d = distanceToSegmentMeters(live, committee, pin)
                    d / (sogKn * 0.514444) // seconds
                } else null

                // turn instruction = differenza tra COG e layline target
                if (live != null && twdDeg != null && cogDeg != null) {
                    val brgPort = (twdDeg + 180 - tackAngleDeg + 360) % 360
                    val brgStar = (twdDeg + 180 + tackAngleDeg + 360) % 360
                    val targetBrg = if (targetIsPort == true) brgPort else brgStar
                    val delta = normDelta(targetBrg - cogDeg)
                    turnDir = if (delta >= 0) "»" else "«"
                    turn = kotlin.math.abs(delta)
                } else {
                    turnDir = null
                    turn = null
                }

                mv.invalidate()
            }
        )

        // Barra controlli
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(onClick = { onSetCommitteeFromGps?.invoke() }, enabled = onSetCommitteeFromGps != null) {
                Text("Set COM GPS")
            }
            Button(onClick = { onSetPinFromGps?.invoke() }, enabled = onSetPinFromGps != null) {
                Text("Set PIN GPS")
            }
            Button(onClick = { onClearLine?.invoke() }, enabled = onClearLine != null) {
                Text("Clear")
            }
            Spacer(Modifier.weight(1f))
            if (isafCountdownEnabled) {
                Button(onClick = { startAt = System.currentTimeMillis() + 300_000L }) {
                    Text("Start 5-4-1-0")
                }
                Button(onClick = { startAt = System.currentTimeMillis() + 240_000L }) {
                    Text("Sync @4")
                }
                Button(onClick = { startAt = System.currentTimeMillis() + 60_000L }) {
                    Text("Sync @1")
                }
                Button(onClick = { startAt = null; burn = null; remainingSec = null }) {
                    Text("Stop")
                }
            }
        }

        // Info + strumento "turn instruction"
        Card(Modifier.fillMaxWidth().padding(8.dp)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Prestart / Laylines", style = MaterialTheme.typography.titleMedium)
                Text("TTL: " + (ttl?.let { formatTime(it) } ?: "—"))
                Text("BURN: " + (burn?.let { formatSignedTime(it) } ?: "—"))
                Text("Countdown: " + (remainingSec?.let { formatTime(it.toDouble()) } ?: "—"))

                Spacer(Modifier.height(8.dp))
                TurnInstrument(turnDeg = turn, dir = turnDir)
            }
        }
    }
}

/** strumento circolare per “turn instruction” */
@Composable
private fun TurnInstrument(turnDeg: Double?, dir: String?) {
    val value = turnDeg ?: 0.0
    val clamped = value.coerceIn(0.0, 60.0) // scala ±60°
    val fraction = (clamped / 60.0).toFloat()
    val isLeft = dir == "«"
    val label = if (turnDeg == null || dir == null) "—" else "${if (isLeft) "L" else "R"} ${"%.0f".format(value)}°"

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Canvas(modifier = Modifier.size(140.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.5f

            // cerchio base
            drawCircle(
                color = MaterialTheme.colorScheme.outline,
                radius = radius,
                style = Stroke(width = 4f)
            )

            // tacca 0°
            drawLine(
                color = MaterialTheme.colorScheme.onSurface,
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y - radius + 16f),
                strokeWidth = 4f,
                cap = StrokeCap.Round
            )

            fun tick(angleDeg: Float) {
                val rad = Math.toRadians(angleDeg.toDouble()).toFloat()
                val sx = center.x + sin(rad) * (radius - 12f)
                val sy = center.y - cos(rad) * (radius - 12f)
                val ex = center.x + sin(rad) * radius
                val ey = center.y - cos(rad) * radius
                drawLine(
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    start = Offset(sx, sy),
                    end = Offset(ex, ey),
                    strokeWidth = 3f,
                    cap = StrokeCap.Round
                )
            }

            tick(-30f); tick(30f); tick(-60f); tick(60f)

            // ago
            val sign = if (isLeft) -1f else 1f
            val angle = fraction * 60f * sign
            val rad = Math.toRadians(angle.toDouble()).toFloat()
            val ex = center.x + sin(rad) * (radius - 8f)
            val ey = center.y - cos(rad) * (radius - 8f)
            drawLine(
                color = MaterialTheme.colorScheme.primary,
                start = center,
                end = Offset(ex, ey),
                strokeWidth = 6f,
                cap = StrokeCap.Round
            )
        }
        Text("Turn instruction: $label")
    }
}

/** produce una bitmap freccia “su” da ruotare */
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

private fun normDelta(d: Double): Double {
    var x = ((d + 540.0) % 360.0) - 180.0
    if (x > 180) x -= 360
    if (x < -180) x += 360
    return x
}

/** distanza punto->segmento in metri (approssimazione planare locale) */
private fun distanceToSegmentMeters(p: LatLon, a: LatLon, b: LatLon): Double {
    val mPerDegLat = 111_132.0
    val mPerDegLon =
        111_320.0 * cos(Math.toRadians(((a.lat + b.lat + p.lat) / 3.0)))
    data class P(val x: Double, val y: Double)
    fun toXY(ll: LatLon) = P((ll.lon - a.lon) * mPerDegLon, (ll.lat - a.lat) * mPerDegLat)

    val A = P(0.0, 0.0)
    val B = toXY(b)
    val Pp = toXY(p)

    val ABx = B.x - A.x; val ABy = B.y - A.y
    val APx = Pp.x - A.x; val APy = Pp.y - A.y
    val ab2 = ABx*ABx + ABy*ABy
    val t = if (ab2 <= 1e-9) 0.0 else ((APx*ABx + APy*ABy) / ab2).coerceIn(0.0, 1.0)
    val Hx = A.x + ABx * t; val Hy = A.y + ABy * t
    val dx = Pp.x - Hx; val dy = Pp.y - Hy
    return sqrt(dx*dx + dy*dy)
}

private fun formatTime(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    val m = s / 60
    val ss = s % 60
    return "%d:%02d".format(m, ss)
}

private fun formatSignedTime(seconds: Double): String {
    val sign = if (seconds >= 0) "+" else "-"
    val s = kotlin.math.abs(seconds).toInt()
    val m = s / 60
    val ss = s % 60
    return "$sign%02d:%02d".format(m, ss)
}
