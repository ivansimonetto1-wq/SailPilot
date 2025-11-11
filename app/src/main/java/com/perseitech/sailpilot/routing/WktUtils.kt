package com.perseitech.sailpilot.routing

/**
 * Parser WKT molto tollerante per MULTIPOLYGON.
 * Assume l’ordine standard WKT: "lon lat" (X Y). Nel modello usiamo LatLon(lat, lon),
 * quindi invertiamo quando costruiamo LatLon.
 *
 * Output: List<Poligono>, dove Poligono = List<Anello>, Anello = List<LatLon>.
 * L’anello [0] è il contorno esterno, gli eventuali successivi sono buchi.
 */
object WktUtils {

    private fun clean(s: String): String =
        s.trim().replace("\n", " ").replace("\r", " ")

    private fun toDoubleSafe(s: String): Double =
        s.trim().trim('(', ')').toDouble()

    /**
     * Esempi accettati:
     * MULTIPOLYGON (((lon lat, lon lat, ...)), ((...))), ((...))
     */
    fun parseMultiPolygon(wkt: String): List<List<List<LatLon>>> {
        val src = clean(wkt)
        require(src.startsWith("MULTIPOLYGON", ignoreCase = true)) {
            "Solo MULTIPOLYGON supportato"
        }

        // Ritaglia solo il corpo dopo 'MULTIPOLYGON'
        // e rimuove la prima e l'ultima parentesi tonde principali.
        val body = src.substringAfter("MULTIPOLYGON", "").trim()
        val trimmed = body.dropWhile { it != '(' }.trim()
        val core = trimmed.trimStart('(').trimEnd(')') // ((...)),((...))

        // Split dei poligoni: separatore = ")), ((" a qualsiasi quantità di spazi
        val polygonChunks = core.split(Regex("\\)\\)\\s*,\\s*\\(\\("))

        val polygons = ArrayList<List<List<LatLon>>>()
        for (polyChunk in polygonChunks) {
            // Ogni poligono ha uno o più anelli: " ( ... ) , ( ... ) "
            val ringsCore = polyChunk.trim().trimStart('(').trimEnd(')') // (...),(...)
            val ringChunks = ringsCore.split(Regex("\\)\\s*,\\s*\\("))
            val rings = ArrayList<List<LatLon>>()

            for (ringChunk in ringChunks) {
                // Punti separati da virgola
                val pointTokens = ringChunk.split(Regex("\\s*,\\s*"))
                val pts = ArrayList<LatLon>(pointTokens.size)
                for (ptTok in pointTokens) {
                    val pieces = ptTok.trim().trim('(', ')').split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                    if (pieces.size < 2) continue
                    val lon = toDoubleSafe(pieces[0])
                    val lat = toDoubleSafe(pieces[1])
                    pts.add(LatLon(lat, lon)) // invertiamo -> LatLon(lat, lon)
                }
                if (pts.isNotEmpty()) rings.add(pts)
            }
            if (rings.isNotEmpty()) polygons.add(rings)
        }
        return polygons
    }
}
