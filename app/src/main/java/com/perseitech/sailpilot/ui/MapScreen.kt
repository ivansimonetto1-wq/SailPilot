package com.perseitech.sailpilot.ui

import android.content.Context
import android.preference.PreferenceManager
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

import com.perseitech.sailpilot.R
import com.perseitech.sailpilot.data.Geo
import com.perseitech.sailpilot.data.LatLon
import com.perseitech.sailpilot.location.LocationService
import com.perseitech.sailpilot.routing.SeaRouter

import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

/* ===== Enums a livello di file ===== */
enum class PickMode { Idle, PickStart, PickDest, GpsStart }

/* ===== Composable ===== */
@Composable
fun MapScreen(modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val router = remember { SeaRouter(ctx) }
    val scope = rememberCoroutineScope()

    // stato mappa/overlay
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var routeOverlay by remember { mutableStateOf<Polyline?>(null) }
    var trackOverlay by remember { mutableStateOf<Polyline?>(null) }

    // marker
    var startMarker by remember { mutableStateOf<Marker?>(null) }
    var destMarker by remember { mutableStateOf<Marker?>(null) }
    var boatMarker by remember { mutableStateOf<Marker?>(null) }

    // punti
    var start by remember { mutableStateOf<GeoPoint?>(null) }
    var dest by remember { mutableStateOf<GeoPoint?>(null) }

    // scia
    val track = remember { mutableStateListOf<GeoPoint>() }

    // HUD
    var hudText by remember { mutableStateOf("—") }

    // modalità scelta
    var pickMode by remember { mutableStateOf(PickMode.Idle) }
    var hint by remember { mutableStateOf("—") }

    // Dialogo “popup”
    var showDialog by remember { mutableStateOf(false) }
    var dialogText by remember { mutableStateOf("") }

    // job calcolo rotta (debounce)
    var routeJob by remember { mutableStateOf<Job?>(null) }

    /* ====== LAYOUT ====== */
    Box(modifier = modifier.fillMaxSize()) {

        /* ---------- MapView ---------- */
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context: Context ->
                Configuration.getInstance().load(
                    context,
                    PreferenceManager.getDefaultSharedPreferences(context)
                )
                Configuration.getInstance().userAgentValue = "SailPilot-MVP"

                MapView(context).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    controller.setZoom(6.5)
                    controller.setCenter(GeoPoint(42.5, 12.5))

                    overlays.add(RotationGestureOverlay(this).apply { isEnabled = true })
                    overlays.add(ScaleBarOverlay(this))

                    routeOverlay = Polyline().apply {
                        outlinePaint.strokeWidth = 5f
                        outlinePaint.color = android.graphics.Color.RED
                    }
                    overlays.add(routeOverlay)

                    trackOverlay = Polyline().apply { outlinePaint.strokeWidth = 6f }
                    overlays.add(trackOverlay)

                    startMarker = Marker(this).apply {
                        title = "Partenza"
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_start)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        isEnabled = false
                    }
                    overlays.add(startMarker)

                    destMarker = Marker(this).apply {
                        title = "Arrivo"
                        icon = ContextCompat.getDrawable(context, R.drawable.ic_marker_dest)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        isEnabled = false
                    }
                    overlays.add(destMarker)

                    boatMarker = Marker(this).apply {
                        title = "Barca"
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        isEnabled = false
                    }
                    overlays.add(boatMarker)

                    val events = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean = false
                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            val gp = p ?: return false
                            when (pickMode) {
                                PickMode.PickStart -> {
                                    start = gp
                                    startMarker?.apply { position = gp; isEnabled = true }
                                    pickMode = PickMode.PickDest
                                    hint = "Seleziona ARRIVO (long press)"
                                    // popup per ARRIVO
                                    dialogText = "Ora tocca a lungo sulla mappa per impostare l’ARRIVO."
                                    showDialog = true
                                }
                                PickMode.PickDest -> {
                                    dest = gp
                                    destMarker?.apply { position = gp; isEnabled = true }
                                    hint = "Calcolo rotta…"
                                    // il calcolo lo fa LaunchedEffect(start, dest)
                                }
                                else -> Unit
                            }
                            invalidate()
                            return true
                        }
                    }
                    overlays.add(MapEventsOverlay(events))

                    mapView = this
                }
            },
            onRelease = { v -> try { v.onPause(); v.onDetach() } catch (_: Throwable) {} }
        )

        /* ---------- HUD (in alto a sinistra) ---------- */
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color(0x66000000))
                .padding(8.dp)
        ) { Text(if (hint != "—") "$hint   $hudText" else hudText, color = Color.White) }

        /* ---------- Pulsanti (in basso a sinistra) ---------- */
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .wrapContentHeight(),
            horizontalAlignment = Alignment.Start
        ) {
            // Partenza da mappa
            Button(
                onClick = {
                    pickMode = PickMode.PickStart
                    hint = "Seleziona PARTENZA (long press)"
                    dialogText = "Tocca a lungo sulla mappa per impostare la PARTENZA."
                    showDialog = true
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) { Text("Partenza da mappa") }

            // Partenza da GPS
            Button(
                onClick = {
                    pickMode = PickMode.GpsStart
                    hint = "Leggo la tua posizione…"
                    scope.launch {
                        val last = LocationService.lastLocation.firstOrNull()
                        val mv = mapView
                        if (last != null && mv != null) {
                            val gp = GeoPoint(last.latitude, last.longitude)
                            start = gp
                            startMarker?.apply { position = gp; isEnabled = true }
                            mv.controller.setZoom(14.0)
                            mv.controller.setCenter(gp)
                            pickMode = PickMode.PickDest
                            hint = "Seleziona ARRIVO (long press)"
                            dialogText = "Posizione acquisita. Ora tocca a lungo per impostare l’ARRIVO."
                            showDialog = true
                        } else {
                            pickMode = PickMode.Idle
                            hint = "GPS non disponibile"
                            dialogText = "GPS non disponibile."
                            showDialog = true
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 8.dp)
            ) { Text("Partenza da GPS") }

            // Clear
            Button(onClick = {
                pickMode = PickMode.Idle
                hint = "—"
                start = null
                dest = null
                startMarker?.isEnabled = false
                destMarker?.isEnabled = false
                routeOverlay?.setPoints(emptyList())
                mapView?.invalidate()
                routeJob?.cancel()
                routeJob = null
            }) { Text("Clear") }
        }

        /* ---------- Dialog (popup) ---------- */
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                confirmButton = {
                    TextButton(onClick = { showDialog = false }) { Text("OK") }
                },
                title = { Text("SailPilot") },
                text = { Text(dialogText) }
            )
        }
    }

    /* ---------- Tracking barca + HUD ---------- */
    LaunchedEffect(Unit) {
        LocationService.lastLocation.collect { loc ->
            val mv = mapView ?: return@collect
            if (loc != null) {
                val gp = GeoPoint(loc.latitude, loc.longitude)
                boatMarker?.apply { position = gp; isEnabled = true }

                // scia
                track.add(gp)
                trackOverlay?.setPoints(track)

                // HUD
                dest?.let { d ->
                    val nm = Geo.distanceNm(LatLon(gp.latitude, gp.longitude), LatLon(d.latitude, d.longitude))
                    val brg = Geo.bearingDeg(LatLon(gp.latitude, gp.longitude), LatLon(d.latitude, d.longitude))
                    hudText = "D: ${"%.2f".format(nm)} nm  PR: ${"%.0f".format(brg)}°"
                }

                mv.controller.setCenter(gp)
                mv.invalidate()
            }
        }
    }

    /* ---------- Ricalcolo rotta (debounced) ---------- */
    LaunchedEffect(start, dest) {
        val mv = mapView ?: return@LaunchedEffect
        if (start == null || dest == null) return@LaunchedEffect

        routeJob?.cancel()
        routeJob = launch {
            hint = "Calcolo rotta…"
            val path = router.route(start!!, dest!!, stepDeg = 0.001)
            routeOverlay?.setPoints(path)
            mv.invalidate()
            hint = "—"
        }
    }
}
