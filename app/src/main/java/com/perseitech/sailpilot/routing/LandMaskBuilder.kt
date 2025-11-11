package com.perseitech.sailpilot.routing

/**
 * Costruisce una LandMask discretizzando il MULTIPOLYGON WKT su una griglia.
 * Implementazione semplice: valuta il centro di ogni cella con point-in-polygon.
 * Assunzione: WKT in ordine standard "lon lat" (X Y); LatLon è (lat, lon).
 */
object LandMaskBuilder {

    fun fromWkt(landWkt: String, cfg: GridConfig): LandMask {
        // parse MULTIPOLYGON -> List<Polygon>, Polygon = List<Ring>, Ring = List<LatLon>
        val multipolygon: List<List<List<LatLon>>> = WktUtils.parseMultiPolygon(landWkt)

        // Celle: true = terra, false = mare
        val cells = BooleanArray(cfg.rows * cfg.cols)

        // Point-in-ring (ray casting) usando lat come Y e lon come X
        fun pointInRing(p: LatLon, ring: List<LatLon>): Boolean {
            var inside = false
            var j = ring.lastIndex
            for (i in ring.indices) {
                val a = ring[i]
                val b = ring[j]
                // Controllo attraversamento del segmento verticale al livello di p.lat
                val cond = ((a.lat > p.lat) != (b.lat > p.lat))
                if (cond) {
                    val xInt = (b.lon - a.lon) * (p.lat - a.lat) / ((b.lat - a.lat) + 1e-12) + a.lon
                    if (p.lon < xInt) inside = !inside
                }
                j = i
            }
            return inside
        }

        fun pointInPolygon(p: LatLon, poly: List<List<LatLon>>): Boolean {
            if (poly.isEmpty()) return false
            // anello 0 = esterno; successivi = buchi
            if (!pointInRing(p, poly[0])) return false
            for (k in 1 until poly.size) if (pointInRing(p, poly[k])) return false
            return true
        }

        // Campiona il centro di ciascuna cella
        for (r in 0 until cfg.rows) {
            val lat = cfg.latAt(r) + cfg.stepLatDeg * 0.5
            for (c in 0 until cfg.cols) {
                val lon = cfg.lonAt(c) + cfg.stepLonDeg * 0.5
                val p = LatLon(lat, lon)

                var isLand = false
                for (poly in multipolygon) {
                    if (pointInPolygon(p, poly)) { isLand = true; break }
                }
                cells[r * cfg.cols + c] = isLand
            }
        }

        // NB: qui NON passiamo 'bbox' perché il tuo LandMask non lo prevede
        return LandMask(
            cfg = cfg,
            cells = cells
        )
    }
}
