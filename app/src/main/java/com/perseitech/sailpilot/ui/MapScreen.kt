package com.perseitech.sailpilot.ui

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.perseitech.sailpilot.PickMode
import com.perseitech.sailpilot.routing.LatLon
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.events.MapEventsReceiver

@Composable
fun MapScreen(
    start: LatLon?,
    goal: LatLon?,
    path: List<LatLon>,
    pickMode: PickMode,
    isSea: (LatLon) -> Boolean,
    onRequestPickStart: () -> Unit,
    onRequestGpsStart: () -> Unit,
    onClearRoute: () -> Unit,
    onPointPicked: (LatLon) -> Unit
) {
    val ctx = LocalContext.current

    // Stato interno del MapView per evitare ricreazioni
    var mapView by remember { mutableStateOf<MapView?>(null) }

    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    MapView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(6.0)
                        controller.setCenter(GeoPoint(41.9, 12.5)) // Italia approx

                        // Overlays base
                        overlays.add(ScaleBarOverlay(this))
                        overlays.add(CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), this).apply { enableCompass() })
                        overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })

                        // Overlay eventi (single tap / long press)
                        val eventsReceiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                                // no-op (potresti mettere selezione rapida qui)
                                return false
                            }
                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                if (p == null) return false
                                val picked = LatLon(p.latitude, p.longitude)
                                // Rispetta vincolo "solo mare"
                                if (!isSea(picked)) return true // consumiamo, ma ignoriamo punto
                                onPointPicked(picked)
                                return true
                            }
                        }
                        overlays.add(MapEventsOverlay(eventsReceiver))

                        // salva riferimento
                        mapView = this
                    }
                },
                update = { mv ->
                    // Pulizia marker/linea e ridisegno
                    // (manteniamo solo overlays fissi: scalebar/compass/rotation/events)
                    mv.overlays.removeAll { it is Marker || it is Polyline }

                    // Start marker
                    start?.let {
                        val m = Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Partenza"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mv.overlays.add(m)
                    }

                    // Goal marker
                    goal?.let {
                        val m = Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Arrivo"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        }
                        mv.overlays.add(m)
                    }

                    // Route polyline
                    if (path.size > 1) {
                        val line = Polyline().apply {
                            outlinePaint.color = Color.RED
                            outlinePaint.strokeWidth = 6f
                            setPoints(path.map { GeoPoint(it.lat, it.lon) })
                        }
                        mv.overlays.add(line)

                        // Fit solo quando esiste una rotta
                        val bb = org.osmdroid.util.BoundingBox.fromGeoPoints(path.map { GeoPoint(it.lat, it.lon) })
                        mv.zoomToBoundingBox(bb, true, 100)
                    }

                    mv.invalidate()
                }
            )

            // Pulsanti azione
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1) Icona freccia verso l'alto = seleziona partenza manuale
                FloatingActionButton(onClick = onRequestPickStart) {
                    Icon(imageVector = Icons.Filled.ArrowUpward, contentDescription = "Seleziona partenza (long-tap)")
                }
                // 2) Cestino = cancella rotta
                FloatingActionButton(onClick = onClearRoute) {
                    Icon(imageVector = Icons.Filled.Delete, contentDescription = "Cancella rotta")
                }
                // 3) GPS = usa posizione dispositivo come partenza
                FloatingActionButton(onClick = onRequestGpsStart) {
                    Icon(imageVector = Icons.Filled.MyLocation, contentDescription = "Partenza da GPS")
                }
            }

            // Piccolo hint di modalità
            if (pickMode != PickMode.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                ) {
                    val msg = when (pickMode) {
                        PickMode.PICK_START -> "Long-tap sulla mappa per impostare la PARTENZA (solo mare)"
                        PickMode.PICK_GOAL  -> "Long-tap sulla mappa per impostare l'ARRIVO (solo mare)"
                        else -> ""
                    }
                    if (msg.isNotEmpty()) {
                        Surface(shadowElevation = 4.dp) {
                            Text(
                                text = msg,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
