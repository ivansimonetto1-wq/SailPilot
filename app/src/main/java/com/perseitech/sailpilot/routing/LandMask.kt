package com.perseitech.sailpilot.routing

/**
 * Raster di terra/ mare su griglia definita da GridConfig.
 * cells[ind] = true  -> TERRA
 * cells[ind] = false -> MARE
 */
class LandMask(
    val cfg: GridConfig,
    private val cells: BooleanArray
) {
    val rows: Int get() = cfg.rows
    val cols: Int get() = cfg.cols

    /** Indice lineare r,c -> idx nell’array cells */
    private fun idx(r: Int, c: Int): Int = r * cols + c

    /** true se la cella è terra (OOB = terra per sicurezza) */
    fun isLand(r: Int, c: Int): Boolean {
        if (r !in 0 until rows || c !in 0 until cols) return true
        return cells[idx(r, c)]
    }

    /** test puntuale con conversione lat/lon -> r,c */
    fun isLand(lat: Double, lon: Double): Boolean {
        val rc = cfg.latLonToRC(LatLon(lat, lon)) ?: return true
        return isLand(rc.first, rc.second)
    }

    fun isLand(p: LatLon): Boolean = isLand(p.lat, p.lon)
    fun isSea(r: Int, c: Int): Boolean = !isLand(r, c)
    fun isSea(p: LatLon): Boolean = !isLand(p)

    /**
     * Ritorna una NUOVA LandMask con la terra “dilatata” di [radiusCells] celle (Chebyshev).
     * radiusCells = 1 crea una fascia di rispetto ≈ size_cella attorno alle coste.
     */
    fun dilated(radiusCells: Int): LandMask {
        if (radiusCells <= 0) return this
        val out = BooleanArray(rows * cols)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                var isLandHere = false
                val r0 = r - radiusCells
                val r1 = r + radiusCells
                val c0 = c - radiusCells
                val c1 = c + radiusCells
                var rr = r0
                while (rr <= r1 && !isLandHere) {
                    var cc = c0
                    while (cc <= c1) {
                        if (isLand(rr, cc)) { // OOB -> terra
                            isLandHere = true
                            break
                        }
                        cc++
                    }
                    rr++
                }
                out[idx(r, c)] = isLandHere
            }
        }
        return LandMask(cfg, out)
    }

    /**
     * Distanza (in CELLE, metrica Chebyshev 8-neighbor) alla costa.
     * Celle terra hanno distanza 0. Celle OOB sono trattate come terra (quindi vicino ai bordi la distanza tende a 0).
     *
     * Restituisce un IntArray rows*cols con distanze >=0.
     */
    fun distanceToLandCells(): IntArray {
        val INF = Int.MAX_VALUE / 4
        val dist = IntArray(rows * cols) { INF }

        fun push(r: Int, c: Int, d: Int, q: ArrayDeque<Pair<Int, Int>>) {
            if (r !in 0 until rows || c !in 0 until cols) return
            val i = idx(r, c)
            if (d < dist[i]) {
                dist[i] = d
                q.addLast(r to c)
            }
        }

        val q: ArrayDeque<Pair<Int, Int>> = ArrayDeque()

        // seed: tutte le celle terra a distanza 0
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                if (isLand(r, c)) {
                    val i = idx(r, c)
                    dist[i] = 0
                    q.addLast(r to c)
                }
            }
        }

        // BFS 8-neighbor
        val dirs = arrayOf(
            intArrayOf( 1,  0), intArrayOf(-1,  0), intArrayOf( 0,  1), intArrayOf( 0, -1),
            intArrayOf( 1,  1), intArrayOf( 1, -1), intArrayOf(-1,  1), intArrayOf(-1, -1)
        )

        while (q.isNotEmpty()) {
            val (r, c) = q.removeFirst()
            val base = dist[idx(r, c)]
            val nd = base + 1
            dirs.forEach { d ->
                push(r + d[0], c + d[1], nd, q)
            }
        }
        return dist
    }
}
