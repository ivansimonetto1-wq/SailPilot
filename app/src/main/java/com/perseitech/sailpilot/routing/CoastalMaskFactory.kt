package com.perseitech.sailpilot.routing

/**
 * Factory per costruire rapidamente una LandMask a partire dal WKT
 * dentro una BBox (con pad) e risoluzione desiderata (targetCellMeters).
 *
 * Dipendenze attese:
 * - GridConfig.fromBBox(raw: BBox, padMeters: Double, targetCellMeters: Double): GridConfig
 * - LandMaskBuilder.fromWkt(landWkt: String, cfg: GridConfig): LandMask
 */
object CoastalMaskFactory {

    /**
     * Costruisce la LandMask:
     * 1) crea la GridConfig a partire dalla BBox e dai parametri,
     * 2) rasterizza il WKT sulla griglia (via LandMaskBuilder).
     */
    fun build(
        landWkt: String,
        bbox: BBox,
        padMeters: Double,
        targetCellMeters: Double
    ): LandMask {
        val cfg = GridConfig.fromBBox(bbox, padMeters, targetCellMeters)
        return LandMaskBuilder.fromWkt(landWkt, cfg)
    }
}
