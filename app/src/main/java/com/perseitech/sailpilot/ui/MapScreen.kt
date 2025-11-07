package com.perseitech.sailpilot.ui

import android.content.Context
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

import com.perseitech.sailpilot.routing.SeaRouter

import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline

/**
 * Schermata mappa (Compose) con gestione overlay/marker e rotta.
 * - Rimuove gli errori di inferenza tipi su mutableStateOf(...)
 * - Usa routeOverlay invece di una variabile inesistente 'route'
 * - Importa @Composable / SeaRouter
 */
@Composable
fun MapScreen(
    modifier: Modifier = Modifier
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    // Router (assicurati che SeaRouter sia in package com.perseitech.sailpilot.routing)
    val router = remember(ctx) { SeaRouter(ctx) }

    // Stato mappa / overlay
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var coastOverlay by remember { mutableStateOf<Polyline?>(null) }

    // Marker
    var startMarker by remember { mutableStateOf<Marker?>(null) }
    var endMarker   by remember { mutableStateOf<Marker?>(null) }
    var boatMarker  by remember { mutableStateOf<Marker?>(null) }

    // Punti start/end
    var start by remember { mutableStateOf<GeoPoint?>(null) }
    var end   by remember { mutableStateOf<GeoPoint?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).apply {
                // Configurazione base mappa; personalizza a piacere
                controller.setZoom(9.0)
                controller.setCenter(GeoPoint(43.0, 12.0))
                // salva il riferimento nella state
                mapView = this
            }
        },
        update = { view ->
            // quando cambiano overlay/marker, qui puoi fare invalidate
            view.invalidate()
        }
    )

    /**
     * Esempio: chiama il ricalcolo rotta quando hai sia start che end.
     * Sostituisci il TODO con la tua chiamata reale a SeaRouter.
     */
    fun recomputeRouteIfReady() {
        val mv = mapView ?: return
        val s = start ?: return
        val e = end ?: return

        scope.launch {
            // ===== COLLEGA QUI LA TUA LOGICA DI ROUTING =====
            // Esempio: val pts: List<GeoPoint> = router.route(s, e)
            //          oppure: val pts = router.computeRoute(s, e)
            // Per ora metto un TODO per non introdurre dipendenze sbagliate.
            val pts: List<GeoPoint> = run {
                // TODO: usa SeaRouter per calcolare i punti rotta fra 's' ed 'e'
                // Rimuovi questo fallback non-ottimale appena agganci il router.
                straightLineFallback(s, e)
            }
            // ================================================

            // (ri)costruisci l’overlay rotta
            routeOverlay?.let { mv.overlays.remove(it) }
            routeOverlay = Polyline().apply { setPoints(pts) }
            mv.overlays.add(routeOverlay)
            mv.invalidate()
        }
    }

    /**
     * Queste due funzioni demo mostrano come potresti settare i punti.
     * Nella tua app li avrai da tap/long-press o da UI esterna.
     */
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

    // --- Esempio: imposta due punti di test (puoi togliere) ---
    // Evita di eseguirlo a ogni recomposition: lo leghiamo alla prima creazione della mappa
    if (mapView != null && start == null && end == null) {
        // Punti fittizi in Tirreno
        setStart(GeoPoint(43.55, 10.30))
        setEnd(GeoPoint(42.45, 11.00))
    }
}

/**
 * Fallback temporaneo: polilinea rettilinea tra due punti.
 * Sostituiscilo con il risultato del SeaRouter.
 */
private fun straightLineFallback(a: GeoPoint, b: GeoPoint): List<GeoPoint> = listOf(a, b)
