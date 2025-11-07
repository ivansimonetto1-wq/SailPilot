package com.perseitech.sailpilot.routing
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryFactory

class LandMask(
    val cfg: GridConfig,
    val mask: Array<BooleanArray> // true = terra, false = mare
) {
    fun isLand(r: Int, c: Int): Boolean =
        r < 0 || c < 0 || r >= cfg.rows || c >= cfg.cols || mask[r][c]
}


object LandMaskBuilder {
    private val gf = GeometryFactory()


    fun rasterize(prepared: WktLandLoader.PreparedLand, cfg: GridConfig): LandMask {
        val m = Array(cfg.rows) { BooleanArray(cfg.cols) }
        for (r in 0 until cfg.rows) {
            for (c in 0 until cfg.cols) {
                val p = cfg.rcToLatLon(r, c)
                val pt = gf.createPoint(Coordinate(p.lon, p.lat))
                m[r][c] = prepared.prepared.covers(pt) // true = terra
            }
        }
        return LandMask(cfg, m)
    }


    /** Dilata morfologicamente la maschera di "padMeters". */
    fun dilate(mask: LandMask, padMeters: Double): LandMask {
        val cellMetersLat = GeoUtils.metersPerDegLat() * mask.cfg.cellDegLat
        val cellMetersLon = GeoUtils.metersPerDegLon(mask.cfg.latMid) * mask.cfg.cellDegLon
        val cellMeters = minOf(cellMetersLat, cellMetersLon)
        val n = kotlin.math.max(1, kotlin.math.round(padMeters / cellMeters).toInt())
        if (n <= 0) return mask
        val src = mask.mask
        val rows = mask.cfg.rows
        val cols = mask.cfg.cols
        val out = Array(rows) { BooleanArray(cols) }
// copia iniziale
        for (r in 0 until rows) {
            System.arraycopy(src[r], 0, out[r], 0, cols)
        }
// quadrato (che è conservativo) per n iterazioni
        repeat(n) {
            val tmp = Array(rows) { BooleanArray(cols) }
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    var land = out[r][c]
                    if (!land) {
                        loop@ for (dr in -1..1) for (dc in -1..1) {
                            val rr = r + dr; val cc = c + dc
                            if (rr in 0 until rows && cc in 0 until cols && out[rr][cc]) { land = true; break@loop }
                        }
                    }
                    tmp[r][c] = land
                }
            }
            for (r in 0 until rows) System.arraycopy(tmp[r], 0, out[r], 0, cols)
        }
        return LandMask(mask.cfg, out)
    }
}