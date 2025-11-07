package com.perseitech.sailpilot.routing


import java.util.PriorityQueue
import kotlin.math.sqrt


private data class Node(val r:Int,val c:Int)


private data class QN(val f:Double,val g:Double,val n:Node): Comparable<QN> {
    override fun compareTo(other: QN): Int = f.compareTo(other.f)
}


object AStarRouter {
    private val N8 = arrayOf(
        intArrayOf(1,0), intArrayOf(-1,0), intArrayOf(0,1), intArrayOf(0,-1),
        intArrayOf(1,1), intArrayOf(1,-1), intArrayOf(-1,1), intArrayOf(-1,-1)
    )


    fun route(land: LandMask, start: LatLon, goal: LatLon): List<LatLon>? {
        val (rs, cs) = land.cfg.latLonToRC(start)
        val (rg, cg) = land.cfg.latLonToRC(goal)
        if (land.isLand(rs, cs) || land.isLand(rg, cg)) return null
        val rows = land.cfg.rows; val cols = land.cfg.cols
        val open = PriorityQueue<QN>()
        val g = Array(rows) { DoubleArray(cols) { Double.POSITIVE_INFINITY } }
        val cameR = Array(rows) { IntArray(cols) { -1 } }
        val cameC = Array(rows) { IntArray(cols) { -1 } }
        fun h(r:Int,c:Int): Double {
            val a = land.cfg.rcToLatLon(r,c)
            val b = land.cfg.rcToLatLon(rg,cg)
            return GeoUtils.haversineMeters(a,b)
        }
        fun push(r:Int,c:Int,ng:Double){
            val f = ng + h(r,c)
            open.add(QN(f, ng, Node(r,c)))
        }
        g[rs][cs] = 0.0
        push(rs,cs,0.0)
        var expanded = 0
        while (open.isNotEmpty()) {
            val cur = open.poll(); val r = cur.n.r; val c = cur.n.c
            if (r == rg && c == cg) break
            if (cur.g > g[r][c]) continue
            expanded++
            for (d in N8) {
                val rr = r + d[0]; val cc = c + d[1]
                if (rr !in 0 until rows || cc !in 0 until cols) continue
                if (land.isLand(rr,cc)) continue
                val diag = (d[0] != 0 && d[1] != 0)
                val stepCost = landStepCostMeters(land, r,c, rr,cc, diag)
                val ng = cur.g + stepCost
                if (ng < g[rr][cc]) {
                    g[rr][cc] = ng
                    cameR[rr][cc] = r; cameC[rr][cc] = c
                    push(rr,cc,ng)
                }
            }
        }
        if (g[rg][cg].isInfinite()) return null
// ricostruisci
        val pathRC = ArrayList<Node>()
        var cr = rg; var cc = cg
        while (cr != -1 && cc != -1) { pathRC.add(Node(cr,cc)); val pr = cameR[cr][cc]; val pc = cameC[cr][cc]; cr = pr; cc = pc }
        pathRC.reverse()
        return pathRC.map { land.cfg.rcToLatLon(it.r, it.c) }
    }


    private fun landStepCostMeters(land: LandMask, r:Int,c:Int, rr:Int,cc:Int, diag:Boolean): Double {
        val a = land.cfg.rcToLatLon(r,c)
        val b = land.cfg.rcToLatLon(rr,cc)
        val d = GeoUtils.haversineMeters(a,b)
        return if (diag) d * 1.0 else d * 1.0 // already true distance; keep 1:1
    }
}