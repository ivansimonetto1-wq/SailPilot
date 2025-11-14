package com.perseitech.sailpilot.regatta

import com.perseitech.sailpilot.routing.LatLon
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

enum class LegType { UPWIND, REACH, DOWNWIND }

enum class SailChoice {
    MAIN_JIB,      // randa + fiocco
    GENOA,         // randa + genoa
    CODE_ZERO,     // randa + code 0
    GENNAKER,      // randa + gennaker
    SPI,           // randa + spinnaker
    STAYSAIL       // staysail addizionale (vento forte)
}

/**
 * Una singola tratta della simulazione.
 */
data class SimLeg(
    val from: LatLon,
    val to: LatLon,
    val distanceNm: Double,
    val legType: LegType,
    val twaDeg: Double,
    val sail: SailChoice,
    val estSpeedKn: Double,
    val estTimeMin: Double
)

/**
 * Risultato complessivo della simulazione.
 */
data class RegattaSimulationResult(
    val legs: List<SimLeg>,
    val totalTimeMin: Double
)

/**
 * Motore di simulazione semplice:
 *  - rotta: COM → mark1 → mark2 → ... → PIN (se presenti)
 *  - per ogni tratta:
 *      - calcola bearing
 *      - calcola TWA = TWD - BRG
 *      - classifica andatura (bolina / traverso / poppa)
 *      - sceglie vela suggerita
 *      - stima velocità e tempo
 *
 * In futuro potremo sostituire estimateSpeed + chooseSail con polari reali per classe.
 */
object RegattaSimulator {

    fun simulate(
        course: RegattaCourse,
        twdDeg: Double,
        twsKn: Double
    ): RegattaSimulationResult? {
        val pts = mutableListOf<LatLon>()
        val c = course.committee
        val p = course.pin
        if (c != null) pts += c
        pts += course.marks
        if (p != null) pts += p
        if (pts.size < 2) return null

        val legs = mutableListOf<SimLeg>()

        for (i in 0 until pts.size - 1) {
            val a = pts[i]
            val b = pts[i + 1]
            val distM = haversineMeters(a, b)
            val distNm = distM / 1852.0
            val brg = bearing(a, b)
            val twa = norm180(twdDeg - brg)

            val legType = when (abs(twa)) {
                in 0.0..60.0   -> LegType.UPWIND
                in 60.0..120.0 -> LegType.REACH
                else           -> LegType.DOWNWIND
            }

            val sail = chooseSail(legType, twsKn)
            val speed = estimateSpeed(legType, twsKn)
            val timeH = if (speed > 0.1) distNm / speed else 0.0

            legs += SimLeg(
                from = a,
                to = b,
                distanceNm = distNm,
                legType = legType,
                twaDeg = twa,
                sail = sail,
                estSpeedKn = speed,
                estTimeMin = timeH * 60.0
            )
        }

        val total = legs.sumOf { it.estTimeMin }
        return RegattaSimulationResult(legs, total)
    }

    // --- HELPER INTERNI ---

    private fun chooseSail(type: LegType, twsKn: Double): SailChoice =
        when (type) {
            LegType.UPWIND ->
                if (twsKn <= 12) SailChoice.GENOA else SailChoice.MAIN_JIB

            LegType.REACH ->
                if (twsKn <= 16) SailChoice.CODE_ZERO else SailChoice.GENNAKER

            LegType.DOWNWIND ->
                if (twsKn <= 18) SailChoice.SPI else SailChoice.GENNAKER
        }

    /**
     * Stima ultra semplificata della velocità potenziale:
     * in futuro la sostituiremo con polari per classe.
     */
    private fun estimateSpeed(type: LegType, twsKn: Double): Double {
        val base = when (type) {
            LegType.UPWIND   -> 0.55   // 55% del TWS
            LegType.REACH    -> 0.80   // 80% del TWS
            LegType.DOWNWIND -> 0.70   // 70% del TWS
        }
        return (base * twsKn).coerceIn(2.0, 12.0)
    }

    private fun haversineMeters(a: LatLon, b: LatLon): Double {
        val R = 6371000.0
        val φ1 = Math.toRadians(a.lat)
        val φ2 = Math.toRadians(b.lat)
        val dφ = φ2 - φ1
        val dλ = Math.toRadians(b.lon - a.lon)
        val sinDφ = sin(dφ / 2)
        val sinDλ = sin(dλ / 2)
        val h = sinDφ * sinDφ + cos(φ1) * cos(φ2) * sinDλ * sinDλ
        val c = 2 * atan2(sqrt(h), sqrt(1 - h))
        return R * c
    }

    private fun bearing(a: LatLon, b: LatLon): Double {
        val φ1 = Math.toRadians(a.lat)
        val φ2 = Math.toRadians(b.lat)
        val λ1 = Math.toRadians(a.lon)
        val λ2 = Math.toRadians(b.lon)
        val dλ = λ2 - λ1
        val y = sin(dλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
        var deg = Math.toDegrees(atan2(y, x))
        if (deg < 0) deg += 360.0
        return deg
    }

    private fun norm180(d: Double): Double {
        var x = (d + 540.0) % 360.0 - 180.0
        if (x < -180) x += 360.0
        if (x > 180) x -= 360.0
        return x
    }
}
