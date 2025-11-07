package com.perseitech.sailpilot.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.launch

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

// IMPORT del router: assicurati che il package di SeaRouter sia questo.
import com.perseitech.sailpilot.routing.SeaRouter

@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Router
    val router = remember(ctx) { SeaRouter(ctx) }

    // ===== Stato mappa / overlay =====
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var coastOverlay by remember { mutableStateOf<Polyline?>(null) }

    // ===== Marker =====
    var startMarker by remember { mutableStateOf<Marker?>(null) }
    var endMarker by remember { mutableStateOf<Marker?>(null) }
    var boatMarker by remember { mutableStateOf<Marker?>(null) }

    // ===== Punti =====
    var start by remember { mutableStateOf<GeoPoint?>(null) }
    var end by remember { mutableStateOf<GeoPoint?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                controller.setZoom(9.0)
                controller.setCenter(GeoPoint(43.0, 12.0))
                mapView = this
            }
        },
        update = { it.invalidate() }
    )

    fun recomputeRouteIfReady() {
        val mv = mapView ?: return
        val s = start ?: return
        val e = end ?: return

        scope.launch {
            // Sostituisci con la tua API reale:
            // es. val pts: List<GeoPoint> = router.route(s, e)
            val pts: List<GeoPoint> = straightLineFallback(s, e)

            routeOverlay?.let { mv.overlays.remove(it) }
            routeOverlay = Polyline().apply { setPoints(pts) }
            mv.overlays.add(routeOverlay)
            mv.invalidate()
        }
    }

    fun setStart(point: GeoPoint) {
        val mv = mapView ?: return
        start = point
        startMarker?.let { mv.overlays.remove(it) }
        startMarker = Marker(mv).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Start"
        }
        mv.overlays.add(startMarker)
        mv.invalidate()
        recomputeRouteIfReady()
    }

    fun setEnd(point: GeoPoint) {
        val mv = mapView ?: return
        end = point
        endMarker?.let { mv.overlays.remove(it) }
        endMarker = Marker(mv).apply {
            position = point
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "End"
        }
        mv.overlays.add(endMarker)
        mv.invalidate()
        recomputeRouteIfReady()
    }

    // Demo: set di due punti di prova alla prima init
    if (mapView != null && start == null && end == null) {
        setStart(GeoPoint(43.55, 10.30))
        setEnd(GeoPoint(42.45, 11.00))
    }
}

private fun straightLineFallback(a: GeoPoint, b: GeoPoint): List<GeoPoint> = listOf(a, b)
