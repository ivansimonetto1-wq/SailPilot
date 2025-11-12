package com.perseitech.sailpilot.routing

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min

class SeaRouter {

    companion object {
        private const val MAX_CELLS = 1_200_000
        private const val UPSCALE_FACTOR = 1.25
        private const val MAX_UPSCALES = 6
    }

    /**
     * @param depthProvider opzionale: se presente, evita profondità < [minDepthMeters]
     * @param minDepthMeters soglia minima (m). Ignorata se depthProvider = null.
     */
    fun route(
        landWkt: String,
        start: LatLon,
        goal: LatLon,
        bbox: BBox,
        padMeters: Double,
        targetCellMeters: Double,
        depthProvider: DepthProvider? = null,
        minDepthMeters: Double = 2.0
    ): List<LatLon>? {
        var cell = targetCellMeters
        var attempt = 0
        var cfg: GridConfig

        while (true) {
            cfg = GridConfig.fromBBox(bbox, padMeters, cell)
            val estCells = cfg.rows.toLong() * cfg.cols.toLong()
            if (estCells <= MAX_CELLS || attempt >= MAX_UPSCALES) break
            cell *= UPSCALE_FACTOR
            attempt++
        }

        var mask = LandMaskBuilder.fromWkt(landWkt, cfg)
        // se c'è un provider batimetrico, “aggiungi” acque basse come se fossero terra
        mask = mask.withShallowAsLand(depthProvider, minDepthMeters)

        val raw = AStar.route(mask, start, goal) ?: return null

        val lat0 = cfg.bbox.center.lat.coerceIn(-89.0, 89.0)
        val mPerDegLat = 111_132.0
        val mPerDegLon = 111_320.0 * cos(lat0 * PI / 180.0)
        val cellLatM = cfg.stepLatDeg * mPerDegLat
        val cellLonM = cfg.stepLonDeg * mPerDegLon
        val cellM = min(cellLatM, cellLonM)

        val tol = 1.6 * cellM
        val sample = 0.5 * cellM

        val simplified = PathPost.simplifyRouteSafe(raw, mask.dilated(1), tolMeters = tol, sampleStepMeters = sample)
        val smoothed = PathPost.smoothChaikinSafe(
            route = simplified, land = mask.dilated(1), iters = 1, alpha = 0.25, sampleStepMeters = sample, keepEndpoints = true
        )
        return smoothed
    }
}
