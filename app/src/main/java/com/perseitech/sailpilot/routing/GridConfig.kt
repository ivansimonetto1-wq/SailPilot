package com.perseitech.sailpilot.routing

import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.PI

/**
 * Configurazione della griglia regolare su cui rasterizziamo la costa.
 *
 * - rows/cols: numero di righe/colonne
 * - stepLatDeg/stepLonDeg: passo in gradi
 * - bbox: bounding box *espansa* (se serve) entro cui vale la griglia
 */
data class GridConfig(
    val bbox: BBox,
    val rows: Int,
    val cols: Int,
    val stepLatDeg: Double,
    val stepLonDeg: Double
) {
    companion object {

        /**
         * Crea una griglia a partire da una BBox logica, un margine [padMeters] e una
         * dimensione cella target [targetCellMeters] (in metri).
         *
         * Lateralmente usa la conversione metri↔gradi in base alla latitudine del centro bbox.
         * Garantisce almeno 2x2 celle.
         */
        fun fromBBox(
            bbox: BBox,
            padMeters: Double,
            targetCellMeters: Double
        ): GridConfig {
            // conversione metri -> gradi (approssimata ma adeguata per scale locali)
            val lat0 = bbox.center.lat.coerceIn(-89.0, 89.0)
            val metersPerDegLat = 111_132.0                   // ~ media
            val metersPerDegLon = 111_320.0 * cos(lat0 * PI / 180.0)

            val padLatDeg = (padMeters / metersPerDegLat).coerceAtLeast(0.0)
            val padLonDeg = (padMeters / metersPerDegLon).coerceAtLeast(0.0)

            val minLat = bbox.minLat - padLatDeg
            val maxLat = bbox.maxLat + padLatDeg
            val minLon = bbox.minLon - padLonDeg
            val maxLon = bbox.maxLon + padLonDeg
            val ebbox = BBox(minLat, minLon, maxLat, maxLon)

            val heightMeters = (maxLat - minLat).coerceAtLeast(0.0) * metersPerDegLat
            val widthMeters  = (maxLon - minLon).coerceAtLeast(0.0) * metersPerDegLon

            // almeno 2x2 celle; evitiamo divisioni per zero
            val tcm = max(targetCellMeters, 1.0)

            val rows = max(2, ceil(heightMeters / tcm).toInt())
            val cols = max(2, ceil(widthMeters  / tcm).toInt())

            val stepLatDeg = (maxLat - minLat) / rows
            val stepLonDeg = (maxLon - minLon) / cols

            return GridConfig(
                bbox = ebbox,
                rows = rows,
                cols = cols,
                stepLatDeg = stepLatDeg,
                stepLonDeg = stepLonDeg
            )
        }
    }

    /** Latitudine all’inizio della riga (bordo superiore della cella). */
    fun latAt(row: Int): Double = bbox.minLat + row * stepLatDeg

    /** Longitudine all’inizio della colonna (bordo sinistro della cella). */
    fun lonAt(col: Int): Double = bbox.minLon + col * stepLonDeg

    /** Converte lat/lon in (riga, colonna) della cella contenente il punto. */
    fun latLonToRC(p: LatLon): Pair<Int, Int>? {
        val r = floor((p.lat - bbox.minLat) / stepLatDeg).toInt()
        val c = floor((p.lon - bbox.minLon) / stepLonDeg).toInt()
        return if (r in 0 until rows && c in 0 until cols) r to c else null
    }

    /** Centro della cella (r,c) in coordinate geografiche. */
    fun rcToLatLon(r: Int, c: Int): LatLon {
        val lat = bbox.minLat + (r + 0.5) * stepLatDeg
        val lon = bbox.minLon + (c + 0.5) * stepLonDeg
        return LatLon(lat, lon)
    }
}
