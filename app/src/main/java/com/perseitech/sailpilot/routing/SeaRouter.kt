package com.perseitech.sailpilot.routing

import android.util.Log

class SeaRouter {

    fun route(
        landWkt: String,
        start: LatLon,
        goal: LatLon,
        bbox: BBox,
        padMeters: Double = 400.0,
        targetCellMeters: Double = 200.0,
        maxSegmentMeters: Double = 150.0
    ): List<LatLon>? {
        val cfg = GridConfig.build(bbox, padMeters, targetCellMeters)
        val mask = LandMaskBuilder.fromWkt(landWkt, cfg)

        // vincolo: partenza/arrivo devono stare in mare → snap se servono
        val s = if (mask.isSea(start)) start else SnapToSea.snap(start, mask) ?: return null
        val g = if (mask.isSea(goal)) goal else SnapToSea.snap(goal, mask) ?: return null

        val raw = AStar.route(mask, s, g) ?: return null
        val nice = PathPost.densify(raw, maxSegmentMeters)

        Log.d("SeaRouter", "A* path pts=${raw.size} (out=${nice.size}) rows=${cfg.rows} cols=${cfg.cols}")
        return nice
    }

    /** Espone anche solo il test mare/terra per i long-tap. */
    fun isSea(landWkt: String, bbox: BBox, padMeters: Double, targetCellMeters: Double, p: LatLon): Boolean {
        val cfg = GridConfig.build(bbox, padMeters, targetCellMeters)
        val mask = LandMaskBuilder.fromWkt(landWkt, cfg)
        return mask.isSea(p)
    }
}
