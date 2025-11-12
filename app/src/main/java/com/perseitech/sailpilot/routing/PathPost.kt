package com.perseitech.sailpilot.routing

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Post-processing della rotta:
 * - simplifyRouteSafe: Douglas–Peucker vincolato (no terra).
 * - smoothChaikinSafe: Chaikin selettivo, vincolato (no terra), per smussare gli spigoli.
 *
 * Tutti i “check terra” usano una scansione cella-per-cella sulla griglia della LandMask:
 * questo evita di “saltare” istmi sottili o punte tra due campioni.
 */
object PathPost {

    // --- util comuni (proiezione locale in metri) ---
    private fun metersPerDeg(lat: Double): Pair<Double, Double> {
        val latClamped = lat.coerceIn(-89.0, 89.0)
        val cosRef = cos(latClamped * PI / 180.0)
        val mPerDegLat = 111_132.0
        val mPerDegLon = 111_320.0 * cosRef
        return mPerDegLat to mPerDegLon
    }

    private fun mkXY(origin: LatLon): (LatLon) -> Pair<Double, Double> {
        val (mLat, mLon) = metersPerDeg(origin.lat)
        val oLat = origin.lat
        val oLon = origin.lon
        return { p -> ((p.lon - oLon) * mLon) to ((p.lat - oLat) * mLat) }
    }

    private fun distPointToSegMeters(
        p: LatLon, a: LatLon, b: LatLon, toXY: (LatLon) -> Pair<Double, Double>
    ): Double {
        val (ax, ay) = toXY(a)
        val (bx, by) = toXY(b)
        val (px, py) = toXY(p)
        val vx = bx - ax
        val vy = by - ay
        val wx = px - ax
        val wy = py - ay
        val vv = vx * vx + vy * vy
        if (vv <= 1e-12) return hypot(px - ax, py - ay)
        val t = ((wx * vx + wy * vy) / vv).coerceIn(0.0, 1.0)
        val cx = ax + t * vx
        val cy = ay + t * vy
        return hypot(px - cx, py - cy)
    }

    // ---- check robusto: cammina cella-per-cella sulla griglia della LandMask ----
    private fun segmentCrossesLandGrid(a: LatLon, b: LatLon, land: LandMask): Boolean {
        val start = land.cfg.latLonToRC(a) ?: return true // OOB = terra
        val end   = land.cfg.latLonToRC(b) ?: return true

        var r = start.first.toDouble()
        var c = start.second.toDouble()
        val dr = (end.first - start.first).toDouble()
        val dc = (end.second - start.second).toDouble()
        val steps = max(kotlin.math.abs(dr), kotlin.math.abs(dc)).toInt().coerceAtLeast(1)
        val stepR = dr / steps
        val stepC = dc / steps

        for (i in 0..steps) {
            val ri = r.roundToInt()
            val ci = c.roundToInt()
            if (land.isLand(ri, ci)) return true
            r += stepR
            c += stepC
        }
        return false
    }

    /**
     * Douglas–Peucker vincolato: non elimina vertici se il segmento diretto attraversa terra.
     *
     * @param tolMeters tolleranza Douglas–Peucker (metri) per la deviazione massima consentita
     * @param sampleStepMeters ignorato (mantenuto per compatibilità chiamante)
     */
    fun simplifyRouteSafe(
        route: List<LatLon>,
        land: LandMask,
        tolMeters: Double,
        sampleStepMeters: Double
    ): List<LatLon> {
        if (route.size <= 2) return route

        val toXY = mkXY(route.first())
        val keep = BooleanArray(route.size)
        keep[0] = true
        keep[route.lastIndex] = true

        fun rdp(i0: Int, i1: Int) {
            if (i1 - i0 <= 1) return
            val A = route[i0]
            val B = route[i1]

            // Se il segmento diretto attraversa terra sulla griglia, DEVE essere diviso
            var mustSplit = segmentCrossesLandGrid(A, B, land)

            var maxD = -1.0
            var maxIdx = -1
            if (!mustSplit) {
                for (i in (i0 + 1) until i1) {
                    val d = distPointToSegMeters(route[i], A, B, toXY)
                    if (d > maxD) { maxD = d; maxIdx = i }
                }
                if (maxD > tolMeters) mustSplit = true
            }

            if (mustSplit) {
                val mid = if (maxIdx in (i0 + 1) until i1) maxIdx else (i0 + i1) / 2
                keep[mid] = true
                rdp(i0, mid)
                rdp(mid, i1)
            }
        }

        rdp(0, route.lastIndex)

        val out = ArrayList<LatLon>(keep.count { it })
        for (i in route.indices) if (keep[i]) out.add(route[i])
        return if (out.isEmpty()) route else out
    }

    /**
     * Chaikin smoothing selettivo e vincolato: per ogni segmento (Pi, Pi+1) genera Q,R
     * e li accetta solo se i tratti (Pi,Q), (Q,R), (R,Pi+1) non attraversano terra.
     */
    fun smoothChaikinSafe(
        route: List<LatLon>,
        land: LandMask,
        iters: Int,
        alpha: Double,
        sampleStepMeters: Double,
        keepEndpoints: Boolean = true
    ): List<LatLon> {
        if (route.size < 3 || iters <= 0) return route

        var cur = route
        repeat(iters) {
            val sm = ArrayList<LatLon>(cur.size * 2)
            if (keepEndpoints) sm.add(cur.first())
            for (i in 0 until cur.size - 1) {
                val p = cur[i]
                val q = cur[i + 1]
                val Q = LatLon(
                    lat = (1 - alpha) * p.lat + alpha * q.lat,
                    lon = (1 - alpha) * p.lon + alpha * q.lon
                )
                val R = LatLon(
                    lat = alpha * p.lat + (1 - alpha) * q.lat,
                    lon = alpha * p.lon + (1 - alpha) * q.lon
                )
                val ok =
                    !segmentCrossesLandGrid(p, Q, land) &&
                            !segmentCrossesLandGrid(Q, R, land) &&
                            !segmentCrossesLandGrid(R, q, land)

                if (ok) {
                    sm.add(Q); sm.add(R)
                } else {
                    // niente smoothing su questo tratto
                    sm.add(q)
                }
            }
            if (keepEndpoints && sm.last() != cur.last()) {
                // assicura l'endpoint finale
                if (!segmentCrossesLandGrid(sm.last(), cur.last(), land)) {
                    sm[sm.lastIndex] = cur.last()
                } else {
                    sm.add(cur.last())
                }
            }
            cur = sm
        }
        return cur
    }
}
