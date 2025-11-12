package com.perseitech.sailpilot.export

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.perseitech.sailpilot.routing.LatLon
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object GpxExporter {

    /**
     * Esporta la polilinea come GPX 1.1 in Download.
     * @return Uri del file creato, oppure null se fallisce.
     */
    fun exportToDownloads(context: Context, namePrefix: String, path: List<LatLon>): Uri? {
        if (path.size < 2) return null

        val time = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "${namePrefix}-${time}.gpx"

        val resolver = context.contentResolver
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/gpx+xml")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/")
            }
        }

        val itemUri = resolver.insert(collection, values) ?: return null

        try {
            resolver.openOutputStream(itemUri)?.use { out ->
                val sb = StringBuilder()
                sb.append("""<?xml version="1.0" encoding="UTF-8"?>""").append('\n')
                sb.append("""<gpx version="1.1" creator="SailPilot" xmlns="http://www.topografix.com/GPX/1/1">""").append('\n')
                sb.append("  <trk>\n")
                sb.append("    <name>${fileName}</name>\n")
                sb.append("    <trkseg>\n")
                for (p in path) {
                    sb.append(String.format(Locale.US, "      <trkpt lat=\"%.8f\" lon=\"%.8f\"/>%n", p.lat, p.lon))
                }
                sb.append("    </trkseg>\n")
                sb.append("  </trk>\n")
                sb.append("</gpx>\n")
                out.write(sb.toString().toByteArray(Charsets.UTF_8))
            }
            return itemUri
        } catch (e: IOException) {
            // best-effort cleanup
            runCatching { resolver.delete(itemUri, null, null) }
            return null
        }
    }
}
