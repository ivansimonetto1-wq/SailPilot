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
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.TilesOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

enum class AppMode { NAVIGATION, REGATTA }

@Composable
fun MapScreen(
    start: LatLon?,
    goal: LatLon?,
    path: List<LatLon>,
    pickMode: PickMode,
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
    // nuovi callback
    onOpenSettings: () -> Unit,
    onOpenSailToPort: () -> Unit,
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
                        controller.setZoom(8.5)
                        controller.setCenter(GeoPoint(41.9, 12.5))

                        overlays.add(ScaleBarOverlay(this))
                        overlays.add(CompassOverlay(ctx, InternalCompassOrientationProvider(ctx), this).apply { enableCompass() })
                        overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
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

                    if (seamarksEnabled && seamarksOverlay == null) {
                        val ov = Seamarks.buildOverlay(ctx); mv.overlays.add(ov); seamarksOverlay = ov
                    } else if (!seamarksEnabled && seamarksOverlay != null) {
                        mv.overlays.remove(seamarksOverlay); seamarksOverlay = null
                    }

                    mv.overlays.removeAll { it is Marker || it is Polyline }

                    start?.let {
                        mv.overlays.add(Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Partenza"
                            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        })
                    }
                    goal?.let {
                        mv.overlays.add(Marker(mv).apply {
                            position = GeoPoint(it.lat, it.lon)
                            title = "Arrivo"
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

            // MENU ⋮
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
            ) {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Modalità: " + if (appMode == AppMode.NAVIGATION) "Navigation" else "Regatta") },
                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                        onClick = { onToggleMode(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Impostazioni…") },
                        leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        onClick = { onOpenSettings(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Sail to Port…") },
                        leadingIcon = { Icon(Icons.Filled.Sailing, contentDescription = null) },
                        onClick = { onOpenSailToPort(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Connessioni dati…") },
                        leadingIcon = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                        onClick = { onOpenConnections(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Velocità stimata: ${estSpeedKnots} kn") },
                        onClick = {
                            val list = listOf(3,4,5,6,7,8)
                            val idx = list.indexOf(estSpeedKnots).takeIf { it >= 0 } ?: 2
                            onChangeSpeed(list[(idx + 1) % list.size]); menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (forceLightBasemap) "Usa mappa dettagliata" else "Usa mappa leggera") },
                        onClick = { onToggleForceLight?.invoke(); menuExpanded = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Tools…") },
                        onClick = { onOpenTools(); menuExpanded = false }
                    )
                    if (appMode == AppMode.NAVIGATION) {
                        DropdownMenuItem(text = { Text("Apri Control Panel") }, onClick = { onOpenControlOrRegatta(); menuExpanded = false })
                        DropdownMenuItem(text = { Text("Ottimizza rotta") }, onClick = { onOptimizeRoute(); menuExpanded = false })
                    } else {
                        DropdownMenuItem(text = { Text("Apri pannello Regatta") }, onClick = { onOpenControlOrRegatta(); menuExpanded = false })
                    }
                    DropdownMenuItem(
                        text = { Text("Credits") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                        onClick = { showCredits = true; menuExpanded = false }
                    )
                }
            }

            // FAB colonna
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showPortInfoIcon) {
                    FloatingActionButton(onClick = onOpenPortInfo) { Icon(Icons.Filled.Info, contentDescription = "Port info") }
                }
                FloatingActionButton(onClick = onToggleTracking) {
                    if (trackingEnabled) Icon(Icons.Filled.Stop, contentDescription = "Stop")
                    else Icon(Icons.Filled.PlayArrow, contentDescription = "Start")
                }
                FloatingActionButton(onClick = { seamarksEnabled = !seamarksEnabled }) {
                    if (seamarksEnabled) Icon(Icons.Filled.VisibilityOff, contentDescription = "Hide seamarks")
                    else Icon(Icons.Filled.Visibility, contentDescription = "Show seamarks")
                }
                FloatingActionButton(onClick = onOptimizeRoute) { Icon(Icons.Filled.Build, contentDescription = "Ottimizza") }
                FloatingActionButton(onClick = onRequestPickStart) { Icon(Icons.Filled.ArrowUpward, contentDescription = "Pick start") }
                FloatingActionButton(onClick = onClearRoute) { Icon(Icons.Filled.Delete, contentDescription = "Clear") }
                FloatingActionButton(onClick = onRequestGpsStart) { Icon(Icons.Filled.MyLocation, contentDescription = "From GPS") }
            }

            if (showCredits) {
                AlertDialog(
                    onDismissRequest = { showCredits = false },
                    confirmButton = {},
                    title = { Text("Credits") },
                    text = {
                        Column {
                            Text("© OpenStreetMap & OpenSeaMap contributors")
                            Text("Basemap light: © Stamen, © OpenStreetMap contributors")
                        }
                    }
                )
            }

            if (isRouting) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            if (pickMode != PickMode.NONE) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 56.dp)
                ) {
                    val msg = when (pickMode) {
                        PickMode.PICK_START -> "Long-tap per PARTENZA (solo mare)"
                        PickMode.PICK_GOAL -> "Long-tap per ARRIVO (solo mare)"
                        else -> ""
                    }
                    if (msg.isNotEmpty()) {
                        Surface(
                            modifier = Modifier.shadow(4.dp, RoundedCornerShape(8.dp)),
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface
                        ) { Text(msg, modifier = Modifier.padding(8.dp)) }
                    }
                }
            }
        }
    }
}
