package com.perseitech.sailpilot.ui

import android.graphics.Color
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.perseitech.sailpilot.PickMode
import com.perseitech.sailpilot.routing.LatLon
import com.perseitech.sailpilot.tiles.LightBaseMap
import com.perseitech.sailpilot.tiles.Seamarks
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.*
import org.osmdroid.views.overlay.compass.CompassOverlay   // <-- FIX import corretto

enum class AppMode { NAVIGATION, REGATTA }

@Composable
fun MapScreen(
    start: LatLon?,
    goal: LatLon?,
    path: List<LatLon>,
    pickMode: com.perseitech.sailpilot.PickMode,
    isSea: (LatLon) -> Boolean,
    liveLocation: LatLon?,
    trackingEnabled: Boolean,
    onToggleTracking: () -> Unit,
    onRequestPickStart: () -> Unit,
    onRequestGpsStart: () -> Unit,
    onClearRoute: () -> Unit,
    onPointPicked: (LatLon) -> Unit,
    onOptimizeRoute: () -> Unit,
    estSpeedKnots: Int,
    onChangeSpeed: (Int) -> Unit,
    isRouting: Boolean = false,
    forceLightBasemap: Boolean = false,
    onToggleForceLight: (() -> Unit)? = null,
    appMode: AppMode,
    onToggleMode: () -> Unit,
    onOpenConnections: () -> Unit,
    onOpenControlOrRegatta: () -> Unit,
    onOpenTools: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSailToPort: () -> Unit,
    onOpenRegattaSettings: () -> Unit,
    onOpenAppTheme: () -> Unit,
    showPortInfoIcon: Boolean,
    onOpenPortInfo: () -> Unit
) {
    val ctx = LocalContext.current

    var seamarksEnabled by remember { mutableStateOf(true) }
    var seamarksOverlay by remember { mutableStateOf<TilesOverlay?>(null) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showCredits by remember { mutableStateOf(false) }
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
                        setBuiltInZoomControls(false)

                        controller.setZoom(8.5)
                        controller.setCenter(GeoPoint(41.9, 12.5))

                        overlays.add(ScaleBarOverlay(this))

                        // bussola semplice
                        overlays.add(
                            CompassOverlay(ctx, this).apply {
                                enableCompass()
                            }
                        )

                        overlays.add(MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint?) = false
                            override fun longPressHelper(p: GeoPoint?): Boolean {
                                if (p == null) return false
                                val picked = LatLon(p.latitude, p.longitude)
                                if (!isSea(picked)) return true
                                onPointPicked(picked)
                                return true
                            }
                        }))

                        mapView = this
                    }
                },
                update = { mv ->
                    val target = if (forceLightBasemap) LightBaseMap else TileSourceFactory.MAPNIK
                    if (mv.tileProvider.tileSource.name() != target.name()) mv.setTileSource(target)

                    // seamarks overlay
                    if (seamarksEnabled && seamarksOverlay == null) {
                        val ov = Seamarks.buildOverlay(ctx)
                        mv.overlays.add(ov); seamarksOverlay = ov
                    } else if (!seamarksEnabled && seamarksOverlay != null) {
                        mv.overlays.remove(seamarksOverlay); seamarksOverlay = null
                    }

                    // centratura su GPS se non c'è una rotta
                    if (liveLocation != null && start == null && goal == null && path.isEmpty()) {
                        mv.controller.setZoom(12.0)
                        mv.controller.setCenter(GeoPoint(liveLocation.lat, liveLocation.lon))
                    }

                    // pulizia marker / rotta
                    mv.overlays.removeAll { it is Marker || it is Polyline }

                    start?.let {
                        mv.overlays.add(Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Start"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    goal?.let {
                        mv.overlays.add(Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Goal"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    if (path.size > 1) {
                        mv.overlays.add(Polyline().apply {
                            outlinePaint.color = Color.RED
                            outlinePaint.strokeWidth = 6f
                            setPoints(path.map { GeoPoint(it.lat, it.lon) })
                        })
                    }
                    mv.invalidate()
                }
            )

            // MENU ⋮, FAB, overlay routing – identici alla versione che hai già
            // (li lascio invariati per non allungare ancora di più il file)
            // ---------------------------------------------------------------
            // Usa pure quelli dell’ultima versione funzionante, l’unico cambio
            // importante qui è l’import di CompassOverlay e la centratura sul GPS.
            // ---------------------------------------------------------------
        }
    }
}
