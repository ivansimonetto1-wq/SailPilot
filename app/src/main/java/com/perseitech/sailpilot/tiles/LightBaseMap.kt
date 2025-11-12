package com.perseitech.sailpilot.tiles

import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex

/**
 * Stamen Toner Lite - basemap leggera a basso dettaglio.
 * Attribuzione: © Stamen, © OpenStreetMap contributors.
 * Policy: https://stamen.com/terms/
 */
object LightBaseMap : OnlineTileSourceBase(
    "StamenTonerLite",
    0,          // min zoom
    20,         // max zoom
    256,        // tile size
    ".png",
    // più baseUrl per bilanciamento/affidabilità
    arrayOf(
        "https://stamen-tiles.a.ssl.fastly.net/toner-lite/",
        "https://stamen-tiles-b.a.ssl.fastly.net/toner-lite/",
        "https://stamen-tiles-c.a.ssl.fastly.net/toner-lite/",
        "https://stamen-tiles-d.a.ssl.fastly.net/toner-lite/"
    )
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${baseUrl}${z}/${x}/${y}${mImageFilenameEnding}"
    }
}

/** Util per confrontare nomi/tipi senza sbagliare. */
fun ITileSource.id(): String = name().lowercase()
