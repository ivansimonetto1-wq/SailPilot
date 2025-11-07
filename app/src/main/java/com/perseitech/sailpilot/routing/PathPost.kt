package com.perseitech.sailpilot.routing


import kotlin.math.ceil


object PathPost {
    /** Densifica i segmenti a maxSpacingMeters. */
    fun densify(path: List<LatLon>, maxSpacingMeters: Double): List<LatLon> {
        if (path.size <= 1) return path
        val out = ArrayList<LatLon>()
        for (i in 0 until path.lastIndex) {
            val a = path[i]; val b = path[i+1]
            out.add(a)
            val d = GeoUtils.haversineMeters(a,b)
            val n = kotlin.math.max(0, ceil(d / maxSpacingMeters).toInt() - 1)
            for (k in 1..n) {
                val t = k.toDouble() / (n+1)
                val lat = a.lat + (b.lat - a.lat) * t
                val lon = a.lon + (b.lon - a.lon) * t
                out.add(LatLon(lat, lon))
            }
        }
        out.add(path.last())
        return out
    }


    /** Primo punto della polyline che cade su TERRA (restituisce null se tutto mare). */
    fun firstCollision(poly: List<LatLon>, land: LandMask): LatLon? {
        for (p in poly) {
            val (r,c) = land.cfg.latLonToRC(p)
            if (land.isLand(r,c)) return land.cfg.rcToLatLon(r,c)
        }
        return null
    }
}