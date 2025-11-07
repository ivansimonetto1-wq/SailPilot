package com.perseitech.sailpilot.routing


import java.util.ArrayDeque


object SnapToSea {
    /** Restituisce il primo centro‑cella acqua trovato entro rMaxCells dal punto. */
    fun snap(p: LatLon, land: LandMask, rMaxCells: Int = 64): LatLon {
        val (r0, c0) = land.cfg.latLonToRC(p)
        if (!land.isLand(r0, c0)) return land.cfg.rcToLatLon(r0, c0)
        val rows = land.cfg.rows
        val cols = land.cfg.cols
        val vis = Array(rows) { BooleanArray(cols) }
        val q: ArrayDeque<Pair<Int,Int>> = ArrayDeque()
        fun push(r:Int,c:Int){ if (r in 0 until rows && c in 0 until cols && !vis[r][c]) { vis[r][c]=true; q.add(Pair(r,c)) } }
        push(r0,c0)
        var steps = 0
        while (q.isNotEmpty() && steps++ < rMaxCells * rMaxCells) {
            val (r,c) = q.removeFirst()
            if (!land.isLand(r,c)) return land.cfg.rcToLatLon(r,c)
            push(r+1,c); push(r-1,c); push(r,c+1); push(r,c-1)
        }
        error("SnapToSea fallito entro rMaxCells=$rMaxCells")
    }
}