package com.perseitech.sailpilot.data

import android.content.Context
import kotlin.math.*

// ------------------------
// Modello di coordinate
// ------------------------
data class LatLon(val lat: Double, val lon: Double)

// ------------------------
// Utility geografiche + WKT
// ------------------------
object Geo {
    // --- distanza/bearing: invariati ---
    private const val R_EARTH_M = 6371000.0
    private const val M_TO_NM = 0.0005399568

    fun distanceNm(a: LatLon, b: LatLon): Double {
        val φ1 = Math.toRadians(a.lat); val φ2 = Math.toRadians(b.lat)
        val Δφ = Math.toRadians(b.lat - a.lat)
        val Δλ = Math.toRadians(b.lon - a.lon)
        val sinDφ = sin(Δφ / 2); val sinDλ = sin(Δλ / 2)
        val h = sinDφ * sinDφ + cos(φ1) * cos(φ2) * sinDλ * sinDλ
        val d = 2 * atan2(sqrt(h), sqrt(1 - h))
        return (R_EARTH_M * d) * M_TO_NM
    }

    fun bearingDeg(a: LatLon, b: LatLon): Double {
        val φ1 = Math.toRadians(a.lat); val φ2 = Math.toRadians(b.lat)
        val Δλ = Math.toRadians(b.lon - a.lon)
        val y = sin(Δλ) * cos(φ2)
        val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(Δλ)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    // --- WKT: lettura e parsing (senza librerie esterne) ---

    /** Legge un file WKT dagli assets e torna il testo. */
    fun loadWktFromAssets(context: Context, assetName: String): String =
        context.assets.open(assetName).bufferedReader().use { it.readText() }

    /**
     * Converte il testo WKT in una lista di "paths".
     * Ogni path è una lista di LatLon (lat, lon) già nell'ordine corretto.
     *
     * Supportati: MULTIPOLYGON, POLYGON, MULTILINESTRING, LINESTRING.
     * Per i poligoni viene usato solo l’anello esterno.
     */
    fun parseWktToPaths(wktRaw: String): List<List<LatLon>> {
        val wkt = wktRaw.trim()

        return when {
            wkt.startsWith("MULTIPOLYGON", ignoreCase = true) ->
                parseMultiPolygon(wkt)
            wkt.startsWith("POLYGON", ignoreCase = true) ->
                parsePolygon(wkt)
            wkt.startsWith("MULTILINESTRING", ignoreCase = true) ->
                parseMultiLineString(wkt)
            wkt.startsWith("LINESTRING", ignoreCase = true) ->
                listOf(parseLineStringBody(extractFirstParentheses(wkt)))
            else -> emptyList()
        }
    }

    // ---- PARSER HELPERS ----

    private fun parseMultiPolygon(wkt: String): List<List<LatLon>> {
        // cattura ogni (( ... )) (un poligono alla volta)
        val polyRegex = Regex("""\(\(\s*(.*?)\s*\)\)""", RegexOption.DOT_MATCHES_ALL)
        val out = mutableListOf<List<LatLon>>()

        polyRegex.findAll(wkt).forEach { m ->
            val polyText = m.groupValues[1] // può contenere shell e holes: "(x y, ...), (x y, ...)"
            // prendi solo l’anello esterno (prima sequenza prima di "),(")
            val shell = polyText.split("),").first().removeSurrounding("(", ")")
            out += parseCoordsList(shell)
        }
        return out
    }

    private fun parsePolygon(wkt: String): List<List<LatLon>> {
        // prende il contenuto tra (( ... ))
        val inner = extractDoubleParentheses(wkt) ?: return emptyList()
        val shell = inner.split("),").first().removeSurrounding("(", ")")
        return listOf(parseCoordsList(shell))
    }

    private fun parseMultiLineString(wkt: String): List<List<LatLon>> {
        // cattura ogni ( ... ) (una lineString alla volta)
        val lineRegex = Regex("""\(\s*([^()]+?)\s*\)""")
        val out = mutableListOf<List<LatLon>>()
        lineRegex.findAll(wkt).forEach { m ->
            out += parseCoordsList(m.groupValues[1])
        }
        return out
    }

    private fun parseLineStringBody(body: String): List<LatLon> =
        parseCoordsList(body)

    /** Converte "lon lat, lon lat, ..." in List<LatLon(lat, lon)>. */
    private fun parseCoordsList(s: String): List<LatLon> {
        return s.split(',')
            .mapNotNull { token ->
                val parts = token.trim().split(Regex("\\s+"))
                if (parts.size >= 2) {
                    val lon = parts[0].toDoubleOrNull()
                    val lat = parts[1].toDoubleOrNull()
                    if (lat != null && lon != null) LatLon(lat, lon) else null
                } else null
            }
    }

    /** estrae il primo livello di parentesi: "KEYWORD ( ... )" → "..." */
    private fun extractFirstParentheses(wkt: String): String {
        val i = wkt.indexOf('(')
        val j = wkt.lastIndexOf(')')
        return if (i >= 0 && j > i) wkt.substring(i + 1, j) else ""
    }

    /** estrae il contenuto tra doppie parentesi (( ... )) se presente. */
    private fun extractDoubleParentheses(wkt: String): String? {
        val m = Regex("""\(\(\s*(.*?)\s*\)\)""", RegexOption.DOT_MATCHES_ALL).find(wkt)
        return m?.groupValues?.getOrNull(1)
    }
}
