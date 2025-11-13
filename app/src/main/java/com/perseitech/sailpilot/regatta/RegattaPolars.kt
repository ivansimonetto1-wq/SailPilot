package com.perseitech.sailpilot.regatta

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Gestione polari regata.
 *
 * - Se trova un file JSON in assets/polars/<classe>.json lo usa per interpolare l'upwind TWA.
 * - Altrimenti usa una curva "piecewise" di default per la classe.
 *
 * Formato JSON atteso (esempio):
 * {
 *   "upwind": [
 *     {"tws": 6,  "twa": 43.0},
 *     {"tws": 10, "twa": 41.0},
 *     {"tws": 16, "twa": 39.0}
 *   ]
 * }
 */
object RegattaPolars {

    enum class ClassId(val label: String) {
        GENERIC_MONOHULL("Generic Monohull"),
        RACER_CRUISER("Racer/Cruiser"),
        SPORTBOAT("Sportboat"),
        DINGHY("Dinghy"),
        CATAMARAN("Catamaran"),
        FOILER("Foiler")
    }

    private data class UpwindPoint(val tws: Double, val twa: Double)

    private val cache = mutableMapOf<ClassId, List<UpwindPoint>?>()

    /**
     * Versione "completa": se ho il Context, provo a leggere le polari reali da assets.
     */
    fun tackAngleDeg(context: Context?, classId: ClassId, twsKn: Double?): Double {
        if (twsKn == null) return default(classId)
        val ctx = context
        val polar = if (ctx != null) loadUpwindPolars(ctx, classId) else null
        if (polar.isNullOrEmpty()) {
            return fallbackPiecewise(classId, twsKn)
        }
        val twa = interpolateUpwindTwa(polar, twsKn)
        return twa * 2.0
    }

    /**
     * API semplice: niente Context, usa solo la curva piecewise di default.
     */
    fun tackAngleDeg(classId: ClassId, twsKn: Double?): Double {
        if (twsKn == null) return default(classId)
        return fallbackPiecewise(classId, twsKn)
    }

    // ----- lettura polari da assets -----

    private fun loadUpwindPolars(context: Context, classId: ClassId): List<UpwindPoint>? {
        cache[classId]?.let { return it }

        val assetName = when (classId) {
            ClassId.GENERIC_MONOHULL -> "generic_monohull.json"
            ClassId.RACER_CRUISER    -> "racer_cruiser.json"
            ClassId.SPORTBOAT        -> "sportboat.json"
            ClassId.DINGHY           -> "dinghy.json"
            ClassId.CATAMARAN        -> "catamaran.json"
            ClassId.FOILER           -> "foiler.json"
        }

        return try {
            context.assets.open("polars/$assetName").bufferedReader().use { br ->
                val txt = br.readText()
                val root = JSONObject(txt)
                val arr: JSONArray = root.optJSONArray("upwind") ?: return null
                val list = mutableListOf<UpwindPoint>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val tws = o.optDouble("tws", Double.NaN)
                    val twa = o.optDouble("twa", Double.NaN)
                    if (!tws.isNaN() && !twa.isNaN()) {
                        list.add(UpwindPoint(tws, twa))
                    }
                }
                val sorted = list.sortedBy { it.tws }
                cache[classId] = sorted
                sorted
            }
        } catch (_: Exception) {
            cache[classId] = null
            null
        }
    }

    private fun interpolateUpwindTwa(points: List<UpwindPoint>, tws: Double): Double {
        if (points.isEmpty()) return 45.0
        if (tws <= points.first().tws) return points.first().twa
        if (tws >= points.last().tws) return points.last().twa
        for (i in 0 until points.size - 1) {
            val a = points[i]
            val b = points[i + 1]
            if (tws >= a.tws && tws <= b.tws) {
                val t = (tws - a.tws) / (b.tws - a.tws)
                return a.twa + t * (b.twa - a.twa)
            }
        }
        return points.last().twa
    }

    // ----- fallback piecewise se non ci sono polari reali -----

    private fun fallbackPiecewise(classId: ClassId, t: Double): Double {
        return when (classId) {
            ClassId.GENERIC_MONOHULL -> when {
                t < 6   -> 95.0
                t < 10  -> 92.0
                t < 16  -> 88.0
                else    -> 84.0
            }
            ClassId.RACER_CRUISER -> when {
                t < 6   -> 92.0
                t < 10  -> 90.0
                t < 16  -> 86.0
                else    -> 82.0
            }
            ClassId.SPORTBOAT -> when {
                t < 6   -> 90.0
                t < 10  -> 88.0
                t < 16  -> 84.0
                else    -> 80.0
            }
            ClassId.DINGHY -> when {
                t < 6   -> 92.0
                t < 10  -> 88.0
                t < 16  -> 84.0
                else    -> 80.0
            }
            ClassId.CATAMARAN -> when {
                t < 6   -> 88.0
                t < 10  -> 84.0
                t < 16  -> 80.0
                else    -> 76.0
            }
            ClassId.FOILER -> when {
                t < 8   -> 84.0
                t < 12  -> 80.0
                t < 18  -> 76.0
                else    -> 72.0
            }
        }
    }

    private fun default(classId: ClassId) = when (classId) {
        ClassId.GENERIC_MONOHULL -> 90.0
        ClassId.RACER_CRUISER    -> 88.0
        ClassId.SPORTBOAT        -> 86.0
        ClassId.DINGHY           -> 88.0
        ClassId.CATAMARAN        -> 82.0
        ClassId.FOILER           -> 78.0
    }
}
