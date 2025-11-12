package com.perseitech.sailpilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.perseitech.sailpilot.export.GpxExporter
import com.perseitech.sailpilot.location.LocationService
import com.perseitech.sailpilot.location.LocationServiceController
import com.perseitech.sailpilot.routing.*
import com.perseitech.sailpilot.ui.*
import com.perseitech.sailpilot.io.DataBus
import com.perseitech.sailpilot.io.NavData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*

class MainActivity : ComponentActivity() {

    private fun getLastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        for (p in providers.reversed()) {
            if (
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) return null
            val loc = lm.getLastKnownLocation(p)
            if (loc != null) return loc
        }
        return null
    }

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        org.osmdroid.config.Configuration.getInstance().userAgentValue = "SailPilot/1.0 (osmdroid)"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // ---- STATE condiviso ----
                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

                    var trackingEnabled by remember { mutableStateOf(false) }
                    val controller = remember { LocationServiceController(this) }
                    val lastLoc by LocationService.lastLocation.collectAsState(initial = null)

                    // ---- NAV DATA BUS (SignalK/NMEA) con fallback GPS ----
                    val nav by DataBus.nav.collectAsState(initial = NavData())
                    val headingDeg = nav.headingDeg ?: lastLoc?.bearing?.toDouble()
                    val sogKn = nav.sogKn ?: lastLoc?.speed?.takeIf { it > 0 }?.let { it * 1.9438445 }
                    val cogDeg = nav.cogDeg ?: headingDeg
                    val liveLatLon: LatLon? = nav.position ?: lastLoc?.let { LatLon(it.latitude, it.longitude) }

                    var unitMode by remember { mutableStateOf(UnitMode.NAUTICAL) }
                    var speed by remember { mutableStateOf(5.0) }
                    var forceLightBasemap by remember { mutableStateOf(false) }

                    // MODALITÀ
                    var appMode by remember { mutableStateOf(AppMode.NAVIGATION) }

                    // WKT costa
                    val landWktRaw by remember {
                        mutableStateOf(
                            runCatching { assets.open("coast.wkt").bufferedReader().use { it.readText() } }.getOrElse { "" }
                        )
                    }
                    val landWkt = remember(landWktRaw) { landWktRaw.trim() }
                    val wktIsOk = remember(landWkt) { landWkt.startsWith("MULTIPOLYGON", true) || landWkt.startsWith("POLYGON", true) }
                    val landIndex by remember(landWkt) { mutableStateOf(if (wktIsOk) WktLandIndex.parse(landWkt) else WktLandIndex.parse("")) }
                    val isSea: (LatLon) -> Boolean = { p -> if (wktIsOk) !landIndex.contains(p) else true }
                    val router = remember { SeaRouter() }

                    val uiScope = rememberCoroutineScope()
                    var isRouting by remember { mutableStateOf(false) }

                    fun recalcRouteAsync(s: LatLon, g: LatLon) {
                        if (!wktIsOk) {
                            Toast.makeText(this, "Coast WKT non valido.", Toast.LENGTH_LONG).show()
                            return
                        }
                        uiScope.launch {
                            isRouting = true
                            val route = withContext(Dispatchers.Default) {
                                val bbox = BBox.around(s, g)
                                router.route(
                                    landWkt = landWkt,
                                    start = s,
                                    goal = g,
                                    bbox = bbox,
                                    padMeters = 2500.0,
                                    targetCellMeters = 160.0
                                )
                            }
                            isRouting = false
                            if (route == null) {
                                Toast.makeText(this@MainActivity, "Nessun percorso marino trovato.", Toast.LENGTH_LONG).show()
                            } else {
                                path = route
                            }
                        }
                    }

                    fun optimizeRouteIfAny() {
                        val s = start; val g = goal
                        if (s == null || g == null || path.size < 3 || !wktIsOk) return
                        uiScope.launch {
                            isRouting = true
                            val newPath = withContext(Dispatchers.Default) {
                                val bbox = BBox.around(s, g)
                                val cfg = GridConfig.fromBBox(bbox, padMeters = 1500.0, targetCellMeters = 140.0)
                                val mask = LandMaskBuilder.fromWkt(landWkt, cfg).dilated(1)
                                val lat0 = cfg.bbox.center.lat.coerceIn(-89.0, 89.0)
                                val mPerDegLat = 111_132.0
                                val mPerDegLon = 111_320.0 * cos(lat0 * PI / 180.0)
                                val cellLatM = cfg.stepLatDeg * mPerDegLat
                                val cellLonM = cfg.stepLonDeg * mPerDegLon
                                val cellM = min(cellLatM, cellLonM)
                                val tol = 1.6 * cellM
                                val sample = 0.5 * cellM
                                val simplified = PathPost.simplifyRouteSafe(path, mask, tolMeters = tol, sampleStepMeters = sample)
                                PathPost.smoothChaikinSafe(
                                    route = simplified, land = mask, iters = 1, alpha = 0.25, sampleStepMeters = sample, keepEndpoints = true
                                )
                            }
                            isRouting = false
                            path = newPath
                            Toast.makeText(this@MainActivity, "Rotta ottimizzata.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Dati per pannelli
                    fun fmtLat(v: Double) = (if (v >= 0) "N " else "S ") + String.format("%.5f°", abs(v))
                    fun fmtLon(v: Double) = (if (v >= 0) "E " else "W ") + String.format("%.5f°", abs(v))
                    val latText = liveLatLon?.let { fmtLat(it.lat) }
                    val lonText = liveLatLon?.let { fmtLon(it.lon) }

                    val nextWp: LatLon? = if (path.isNotEmpty()) {
                        val me = liveLatLon
                        if (me == null) path.first() else path.firstOrNull { GeoUtils.distanceMeters(me, it) > 30.0 } ?: path.last()
                    } else null
                    val brgToWp = if (liveLatLon != null && nextWp != null) initialBearing(liveLatLon, nextWp) else null
                    val distToWpNm = if (liveLatLon != null && nextWp != null) GeoUtils.distanceMeters(liveLatLon, nextWp) / 1852.0 else null
                    val etaToWpText = if (sogKn != null && sogKn > 0 && distToWpNm != null) {
                        val h = distToWpNm / sogKn; val hh = h.toInt(); val mm = ((h - hh) * 60).roundToInt(); "${hh}h ${mm}m"
                    } else null

                    val estSpeedKnots = if (unitMode == UnitMode.NAUTICAL) speed else (speed / 1.852)

                    // ---- PAGINE dinamiche ----
                    val titles = if (appMode == AppMode.NAVIGATION)
                        listOf("Mappa", "Control", "Tools", "Instruments", "Compass", "Connections")
                    else
                        listOf("Mappa", "Regatta", "Instruments", "Compass", "Tools", "Connections")

                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { titles.size })
                    val uiScopePager = rememberCoroutineScope()

                    fun goTo(pageName: String) {
                        val idx = titles.indexOf(pageName)
                        if (idx >= 0) uiScopePager.launch { pagerState.animateScrollToPage(idx) }
                    }

                    // ---- TAB ----
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { i, t ->
                            Tab(
                                selected = pagerState.currentPage == i,
                                onClick = { uiScopePager.launch { pagerState.animateScrollToPage(i) } },
                                text = { Text(t) }
                            )
                        }
                    }

                    // ---- CONTENUTO ----
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            when (titles[page]) {
                                "Mappa" -> {
                                    MapScreen(
                                        start = start,
                                        goal = goal,
                                        path = path,
                                        pickMode = pickMode,
                                        isSea = isSea,
                                        liveLocation = liveLatLon,
                                        trackingEnabled = trackingEnabled,
                                        onToggleTracking = {
                                            if (trackingEnabled) { controller.stop(); trackingEnabled = false }
                                            else { controller.start(); trackingEnabled = true }
                                        },
                                        onRequestPickStart = { pickMode = PickMode.PICK_START },
                                        onRequestGpsStart = {
                                            val loc = getLastKnownLocation()
                                            if (loc != null) {
                                                start = LatLon(loc.latitude, loc.longitude)
                                                goal = null; path = emptyList(); pickMode = PickMode.PICK_GOAL
                                            } else {
                                                Toast.makeText(this@MainActivity, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onClearRoute = { start = null; goal = null; path = emptyList(); pickMode = PickMode.NONE },
                                        onPointPicked = { picked ->
                                            if (wktIsOk && !isSea(picked)) {
                                                Toast.makeText(this@MainActivity, "Seleziona solo punti in MARE.", Toast.LENGTH_SHORT).show()
                                                return@MapScreen
                                            }
                                            when (pickMode) {
                                                PickMode.PICK_START -> { start = picked; goal = null; path = emptyList(); pickMode = PickMode.PICK_GOAL }
                                                PickMode.PICK_GOAL -> { goal = picked; pickMode = PickMode.NONE; start?.let { recalcRouteAsync(it, picked) } }
                                                else -> {}
                                            }
                                        },
                                        onOptimizeRoute = { optimizeRouteIfAny() },
                                        estSpeedKnots = estSpeedKnots.toInt().coerceAtLeast(0),
                                        onChangeSpeed = { newKn -> speed = if (unitMode == UnitMode.NAUTICAL) newKn.toDouble() else newKn.toDouble() },
                                        isRouting = isRouting,
                                        forceLightBasemap = forceLightBasemap,
                                        onToggleForceLight = { forceLightBasemap = !forceLightBasemap },
                                        appMode = appMode,
                                        onToggleMode = {
                                            appMode = if (appMode == AppMode.NAVIGATION) AppMode.REGATTA else AppMode.NAVIGATION
                                        },
                                        onOpenConnections = { goTo("Connections") },
                                        onOpenControlOrRegatta = { goTo(if (appMode == AppMode.NAVIGATION) "Control" else "Regatta") },
                                        onOpenTools = { goTo("Tools") }
                                    )
                                }
                                "Control" -> {
                                    Box(Modifier.fillMaxSize().background(Color.Black)) {
                                        RouteControlPanel(
                                            start = start,
                                            goal = goal,
                                            path = path,
                                            speed = speed,
                                            unitMode = unitMode,
                                            onUnitToggle = { unitMode = it },
                                            onSpeedChange = { speed = it }
                                        )
                                    }
                                }
                                "Tools" -> {
                                    ToolsPage(
                                        path = path,
                                        unitMode = unitMode,
                                        speed = speed,
                                        onExportGpx = {
                                            val uri = GpxExporter.exportToDownloads(this@MainActivity, "SailPilot-Route", path)
                                            Toast.makeText(
                                                this@MainActivity,
                                                if (uri != null) "GPX salvato in Download." else "Export GPX fallito.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }
                                "Instruments" -> {
                                    InstrumentPanel(
                                        headingDeg = headingDeg,
                                        sogKn = sogKn,
                                        cogDeg = cogDeg,
                                        latText = latText,
                                        lonText = lonText,
                                        distNextNm = distToWpNm,
                                        brgNextDeg = brgToWp,
                                        etaNextText = etaToWpText
                                    )
                                }
                                "Compass" -> {
                                    CompassPanel(
                                        cogDeg = cogDeg,
                                        courseToWpDeg = brgToWp,
                                        sogKn = sogKn,
                                        etaText = etaToWpText
                                    )
                                }
                                "Regatta" -> {
                                    RegattaPanel(
                                        sogKn = sogKn,
                                        cogDeg = cogDeg,
                                        brgToWpDeg = brgToWp,
                                        distToWpNm = distToWpNm,
                                        etaToWpText = etaToWpText
                                    )
                                }
                                "Connections" -> {
                                    ConnectionsPanel(appScope = uiScope)
                                }
                            }
                        }

                        // dots
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            repeat(titles.size) { i ->
                                val selected = pagerState.currentPage == i
                                Box(
                                    modifier = Modifier
                                        .size(if (selected) 10.dp else 8.dp)
                                        .background(
                                            color = if (selected) MaterialTheme.colorScheme.primary else Color.LightGray,
                                            shape = MaterialTheme.shapes.small
                                        )
                                        .clickable { uiScope.launch { pagerState.animateScrollToPage(i) } }
                                )
                            }
                        }

                        if (isRouting) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) { CircularProgressIndicator() }
                        }
                    }
                }
            }
        }
    }
}

private fun initialBearing(a: LatLon, b: LatLon): Double {
    val φ1 = Math.toRadians(a.lat); val φ2 = Math.toRadians(b.lat)
    val λ1 = Math.toRadians(a.lon); val λ2 = Math.toRadians(b.lon)
    val dλ = λ2 - λ1
    val y = sin(dλ) * cos(φ2)
    val x = cos(φ1) * sin(φ2) - sin(φ1) * cos(φ2) * cos(dλ)
    var deg = Math.toDegrees(atan2(y, x))
    if (deg < 0) deg += 360.0
    return deg
}

enum class PickMode { NONE, PICK_START, PICK_GOAL }
