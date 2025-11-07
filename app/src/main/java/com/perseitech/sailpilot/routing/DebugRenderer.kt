package com.perseitech.sailpilot.routing


import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint


object DebugRenderer {
    fun renderMaskWithPath(land: LandMask, path: List<LatLon>?, scale: Int = 2): Bitmap {
        val bmp = Bitmap.createBitmap(land.cfg.cols * scale, land.cfg.rows * scale, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val pFill = Paint().apply { color = Color.WHITE }
        val pSea = Paint().apply { color = Color.BLACK }
        val pPath = Paint().apply { color = Color.RED; strokeWidth = 2f }
        for (r in 0 until land.cfg.rows) for (c in 0 until land.cfg.cols) {
            val x = c*scale; val y = r*scale
            cv.drawRect(x.toFloat(), y.toFloat(), (x+scale).toFloat(), (y+scale).toFloat(), if (land.mask[r][c]) pFill else pSea)
        }
        if (path != null && path.size > 1) {
            for (i in 0 until path.lastIndex) {
                val (r1,c1) = land.cfg.latLonToRC(path[i])
                val (r2,c2) = land.cfg.latLonToRC(path[i+1])
                cv.drawLine((c1*scale+scale/2f), (r1*scale+scale/2f), (c2*scale+scale/2f), (r2*scale+scale/2f), pPath)
            }
        }
        return bmp
    }
}