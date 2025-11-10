package com.perseitech.sailpilot.routing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Render semplice della maschera e di un eventuale path.
 */
object DebugRenderer {

    /**
     * @param land  LandMask rasterizzata (true=terra)
     * @param path  polilinea in lat/lon (opzionale)
     * @param scale pixel per cella (>=1)
     */
    fun renderMaskWithPath(land: LandMask, path: List<LatLon>?, scale: Int = 2): Bitmap {
        require(scale >= 1) { "scale deve essere >= 1" }

        val w = land.cfg.cols * scale
        val h = land.cfg.rows * scale
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)

        val pLand = Paint().apply { color = Color.WHITE }   // terra
        val pSea  = Paint().apply { color = Color.BLACK }   // mare
        val pPath = Paint().apply {
            color = Color.RED
            strokeWidth = (scale.coerceAtLeast(2) / 2f)
            isAntiAlias = true
        }

        // Celle
        for (r in 0 until land.cfg.rows) {
            for (c in 0 until land.cfg.cols) {
                val left   = (c * scale).toFloat()
                val top    = (r * scale).toFloat()
                val right  = (left + scale).toFloat()
                val bottom = (top + scale).toFloat()
                val paint = if (land.isLand(r, c)) pLand else pSea
                cv.drawRect(left, top, right, bottom, paint)
            }
        }

        // Path
        if (path != null && path.size > 1) {
            var prev: Pair<Int, Int>? = null
            for (pt in path) {
                val rc = land.cfg.latLonToRC(pt)
                if (rc == null) {
                    prev = null
                    continue
                }
                if (prev != null) {
                    val (r1, c1) = prev!!
                    val (r2, c2) = rc
                    val x1 = c1 * scale + scale / 2f
                    val y1 = r1 * scale + scale / 2f
                    val x2 = c2 * scale + scale / 2f
                    val y2 = r2 * scale + scale / 2f
                    cv.drawLine(x1, y1, x2, y2, pPath)
                }
                prev = rc
            }
        }

        return bmp
    }
}
