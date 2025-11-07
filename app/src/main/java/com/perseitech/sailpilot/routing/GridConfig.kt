package com.perseitech.sailpilot.routing


import kotlin.math.*


class GridConfig(
    val bbox: BBox,
    val targetCellMeters: Double
) {
    val latMid: Double = (bbox.minLat + bbox.maxLat) * 0.5
    val mPerDegLat: Double = GeoUtils.metersPerDegLat()
    val mPerDegLon: Double = GeoUtils.metersPerDegLon(latMid)


    val cellDegLat: Double = targetCellMeters / mPerDegLat
    val cellDegLon: Double = targetCellMeters / mPerDegLon


    val rows: Int = max(1.0, ceil((bbox.maxLat - bbox.minLat) / cellDegLat)).toInt()
    val cols: Int = max(1.0, ceil((bbox.maxLon - bbox.minLon) / cellDegLon)).toInt()


    fun rcToLatLon(r: Int, c: Int): LatLon {
        val lat = bbox.maxLat - (r + 0.5) * cellDegLat
        val lon = bbox.minLon + (c + 0.5) * cellDegLon
        return LatLon(lat, lon)
    }


    fun latLonToRC(p: LatLon): Pair<Int, Int> {
        val r = ((bbox.maxLat - p.lat) / cellDegLat).toInt()
        val c = ((p.lon - bbox.minLon) / cellDegLon).toInt()
        return Pair(r, c)
    }
}