package com.perseitech.sailpilot.routing

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

data class GridConfig(
    val bbox: BBox,
    val rows: Int,
    val cols: Int,
    val stepLatDeg: Double,
    val stepLonDeg: Double
) {
    companion object {
        /**
         * Crea una griglia a partire da BBox logica + pad in metri e lato cella target in metri.
         */
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
            val cols = max(1, ceil(padded.widthDeg  / stepLonDeg).toInt())

            return GridConfig(
                bbox = padded,
                rows = rows,
                cols = cols,
                stepLatDeg = stepLatDeg,
                stepLonDeg = stepLonDeg
            )
        }
    }

    fun latAt(row: Int): Double = bbox.minLat + row * stepLatDeg
    fun lonAt(col: Int): Double = bbox.minLon + col * stepLonDeg

    fun latLonToRC(p: LatLon): Pair<Int, Int>? {
        val r = floor((p.lat - bbox.minLat) / stepLatDeg).toInt()
        val c = floor((p.lon - bbox.minLon) / stepLonDeg).toInt()
        return if (r in 0 until rows && c in 0 until cols) r to c else null
    }

    fun rcToLatLon(r: Int, c: Int): LatLon {
        val lat = bbox.minLat + (r + 0.5) * stepLatDeg
        val lon = bbox.minLon + (c + 0.5) * stepLonDeg
        return LatLon(lat, lon)
    }
}
