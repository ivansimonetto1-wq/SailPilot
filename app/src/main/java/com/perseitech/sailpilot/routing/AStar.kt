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
 * Richiede:
 *  - LandMask con:
 *      fun isLand(row: Int, col: Int): Boolean
 *      val cfg.rows/cols: Int
 *      fun cfg.latLonToRC(p: LatLon): Pair<Int,Int>?
 *      fun cfg.rcToLatLon(r: Int, c: Int): LatLon
 *  - GeoUtils.distanceMeters(a, b): Double
 */
object AStar {

    // 8-neighborhood
    private val N8 = arrayOf(
        intArrayOf( 1,  0), intArrayOf(-1,  0), intArrayOf( 0,  1), intArrayOf( 0, -1),
        intArrayOf( 1,  1), intArrayOf( 1, -1), intArrayOf(-1,  1), intArrayOf(-1, -1)
    )

    /**
     * Calcola il percorso evitando la terraferma. Ritorna null se non esiste.
     */
    fun route(land: LandMask, start: LatLon, goal: LatLon): List<LatLon>? {
        val rcS = land.cfg.latLonToRC(start) ?: return null
        val rcG = land.cfg.latLonToRC(goal)  ?: return null
        val (rs, cs) = rcS
        val (rg, cg) = rcG

        // Start/goal devono cadere su mare
        if (land.isLand(rs, cs) || land.isLand(rg, cg)) return null

        val rows = land.cfg.rows
        val cols = land.cfg.cols

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
            val cur = open.poll()
            val r = cur.n.r
            val c = cur.n.c

            if (r == rg && c == cg) break
            if (cur.g > gScore[r][c]) continue  // stale

            for (d in N8) {
                val rr = r + d[0]
                val cc = c + d[1]
                if (rr !in 0 until rows || cc !in 0 until cols) continue
                if (land.isLand(rr, cc)) continue  // target cell must be sea

                // Evita “corner cutting” su diagonali:
                val diag = d[0] != 0 && d[1] != 0
                if (diag) {
                    val rSide = r + d[0]
                    val cSide = c + d[1]
                    // se una delle adiacenti ortogonali è terra, vieta il diagonale
                    if (land.isLand(r, cSide) || land.isLand(rSide, c)) continue
                }

                val a = land.cfg.rcToLatLon(r, c)
                val b = land.cfg.rcToLatLon(rr, cc)
                val step = GeoUtils.distanceMeters(a, b) // costo = distanza reale

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

        // ricostruzione path (da goal a start)
        val rcPath = ArrayList<Node>()
        var cr = rg
        var cc = cg
        while (cr != -1 && cc != -1) {
            rcPath.add(Node(cr, cc))
            val pr = cameR[cr][cc]
            val pc = cameC[cr][cc]
            cr = pr
            cc = pc
        }
        rcPath.reverse()

        return rcPath.map { land.cfg.rcToLatLon(it.r, it.c) }
    }
}
