package com.perseitech.sailpilot.routing

import kotlin.math.ceil
import kotlin.math.max

data class GridConfig(
    val padMeters: Double,
    val targetCellMeters: Double,
    val bbox: BBox,
    val rows: Int,
    val cols: Int,
    val stepLatDeg: Double,
    val stepLonDeg: Double
) {
    companion object {
        fun build(raw: BBox, padMeters: Double, targetCellMeters: Double): GridConfig {
            val latPadDeg = GeoUtils.metersToDegLat(padMeters)
            val lonPadDeg = GeoUtils.metersToDegLon(padMeters, raw.center.lat)
            val padded = BBox(
                minLat = raw.minLat - latPadDeg,
                minLon = raw.minLon - lonPadDeg,
                maxLat = raw.maxLat + latPadDeg,
                maxLon = raw.maxLon + lonPadDeg
            )
            val stepLatDeg = GeoUtils.metersToDegLat(targetCellMeters)
            val stepLonDeg = GeoUtils.metersToDegLon(targetCellMeters, padded.center.lat)
            val rows = max(1, ceil(padded.heightDeg / stepLatDeg).toInt())
            val cols = max(1, ceil(padded.widthDeg / stepLonDeg).toInt())
            return GridConfig(
                padMeters = padMeters,
                targetCellMeters = targetCellMeters,
                bbox = padded,
                rows = rows,
                cols = cols,
                stepLatDeg = stepLatDeg,
                stepLonDeg = stepLonDeg
            )
        }
    }

    fun latAt(row: Int): Double = bbox.minLat + (row + 0.5) * stepLatDeg
    fun lonAt(col: Int): Double = bbox.minLon + (col + 0.5) * stepLonDeg

    /** (lat,lon) → (row,col). Restituisce null se fuori griglia. */
    fun latLonToRC(p: LatLon): Pair<Int, Int>? {
        val r = ((p.lat - bbox.minLat) / stepLatDeg).toInt()
        val c = ((p.lon - bbox.minLon) / stepLonDeg).toInt()
        return if (r in 0 until rows && c in 0 until cols) r to c else null
    }

    fun rcToLatLon(r: Int, c: Int): LatLon =
        LatLon(latAt(r), lonAt(c))
}
