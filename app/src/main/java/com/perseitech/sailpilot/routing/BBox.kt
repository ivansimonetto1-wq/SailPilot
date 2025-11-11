package com.perseitech.sailpilot.routing

data class BBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
) {
    val widthDeg: Double get() = (maxLon - minLon).coerceAtLeast(0.0)
    val heightDeg: Double get() = (maxLat - minLat).coerceAtLeast(0.0)
    val center: LatLon get() = LatLon((minLat + maxLat) * 0.5, (minLon + maxLon) * 0.5)

    companion object {
        fun around(a: LatLon, b: LatLon): BBox {
            val minLat = kotlin.math.min(a.lat, b.lat)
            val maxLat = kotlin.math.max(a.lat, b.lat)
            val minLon = kotlin.math.min(a.lon, b.lon)
            val maxLon = kotlin.math.max(a.lon, b.lon)
            return BBox(minLat, minLon, maxLat, maxLon)
        }
    }
}
