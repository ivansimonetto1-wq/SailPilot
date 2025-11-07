package com.perseitech.sailpilot.routing


data class LatLon(val lat: Double, val lon: Double)


data class BBox(val minLat: Double, val minLon: Double, val maxLat: Double, val maxLon: Double) {
    fun clamp(p: LatLon): LatLon = LatLon(
        lat = p.lat.coerceIn(minLat, maxLat),
        lon = p.lon.coerceIn(minLon, maxLon)
    )
}