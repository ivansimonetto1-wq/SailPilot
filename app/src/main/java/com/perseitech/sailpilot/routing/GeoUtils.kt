package com.perseitech.sailpilot.routing

import kotlin.math.*

object GeoUtils {
    private const val EARTH_RADIUS_M = 6_371_008.8
    private const val METERS_PER_DEG_LAT = 111_320.0

    // --- gradi/metri ---
    fun metersToDegLat(m: Double): Double = m / METERS_PER_DEG_LAT

    fun metersToDegLon(m: Double, latDeg: Double): Double {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(latDeg.toRad())
        return m / metersPerDegLon.coerceAtLeast(1e-9)
    }

    fun degLatToMeters(d: Double): Double = d * METERS_PER_DEG_LAT

    fun degLonToMeters(d: Double, latDeg: Double): Double {
        val metersPerDegLon = METERS_PER_DEG_LAT * cos(latDeg.toRad())
        return d * metersPerDegLon
    }

    // --- distanze ---
    /** Distanza sferica (Haversine) in metri. */
    fun haversineMeters(a: LatLon, b: LatLon): Double {
        val φ1 = a.lat.toRad(); val φ2 = b.lat.toRad()
        val dφ = (b.lat - a.lat).toRad()
        val dλ = (b.lon - a.lon).toRad()
        val s = sin(dφ / 2).pow(2) + cos(φ1) * cos(φ2) * sin(dλ / 2).pow(2)
        return 2 * EARTH_RADIUS_M * atan2(sqrt(s), sqrt(1 - s))
    }

    /** Alias usato dall'A*: identico a haversineMeters. */
    fun distanceMeters(a: LatLon, b: LatLon): Double = haversineMeters(a, b)

    // --- utils ---
    private fun Double.toRad() = this * Math.PI / 180.0
}
