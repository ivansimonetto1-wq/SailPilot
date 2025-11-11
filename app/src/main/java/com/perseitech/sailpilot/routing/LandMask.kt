package com.perseitech.sailpilot.routing

import kotlin.math.floor

/**
 * Raster di terra/ mare su griglia definita da GridConfig.
 * cells[ind] = true  -> TERRA
 * cells[ind] = false -> MARE
 */
class LandMask(
    val cfg: GridConfig,
    private val cells: BooleanArray
) {
    val rows: Int get() = cfg.rows
    val cols: Int get() = cfg.cols

    /** Indice lineare r,c -> idx nell’array cells */
    private fun idx(r: Int, c: Int): Int = r * cols + c

    /** true se la cella è terra (OOB = terra per sicurezza) */
    fun isLand(r: Int, c: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return true
        return cells[idx(r, c)]
    }

    /** test puntuale con conversione lat/lon -> r,c */
    fun isLand(lat: Double, lon: Double): Boolean {
        val rc = cfg.latLonToRC(LatLon(lat, lon)) ?: return true
        return isLand(rc.first, rc.second)
    }

    fun isLand(p: LatLon): Boolean = isLand(p.lat, p.lon)
    fun isSea(r: Int, c: Int): Boolean = !isLand(r, c)
    fun isSea(p: LatLon): Boolean = !isLand(p)
}
