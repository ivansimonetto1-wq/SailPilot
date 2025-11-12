package com.perseitech.sailpilot.routing

/**
 * Provider di profondità. Ritorna la profondità in metri (valori positivi) oppure null se sconosciuta.
 * Esempi futuri: raster GeoTIFF offline, mbtiles, servizi locali, ecc.
 */
fun interface DepthProvider {
    fun depthMetersAt(lat: Double, lon: Double): Double?
}

/**
 * Restituisce una NUOVA LandMask dove le celle con profondità < [minDepthMeters] sono marcate come terra.
 * Se [provider] è null, ritorna la mask originale senza modifiche.
 */
fun LandMask.withShallowAsLand(provider: DepthProvider?, minDepthMeters: Double): LandMask {
    if (provider == null) return this
    val rows = cfg.rows
    val cols = cfg.cols
    val out = BooleanArray(rows * cols)
    fun idx(r: Int, c: Int) = r * cols + c

    for (r in 0 until rows) {
        for (c in 0 until cols) {
            val center = cfg.rcToLatLon(r, c)
            val d = provider.depthMetersAt(center.lat, center.lon)
            val shallow = (d != null && d < minDepthMeters)
            out[idx(r, c)] = isLand(r, c) || shallow
        }
    }
    return LandMask(cfg, out)
}
