package com.perseitech.sailpilot.routing


import kotlin.math.*


object GeoUtils {
    private const val M_PER_DEG_LAT = 111_320.0


    fun metersPerDegLat(): Double = M_PER_DEG_LAT


    fun metersPerDegLon(latDeg: Double): Double = M_PER_DEG_LAT * cos(Math.toRadians(latDeg))


    fun haversineMeters(a: LatLon, b: LatLon): Double {
        val R = 6371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val s = sin(dLat/2).pow(2.0) + cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon/2).pow(2.0)
        return 2 * R * asin(min(1.0, sqrt(s)))
    }
}