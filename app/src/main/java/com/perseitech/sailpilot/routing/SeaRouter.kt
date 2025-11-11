package com.perseitech.sailpilot.routing

import android.util.Log

/**
 * Router principale: costruisce la griglia dalla BBox, rasterizza il WKT su LandMask,
 * "aggancia" start/goal al mare se cadono su terra e lancia l'A*.
 */
class SeaRouter {

    fun route(
        landWkt: String,
        start: LatLon,
        goal: LatLon,
        bbox: BBox,
        padMeters: Double = 400.0,
        targetCellMeters: Double = 200.0
    ): List<LatLon>? {
        Log.i(TAG, "SeaRouter.route: WKT bytes=${landWkt.length}")

        // 1) Config griglia + LandMask dal WKT
        val cfg  = GridConfig.build(bbox, padMeters, targetCellMeters)
        val land = LandMaskBuilder.fromWkt(landWkt, cfg)

        // 2) Snap a mare se i punti cadono su terra
        val sSea = if (land.isLand(start)) SnapToSea.snap(start, land) else start
        val gSea = if (land.isLand(goal))  SnapToSea.snap(goal,  land) else goal

        // 3) A* su griglia acqua
        val rawPath = AStar.route(land, sSea, gSea)
        if (rawPath == null) {
            Log.w(TAG, "A* non ha trovato un percorso")
            return null
        }

        // 4) (opzionale) Densificazione post-process (se hai un PathPost; altrimenti restituisci rawPath)
        // return PathPost.densify(rawPath, maxSpacingMeters = targetCellMeters / 2)
        return rawPath
    }

    companion object { private const val TAG = "SeaRouter" }
}
