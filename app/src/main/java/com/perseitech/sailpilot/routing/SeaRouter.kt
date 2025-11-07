package com.perseitech.sailpilot.routing


import android.util.Log


object SeaRouter {
    private const val TAG = "SeaRouter"


    fun route(
        landWkt: String,
        start: LatLon,
        goal: LatLon,
        bbox: BBox,
        padMeters: Double = 400.0,
        targetCellMeters: Double = 200.0,
    ): List<LatLon>? {
        Log.i(TAG, "WKT bytes=${landWkt.length}")


// 1) WKT -> geometria
        val prepared = WktLandLoader.loadAndPrepare(landWkt)
        Log.i(TAG, "WKT parsed: type=${prepared.geom.geometryType}, empty=${prepared.geom.isEmpty}")


// 2) Griglia
        val cfg = GridConfig(bbox, targetCellMeters)
        Log.d(TAG, "Grid rows=${cfg.rows} cols=${cfg.cols} step=~${targetCellMeters}m latMid=${cfg.latMid}")


// 3) Rasterizzazione terra
        var land = LandMaskBuilder.rasterize(prepared, cfg)


// 4) Pad morfologico
        land = LandMaskBuilder.dilate(land, padMeters)
        Log.d(TAG, "Mask padded by ${padMeters}m")


// 5) Snap to sea (se necessario)
        val s = SnapToSea.snap(start, land)
        val g = SnapToSea.snap(goal, land)
        Log.d(TAG, "Snap S=(${s.lat},${s.lon}) G=(${g.lat},${g.lon})")


// 6) A*
        val coarse = AStarRouter.route(land, s, g)
        if (coarse == null) {
            Log.w(TAG, "A* failed: no path. Returning null (no straight fallback).")
            return null
        }
        Log.d(TAG, "A* OK -> pts=${coarse.size}")


// 7) Densify + collision check finale
        val cellMetersLat = GeoUtils.metersPerDegLat() * cfg.cellDegLat
        val cellMetersLon = GeoUtils.metersPerDegLon(cfg.latMid) * cfg.cellDegLon
        val cellMeters = kotlin.math.min(cellMetersLat, cellMetersLon)
        val dense = PathPost.densify(coarse, cellMeters * 0.8)
        val hit = PathPost.firstCollision(dense, land)
        if (hit != null) {
            Log.e(TAG, "Collision after A*: lat=${hit.lat} lon=${hit.lon}")
            return null
        }


        return dense
    }
}