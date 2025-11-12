package com.perseitech.sailpilot.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.*

@Composable
fun CompassPanel(
    cogDeg: Double?,          // ago nero
    courseToWpDeg: Double?,   // ago verde
    sogKn: Double?,           // banner
    etaText: String?          // banner
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
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Compass", color = Color.White, fontSize = 18.sp)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                CompassGauge(cogDeg = cogDeg, targetDeg = courseToWpDeg)
            }

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Banner("COG", cogDeg?.let { "${it.toInt()}°" } ?: "—")
                Banner("Course→WP", courseToWpDeg?.let { "${it.toInt()}°" } ?: "—")
                Banner("SOG", sogKn?.let { "%.1f kn".format(it) } ?: "—")
                Banner("ETA→WP", etaText ?: "—")
            }
        }
    }
}

@Composable
private fun Banner(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color.LightGray, fontSize = 12.sp)
        Text(value, color = Color.White, fontSize = 20.sp)
    }
}

@Composable
private fun CompassGauge(
    cogDeg: Double?,
    targetDeg: Double?
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val r = min(w, h) * 0.38f
        val center = Offset(w / 2f, h / 2f)

        // anello esterno
        drawCircle(Color(0xFFDDDDDD), radius = r, style = Stroke(width = 6f))

        // tacche cardinali + lettere con Canvas Android
        val cardinals = listOf(0 to "N", 90 to "E", 180 to "S", 270 to "W")
        val textPaint = Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 34f
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        cardinals.forEach { (deg, label) ->
            val ang = Math.toRadians((deg - 90).toDouble()).toFloat()
            val p1 = Offset(
                center.x + (r - 18f) * cos(ang),
                center.y + (r - 18f) * sin(ang)
            )
            val p2 = Offset(
                center.x + r * cos(ang),
                center.y + r * sin(ang)
            )
            drawLine(Color.White, p1, p2, strokeWidth = 4f, cap = StrokeCap.Round)

            // testo
            val tx = center.x + (r - 36f) * cos(ang)
            val ty = center.y + (r - 36f) * sin(ang) + 12f
            drawContext.canvas.nativeCanvas.drawText(label, tx, ty, textPaint)
        }

        fun drawNeedle(deg: Double, col: Color, lenMul: Float, width: Float) {
            val ang = Math.toRadians((deg - 90)).toFloat()
            val p = Offset(
                center.x + r * lenMul * cos(ang),
                center.y + r * lenMul * sin(ang)
            )
            drawLine(col, center, p, strokeWidth = width, cap = StrokeCap.Round)
        }

        // target (verde) prima, poi COG (nero) sopra
        targetDeg?.let { drawNeedle(it, Color(0xFF00E676), 0.9f, 10f) }
        cogDeg?.let { drawNeedle(it, Color.Black, 1.0f, 14f) }
    }
}
