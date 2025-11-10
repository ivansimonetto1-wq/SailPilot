package com.perseitech.sailpilot.routing

import kotlin.math.abs

/** Semplice ring (polilinea chiusa) in coordinate geografiche. */
private data class Ring(val pts: List<LatLon>)
private data class MultiPolygon(val polygons: List<Polygon>)
private data class Polygon(val outer: Ring, val holes: List<Ring>)

/** Point in polygon (ray casting). */
private fun pointInRing(p: LatLon, ring: Ring): Boolean {
    var inside = false
    val n = ring.pts.size
    var j = n - 1
    for (i in 0 until n) {
        val pi = ring.pts[i]; val pj = ring.pts[j]
        val xi = pi.lon; val yi = pi.lat
        val xj = pj.lon; val yj = pj.lat
        val intersect = ((yi > p.lat) != (yj > p.lat)) &&
                (p.lon < (xj - xi) * (p.lat - yi) / ((yj - yi).takeIf { it != 0.0 } ?: 1e-12) + xi)
        if (intersect) inside = !inside
        j = i
    }
    return inside
}

private fun pointInPolygon(p: LatLon, poly: Polygon): Boolean {
    if (!pointInRing(p, poly.outer)) return false
    for (h in poly.holes) if (pointInRing(p, h)) return false
    return true
}

/** Parser minimale MULTIPOLYGON WKT (coord in ordine lon lat). */
private object WktLandLoader {
    fun parseMultiPolygon(wkt: String): MultiPolygon {
        val s = wkt.trim()
        require(s.startsWith("MULTIPOLYGON", ignoreCase = true)) { "WKT non supportato: atteso MULTIPOLYGON" }
        // Togli prefisso e parentesi esterne
        val body = s.substringAfter("(", "").dropLast(1).trim()
        // Split level-1 su ")),((" preservando gli anelli interni
        val polysRaw = splitTop(body, ")),((")
        val polys = polysRaw.map { polyRaw ->
            val ringsRaw = splitTop(polyRaw.trim().trim('(', ')'), "),(")
            val rings = ringsRaw.map { ringRaw ->
                val pts = ringRaw.split(",").map { token ->
                    val (x, y) = token.trim().split(Regex("\\s+"))
                    val lon = x.toDouble()
                    val lat = y.toDouble()
                    LatLon(lat, lon)
                }
                // chiudi se non chiuso
                val closed = if (pts.isNotEmpty() && (abs(pts.first().lat - pts.last().lat) > 1e-12 || abs(pts.first().lon - pts.last().lon) > 1e-12))
                    pts + pts.first() else pts
                Ring(closed)
            }
            Polygon(outer = rings.first(), holes = if (rings.size > 1) rings.drop(1) else emptyList())
        }
        return MultiPolygon(polys)
    }

    /** Split che rispetta il livello di parentesi (grezzo ma efficace per WKT). */
    private fun splitTop(s: String, sep: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        var i = 0
        var last = 0
        while (i <= s.length - 1) {
            val ch = s[i]
            when (ch) {
                '(' -> depth++
                ')' -> depth--
            }
            if (depth == 0 && i + sep.length <= s.length && s.substring(i, i + sep.length) == sep) {
                out += s.substring(last, i)
                i += sep.length
                last = i
                continue
            }
            i++
        }
        out += s.substring(last)
        return out
    }
}

/** Griglia booleana: true=terra, false=mare. */
class LandMask(
    val cfg: GridConfig,
    private val cells: BooleanArray
) {
    fun isLand(r: Int, c: Int): Boolean =
        if (r !in 0 until cfg.rows || c !in 0 until cfg.cols) true
        else cells[r * cfg.cols + c]

    fun isLand(p: LatLon): Boolean {
        val rc = cfg.latLonToRC(p) ?: return true
        return isLand(rc.first, rc.second)
    }
    fun isSea(p: LatLon): Boolean = !isLand(p)
}

/** Costruzione LandMask da WKT + GridConfig (campiono il centro cella). */
object LandMaskBuilder {
    fun fromWkt(landWkt: String, cfg: GridConfig): LandMask {
        val mp = WktLandLoader.parseMultiPolygon(landWkt)
        val cells = BooleanArray(cfg.rows * cfg.cols)
        for (r in 0 until cfg.rows) for (c in 0 until cfg.cols) {
            val p = cfg.rcToLatLon(r, c) // centro cella
            val terra = mp.polygons.any { poly -> pointInPolygon(p, poly) }
            cells[r * cfg.cols + c] = terra
        }
        return LandMask(cfg, cells)
    }
}
