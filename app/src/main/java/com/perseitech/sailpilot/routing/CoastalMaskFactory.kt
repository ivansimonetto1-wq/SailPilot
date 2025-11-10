package com.perseitech.sailpilot.routing

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

/**
 * Costruisce una LandMask partendo dal WKT in assets e da una bbox
 * calcolata attorno a start/goal, con padding e risoluzione configurabili.
 *
 * Dipendenze attese:
 *  - data class BBox(minLat, minLon, maxLat, maxLon) { val center: LatLon ... }
 *  - object GeoUtils { metersToDegLat(), metersToDegLon() }
 *  - data class LatLon(lat, lon)
 *  - data class GridConfig + companion fun build(raw: BBox, padMeters: Double, targetCellMeters: Double): GridConfig
 *  - object LandMaskBuilder { fun fromWkt(landWkt: String, cfg: GridConfig): LandMask }
 *  - class LandMask(cfg: GridConfig, ...)
 */
object CoastalMaskFactory {

    fun buildFor(
        context: Context,
        start: LatLon,
        goal: LatLon,
        padMeters: Double = 2_000.0,      // padding geografico per la bbox attorno ai punti
        gridPadMeters: Double = 600.0,    // pad interno usato da GridConfig.build
        targetCellMeters: Double = 200.0  // risoluzione griglia
    ): LandMask {
        val wkt = loadWktFromAssets(context, "coast.wkt")

        // bbox grezza attorno a start/goal
        val minLat = min(start.lat, goal.lat)
        val maxLat = max(start.lat, goal.lat)
        val minLon = min(start.lon, goal.lon)
        val maxLon = max(start.lon, goal.lon)
        val raw = BBox(minLat = minLat, minLon = minLon, maxLat = maxLat, maxLon = maxLon)

        // padding in gradi
        val latPadDeg = GeoUtils.metersToDegLat(padMeters)
        val lonPadDeg = GeoUtils.metersToDegLon(padMeters, raw.center.lat)
        val padded = BBox(
            minLat = raw.minLat - latPadDeg,
            minLon = raw.minLon - lonPadDeg,
            maxLat = raw.maxLat + latPadDeg,
            maxLon = raw.maxLon + lonPadDeg
        )

        // Costruzione della griglia (firma attesa: GridConfig.build(raw, padMeters, targetCellMeters))
        val cfg = GridConfig.build(
            raw = padded,
            padMeters = gridPadMeters,
            targetCellMeters = targetCellMeters
        )

        // Firma reale di LandMaskBuilder: fromWkt(landWkt: String, cfg: GridConfig)
        return LandMaskBuilder.fromWkt(
            landWkt = wkt,
            cfg = cfg
        )
    }

    private fun loadWktFromAssets(context: Context, assetName: String): String {
        context.assets.open(assetName).use { ins ->
            BufferedReader(InputStreamReader(ins)).use { br ->
                val sb = StringBuilder()
                var line: String?
                while (true) {
                    line = br.readLine() ?: break
                    sb.append(line).append('\n')
                }
                return sb.toString()
            }
        }
    }
}
