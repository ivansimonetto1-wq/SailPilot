package com.perseitech.sailpilot.routing

import java.util.ArrayDeque
import kotlin.math.abs

/**
 * Trova la prima cella di mare vicina al punto (BFS su celle).
 * Se il punto è già su mare, restituisce il centro della sua cella.
 * Se non trova mare entro rMaxCells, restituisce il punto originale.
 */
object SnapToSea {

    fun snap(p: LatLon, land: LandMask, rMaxCells: Int = 64): LatLon {
        val rc0 = land.cfg.latLonToRC(p) ?: return p  // fuori griglia: torna il punto com'è
        val (r0, c0) = rc0
        if (!land.isLand(r0, c0)) {
            return land.cfg.rcToLatLon(r0, c0)
        }

        val rows = land.cfg.rows
        val cols = land.cfg.cols
        val seen = Array(rows) { BooleanArray(cols) }
        val q: ArrayDeque<Pair<Int, Int>> = ArrayDeque()

        fun push(r: Int, c: Int) {
            if (r in 0 until rows && c in 0 until cols &&
                !seen[r][c] &&
                abs(r - r0) <= rMaxCells && abs(c - c0) <= rMaxCells
            ) {
                seen[r][c] = true
                q.add(Pair(r, c))
            }
        }

        // 8-neighbors BFS
        push(r0, c0)
        val dirs = arrayOf(
            intArrayOf(1, 0), intArrayOf(-1, 0), intArrayOf(0, 1), intArrayOf(0, -1),
            intArrayOf(1, 1), intArrayOf(1, -1), intArrayOf(-1, 1), intArrayOf(-1, -1)
        )

        while (q.isNotEmpty()) {
            val (r, c) = q.removeFirst()
            if (!land.isLand(r, c)) {
                return land.cfg.rcToLatLon(r, c)
            }
            for (d in dirs) push(r + d[0], c + d[1])
        }

        // Non trovato mare entro rMaxCells: fallback al punto originale
        return p
    }
}
