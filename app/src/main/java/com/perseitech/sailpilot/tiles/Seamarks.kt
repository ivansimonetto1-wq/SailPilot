package com.perseitech.sailpilot.tiles

import android.content.Context
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.views.overlay.TilesOverlay

/**
 * Overlay trasparente OpenSeaMap (segnalamenti + depth contours).
 * Da usare sopra una base map (OSM o OpenSeaMap base).
 *
 * Credits:
 *  © OpenSeaMap contributors, © OpenStreetMap contributors
 *
 * NOTA IMPORTANTE: con osmdroid si passa SOLO la base URL con '/' finale.
 * Osmdroid costruisce automaticamente "/{z}/{x}/{y}.png".
 */
object Seamarks {

    private const val BASE_URL = "https://tiles.openseamap.org/seamark/"

    fun buildOverlay(
        context: Context,
        minZoom: Int = 0,
        maxZoom: Int = 19,
        tileSize: Int = 256
    ): TilesOverlay {
        // NIENTE template {z}/{x}/{y}.png qui: lo aggiunge osmdroid.
        val src: OnlineTileSourceBase = XYTileSource(
            "OPENSEAMAP_SEAMARK",
            minZoom, maxZoom, tileSize, ".png",
            arrayOf(BASE_URL)
        )
        val provider = MapTileProviderBasic(context).apply {
            tileSource = src
        }
        return TilesOverlay(provider, context).apply {
            // PNG già trasparenti
            loadingBackgroundColor = 0x00000000
            setLoadingLineColor(0x00000000)
            setUseDataConnection(true)
        }
    }
}
