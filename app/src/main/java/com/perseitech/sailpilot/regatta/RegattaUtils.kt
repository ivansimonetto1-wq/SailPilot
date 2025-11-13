package com.perseitech.sailpilot.regatta

import com.perseitech.sailpilot.routing.GeoUtils
import com.perseitech.sailpilot.routing.LatLon
import kotlin.math.*

/** Calcolo laylines (due punti a distanza lenM dal punto origine, a ± tackAngle rispetto al TWD). */
fun computeLaylines(
    origin: LatLon,
    twdDeg: Double,
    tackAngleDeg: Double,
    lenM: Double = 2000.0
): Pair<LatLon, LatLon> {
    fun dest(p: LatLon, brgDeg: Double, dM: Double): LatLon {
        val R = 6_371_000.0
        val δ = dM / R
        val θ = Math.toRadians(brgDeg)
        val φ1 = Math.toRadians(p.lat)
        val λ1 = Math.toRadians(p.lon)
        val φ2 = asin(sin(φ1) * cos(δ) + cos(φ1) * sin(δ) * cos(θ))
        val λ2 = λ1 + atan2(sin(θ) * sin(δ) * cos(φ1), cos(δ) - sin(φ1) * sin(φ2))
        return LatLon(Math.toDegrees(φ2), (Math.toDegrees(λ2) + 540) % 360 - 180)
    }
    val brgPort = (twdDeg + 180 - tackAngleDeg + 360) % 360
    val brgStar = (twdDeg + 180 + tackAngleDeg + 360) % 360
    return dest(origin, brgPort, lenM) to dest(origin, brgStar, lenM)
}
