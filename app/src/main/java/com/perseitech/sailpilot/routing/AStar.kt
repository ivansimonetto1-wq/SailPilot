package com.perseitech.sailpilot.routing

import java.util.PriorityQueue

/** Indice di cella (riga, colonna). */
private data class Node(val r: Int, val c: Int)

/** Nodo in open set con (f=g+h). */
private data class QN(val f: Double, val g: Double, val n: Node) : Comparable<QN> {
    override fun compareTo(other: QN): Int = f.compareTo(other.f)
}

/**
 * A* su griglia: naviga SOLO su mare (celle non-terra).
 *
 * - Prima prova con LAND DILATATA di 1 cella (margine sicurezza). Se fallisce, fallback alla land originale.
 * - NO corner-cutting sulle diagonali.
 * - Leggero bias offshore: penalizza passi molto vicini alla costa.
 */
object AStar {

    // 8-neighborhood
    private val N8 = arrayOf(
        intArrayOf( 1,  0), intArrayOf(-1,  0), intArrayOf( 0,  1), intArrayOf( 0, -1),
        intArrayOf( 1,  1), intArrayOf( 1, -1), intArrayOf(-1,  1), intArrayOf(-1, -1)
    )

    // Bias offshore (in celle)
    private const val DIST_BIAS_CELLS = 3     // distanza dalla costa entro cui applico la penalità
    private const val BIAS_STRENGTH   = 0.22  // 0..~0.4 – più alto = più al largo

    /** API pubblica: prova con safety-mask, altrimenti fallback */
    fun route(land: LandMask, start: LatLon, goal: LatLon): List<LatLon>? {
        // 1) prova con maschera dilatata (1 cella)
        val safety = land.dilated(1)
        routeSingleMask(safety, start, goal)?.let { return it }
        // 2) fallback: maschera originale (per passaggi stretti)
        return routeSingleMask(land, start, goal)
    }

    /** A* su una singola mask. */
    private fun routeSingleMask(land: LandMask, start: LatLon, goal: LatLon): List<LatLon>? {
        val rcS = land.cfg.latLonToRC(start) ?: return null
        val rcG = land.cfg.latLonToRC(goal)  ?: return null
        val (rs, cs) = rcS
        val (rg, cg) = rcG

        if (land.isLand(rs, cs) || land.isLand(rg, cg)) return null

        val rows = land.cfg.rows
        val cols = land.cfg.cols

        // distanza (in celle) dalla costa: serve per il bias offshore
        val distToLand = land.distanceToLandCells()
        fun dCells(r: Int, c: Int): Int {
            val i = r * cols + c
            return if (i in distToLand.indices) distToLand[i] else 0
        }

        val open = PriorityQueue<QN>()
        val gScore = Array(rows) { DoubleArray(cols) { Double.POSITIVE_INFINITY } }
        val cameR  = Array(rows) { IntArray(cols) { -1 } }
        val cameC  = Array(rows) { IntArray(cols) { -1 } }

        fun h(r: Int, c: Int): Double {
            val a = land.cfg.rcToLatLon(r, c)
            val b = land.cfg.rcToLatLon(rg, cg)
            return GeoUtils.distanceMeters(a, b)
        }

        fun push(r: Int, c: Int, ng: Double) {
            open.add(QN(f = ng + h(r, c), g = ng, n = Node(r, c)))
        }

        gScore[rs][cs] = 0.0
        push(rs, cs, 0.0)

        while (open.isNotEmpty()) {
            val cur = open.poll() ?: break
            val r = cur.n.r
            val c = cur.n.c

            if (r == rg && c == cg) break
            if (cur.g > gScore[r][c]) continue  // stale

            for (d in N8) {
                val rr = r + d[0]
                val cc = c + d[1]
                if (rr !in 0 until rows || cc !in 0 until cols) continue
                if (land.isLand(rr, cc)) continue

                // Anti corner-cutting sulle diagonali
                val diag = d[0] != 0 && d[1] != 0
                if (diag) {
                    if (land.isLand(r, cc) || land.isLand(rr, c)) continue
                }

                val a = land.cfg.rcToLatLon(r, c)
                val b = land.cfg.rcToLatLon(rr, cc)
                var step = GeoUtils.distanceMeters(a, b) // costo base = distanza reale

                // Bias offshore: penalizza vicinanza costa
                if (BIAS_STRENGTH > 0.0) {
                    val dHere = dCells(rr, cc)
                    if (dHere in 1..DIST_BIAS_CELLS) {
                        val factor = 1.0 + BIAS_STRENGTH * (DIST_BIAS_CELLS - dHere + 1).toDouble() / DIST_BIAS_CELLS
                        step *= factor
                    }
                }

                val ng = cur.g + step
                if (ng < gScore[rr][cc]) {
                    gScore[rr][cc] = ng
                    cameR[rr][cc] = r
                    cameC[rr][cc] = c
                    push(rr, cc, ng)
                }
            }
        }

        if (gScore[rg][cg].isInfinite()) return null

        // ricostruzione
        val path = ArrayList<Node>()
        var cr = rg
        var cc = cg
        while (cr != -1 && cc != -1) {
            path.add(Node(cr, cc))
            val pr = cameR[cr][cc]
            val pc = cameC[cr][cc]
            cr = pr
            cc = pc
        }
        path.reverse()

        return path.map { land.cfg.rcToLatLon(it.r, it.c) }
    }
}
