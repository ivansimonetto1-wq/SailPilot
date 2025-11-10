package com.perseitech.sailpilot.routing

import kotlin.math.ceil
import kotlin.math.max

object PathPost {
    /** Densifica i segmenti a maxSpacingMeters per avere una polilinea “morbida”. */
    fun densify(path: List<LatLon>, maxSpacingMeters: Double): List<LatLon> {
        if (path.size <= 1) return path
        val out = ArrayList<LatLon>()
        for (i in 0 until path.lastIndex) {
            val a = path[i]; val b = path[i+1]
            out.add(a)
            val d = GeoUtils.haversineMeters(a,b)
            val n = max(0, ceil(d / maxSpacingMeters).toInt() - 1)
            for (k in 1..n) {
                val t = k.toDouble() / (n+1)
                out.add(LatLon(a.lat + (b.lat - a.lat) * t, a.lon + (b.lon - a.lon) * t))
            }
        }
        out.add(path.last())
        return out
    }
}
