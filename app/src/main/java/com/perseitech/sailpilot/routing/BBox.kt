package com.perseitech.sailpilot.routing

/**
 * Bounding box in gradi (WGS84).
 * Garantisce che minLat <= maxLat e minLon <= maxLon.
 */
data class BBox(
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double
) {
    init {
        require(!minLat.isNaN() && !minLon.isNaN() && !maxLat.isNaN() && !maxLon.isNaN()) { "NaN in BBox" }
        require(maxLat >= minLat) { "maxLat < minLat" }
        require(maxLon >= minLon) { "maxLon < minLon" }
    }

    val widthDeg: Double get() = maxLon - minLon
    val heightDeg: Double get() = maxLat - minLat
    val center: LatLon get() = LatLon(
        lat = (minLat + maxLat) * 0.5,
        lon = (minLon + maxLon) * 0.5
    )

    fun contains(p: LatLon): Boolean =
        p.lat in minLat..maxLat && p.lon in minLon..maxLon

    fun expand(dLat: Double, dLon: Double): BBox =
        BBox(minLat - dLat, minLon - dLon, maxLat + dLat, maxLon + dLon)

    companion object {
        /** Crea una bbox ordinata a partire da 2 punti qualsiasi. */
        fun fromPoints(a: LatLon, b: LatLon): BBox {
            val minLat = kotlin.math.min(a.lat, b.lat)
            val maxLat = kotlin.math.max(a.lat, b.lat)
            val minLon = kotlin.math.min(a.lon, b.lon)
            val maxLon = kotlin.math.max(a.lon, b.lon)
            return BBox(minLat, minLon, maxLat, maxLon)
        }
    }
}
