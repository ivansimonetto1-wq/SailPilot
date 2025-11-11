package com.perseitech.sailpilot.routing

/** Parser minimale per MULTIPOLYGON/POLYGON WKT + test punto-in-poligono (ray casting). */
class WktLandIndex(private val polys: List<Polygon>) {

    data class Polygon(val outer: List<LatLon>, val holes: List<List<LatLon>> = emptyList())

    fun contains(lat: Double, lon: Double): Boolean {
        val p = LatLon(lat, lon)
        for (poly in polys) {
            if (inRing(p, poly.outer)) {
                var inHole = false
                for (h in poly.holes) if (inRing(p, h)) { inHole = true; break }
                if (!inHole) return true
            }
        }
        return false
    }
    fun contains(p: LatLon) = contains(p.lat, p.lon)

    private fun inRing(p: LatLon, ring: List<LatLon>): Boolean {
        var inside = false
        var j = ring.lastIndex
        for (i in ring.indices) {
            val pi = ring[i]; val pj = ring[j]
            val intersect = ((pi.lon > p.lon) != (pj.lon > p.lon)) &&
                    (p.lat < (pj.lat - pi.lat) * (p.lon - pi.lon) / ((pj.lon - pi.lon).takeIf { it != 0.0 } ?: 1e-12) + pi.lat)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    companion object {
        fun parse(wkt: String): WktLandIndex {
            val s = wkt.trim().uppercase()
            return when {
                s.startsWith("MULTIPOLYGON") -> fromMultiPolygon(wkt)
                s.startsWith("POLYGON") -> fromPolygon(wkt)
                else -> WktLandIndex(emptyList())
            }
        }

        private fun fromPolygon(wkt: String): WktLandIndex {
            val rings = extractRingsInside(wkt, "POLYGON")
            val poly = ringsToPolygon(rings)
            return WktLandIndex(listOf(poly))
        }

        private fun fromMultiPolygon(wkt: String): WktLandIndex {
            // MULTIPOLYGON ( ( (x y, ...), (hole...), ... ), ( ( ... ) ), ... )
            val inner = wkt.substringAfter("(", "").substringBeforeLast(")", "")
            val polys = mutableListOf<Polygon>()
            var level = 0
            var token = StringBuilder()
            fun flush() {
                val t = token.toString().trim()
                if (t.isNotEmpty()) {
                    val rings = extractRings(t)
                    polys += ringsToPolygon(rings)
                }
                token = StringBuilder()
            }
            for (ch in inner) {
                if (ch == '(') { level++; if (level == 1) continue }
                if (ch == ')') { level--; if (level == 0) { flush(); continue } }
                if (level >= 1) token.append(ch)
            }
            return WktLandIndex(polys)
        }

        /** Estrae le ring da "lat lon, lat lon, ..." liste separate da ')' ',' '(' */
        private fun extractRingsInside(wkt: String, head: String): List<List<LatLon>> {
            val inner = wkt.substringAfter("${head}", "")
                .substringAfter("(", "")
                .substringBeforeLast(")", "")
            return extractRings(inner)
        }

        private fun extractRings(s: String): List<List<LatLon>> {
            // s = "(x y, x y, ...),(x y, ...),..."
            val rings = mutableListOf<List<LatLon>>()
            var level = 0
            var token = StringBuilder()
            fun flush() {
                val t = token.toString().trim()
                if (t.isNotEmpty()) rings += parseCoords(t)
                token = StringBuilder()
            }
            for (ch in s) {
                when (ch) {
                    '(' -> { level++; if (level == 1) continue }
                    ')' -> { level--; if (level == 0) { flush(); continue } }
                }
                if (level >= 1) token.append(ch)
            }
            if (level == 0 && token.isNotEmpty()) flush()
            return rings
        }

        private fun parseCoords(s: String): List<LatLon> {
            // supporta "lon lat" o "lat lon"? Assumiamo WKT standard lon lat.
            // Ma i tuoi log mostrano "15.82602 43.69..." (lon,lat). Convertiamo così:
            return s.split(',').mapNotNull { p ->
                val t = p.trim().split(Regex("\\s+"))
                if (t.size >= 2) {
                    val x = t[0].toDoubleOrNull()
                    val y = t[1].toDoubleOrNull()
                    if (x != null && y != null) LatLon(y, x) else null
                } else null
            }
        }

        private fun ringsToPolygon(rings: List<List<LatLon>>): Polygon {
            if (rings.isEmpty()) return Polygon(emptyList())
            val outer = rings.first()
            val holes = if (rings.size > 1) rings.drop(1) else emptyList()
            return Polygon(outer, holes)
        }
    }
}
