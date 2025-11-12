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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.perseitech.sailpilot.export.GpxExporter
import com.perseitech.sailpilot.io.DataBus
import com.perseitech.sailpilot.io.NavData
import com.perseitech.sailpilot.location.LocationService
import com.perseitech.sailpilot.location.LocationServiceController
import com.perseitech.sailpilot.ports.Port
import com.perseitech.sailpilot.ports.PortInfo
import com.perseitech.sailpilot.ports.PortInfoService
import com.perseitech.sailpilot.ports.PortsRepository
import com.perseitech.sailpilot.routing.*
import com.perseitech.sailpilot.settings.BoatSettings
import com.perseitech.sailpilot.settings.BoatSettingsRepository
import com.perseitech.sailpilot.ui.*
import com.perseitech.sailpilot.regatta.BoatClass
import com.perseitech.sailpilot.regatta.BoatClassesRepo
import com.perseitech.sailpilot.regatta.RegattaSettings
import com.perseitech.sailpilot.regatta.RegattaSettingsRepo
import com.perseitech.sailpilot.ui.RegattaBoatDialog
import com.perseitech.sailpilot.ui.SailAdvisorPanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt

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

                    // ---- Stato base rotta ----
                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

                    // porti selezionati (per Info)
                    var startPort by remember { mutableStateOf<Port?>(null) }
                    var goalPort by remember { mutableStateOf<Port?>(null) }

                    // tracking
                    var trackingEnabled by remember { mutableStateOf(false) }
                    val controller = remember { LocationServiceController(this) }
                    val lastLoc by LocationService.lastLocation.collectAsState(initial = null)

                    // NAV DATA BUS (NMEA / SignalK)
                    val nav by DataBus.nav.collectAsState(initial = NavData())
                    val headingDeg = nav.headingDeg ?: lastLoc?.bearing?.toDouble()
                    val sogKn = nav.sogKn ?: lastLoc?.speed?.takeIf { it > 0 }?.let { it * 1.9438445 }
                    val cogDeg = nav.cogDeg ?: headingDeg
                    val liveLatLon: LatLon? = nav.position ?: lastLoc?.let { LatLon(it.latitude, it.longitude) }
                    // (per ora il vento non è collegato qui; lo useremo nel SailAdvisor quando disponibile)

                    var unitMode by remember { mutableStateOf(UnitMode.NAUTICAL) }
                    var speed by remember { mutableStateOf(5.0) }
                    var forceLightBasemap by remember { mutableStateOf(false) }
                    var appMode by remember { mutableStateOf(AppMode.NAVIGATION) }

                    // WKT costa / router
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

                    // Boat settings (generali)
                    val boatRepo = remember { BoatSettingsRepository(this@MainActivity) }
                    val boat by boatRepo.flow.collectAsState(initial = BoatSettings())

                    // Ports
                    val ports by produceState<List<Port>>(initialValue = emptyList()) {
                        value = PortsRepository.loadPorts(this@MainActivity)
                    }

                    // -------- REGATTA settings --------
                    val regRepo = remember { RegattaSettingsRepo(this@MainActivity) }
                    val regSettings by regRepo.flow.collectAsState(initial = RegattaSettings())
                    val boatClasses by produceState<List<BoatClass>>(initialValue = emptyList()) {
                        value = BoatClassesRepo.load(this@MainActivity)
                    }
                    val selectedClass = remember(regSettings, boatClasses) {
                        boatClasses.find { it.id == regSettings.classId }
                    }
                    var showRegattaSettings by remember { mutableStateOf(false) }

                    // Dialogs vari
                    var showSettings by remember { mutableStateOf(false) }
                    var showSailToPort by remember { mutableStateOf(false) }
                    var portInfoToShow by remember { mutableStateOf<PortInfo?>(null) }
                    var loadingPortInfo by remember { mutableStateOf(false) }

                    fun recalcRouteAsync(s: LatLon, g: LatLon) {
                        if (!wktIsOk) {
                            Toast.makeText(this, "Coast WKT non valido.", Toast.LENGTH_LONG).show()
                            return
                        }
                        uiScope.launch {
                            isRouting = true
                            val route = withContext(Dispatchers.Default) {
                                val bbox = BBox.around(s, g)
                                router.route(landWkt, s, g, bbox, padMeters = 2500.0, targetCellMeters = 160.0)
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
                                    route = simplified, land = mask, iters = 1, alpha = 0.25,
                                    sampleStepMeters = sample, keepEndpoints = true
                                )
                            }
                            isRouting = false
                            path = newPath
                            Toast.makeText(this@MainActivity, "Rotta ottimizzata.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // Dati derivati rotta
                    val nextWp: LatLon? = if (path.isNotEmpty()) {
                        val me = liveLatLon
                        if (me == null) path.first()
                        else path.firstOrNull { GeoUtils.distanceMeters(me, it) > 30.0 } ?: path.last()
                    } else null
                    val brgToWp = if (liveLatLon != null && nextWp != null) initialBearing(liveLatLon, nextWp) else null
                    val distToWpNm = if (liveLatLon != null && nextWp != null)
                        GeoUtils.distanceMeters(liveLatLon, nextWp) / 1852.0 else null
                    val etaToWpText = if (sogKn != null && sogKn > 0 && distToWpNm != null) {
                        val h = distToWpNm / sogKn; val hh = h.toInt(); val mm = ((h - hh) * 60).roundToInt(); "${hh}h ${mm}m"
                    } else null
                    val estSpeedKnots = if (unitMode == UnitMode.NAUTICAL) speed else (speed / 1.852)

                    // ---- Pager: titoli dipendenti da modalità ----
                    val titles = if (appMode == AppMode.NAVIGATION)
                        listOf("Mappa", "Control", "Tools", "Instruments", "Compass", "Connections")
                    else
                        listOf("Mappa", "Regatta", "Sails", "Instruments", "Compass", "Tools", "Connections")

                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { titles.size })
                    val uiScopePager = rememberCoroutineScope()
                    fun goTo(pageName: String) {
                        val idx = titles.indexOf(pageName)
                        if (idx >= 0) uiScopePager.launch { pagerState.animateScrollToPage(idx) }
                    }

                    // ---- Tabs
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { i, t ->
                            Tab(
                                selected = pagerState.currentPage == i,
                                onClick = { uiScopePager.launch { pagerState.animateScrollToPage(i) } },
                                text = { Text(t) }
                            )
                        }
                    }

                    // ---- Contenuto
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
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
                                                startPort = null
                                                goal = null; goalPort = null
                                                path = emptyList(); pickMode = PickMode.PICK_GOAL
                                            } else {
                                                Toast.makeText(this@MainActivity, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        onClearRoute = {
                                            start = null; goal = null; path = emptyList()
                                            pickMode = PickMode.NONE; startPort = null; goalPort = null
                                        },
                                        onPointPicked = { picked ->
                                            if (wktIsOk && !isSea(picked)) {
                                                Toast.makeText(this@MainActivity, "Seleziona solo punti in MARE.", Toast.LENGTH_SHORT).show()
                                                return@MapScreen
                                            }
                                            when (pickMode) {
                                                PickMode.PICK_START -> {
                                                    start = picked; startPort = null
                                                    goal = null; goalPort = null; path = emptyList()
                                                    pickMode = PickMode.PICK_GOAL
                                                }
                                                PickMode.PICK_GOAL -> {
                                                    goal = picked; goalPort = null; pickMode = PickMode.NONE
                                                    start?.let { recalcRouteAsync(it, picked) }
                                                }
                                                else -> {}
                                            }
                                        },
                                        onOptimizeRoute = { optimizeRouteIfAny() },
                                        estSpeedKnots = estSpeedKnots.toInt().coerceAtLeast(0),
                                        onChangeSpeed = { newKn ->
                                            speed = if (unitMode == UnitMode.NAUTICAL) newKn.toDouble() else newKn.toDouble()
                                        },
                                        isRouting = isRouting,
                                        forceLightBasemap = forceLightBasemap,
                                        onToggleForceLight = { forceLightBasemap = !forceLightBasemap },
                                        appMode = appMode,
                                        onToggleMode = {
                                            appMode = if (appMode == AppMode.NAVIGATION) AppMode.REGATTA else AppMode.NAVIGATION
                                        },
                                        onOpenConnections = { goTo("Connections") },
                                        onOpenControlOrRegatta = {
                                            goTo(if (appMode == AppMode.NAVIGATION) "Control" else "Regatta")
                                        },
                                        onOpenTools = { goTo("Tools") },
                                        onOpenSettings = {
                                            if (appMode == AppMode.REGATTA) showRegattaSettings = true
                                            else showSettings = true
                                        },
                                        onOpenSailToPort = { showSailToPort = true },
                                        showPortInfoIcon = (startPort != null && goalPort != null),
                                        onOpenPortInfo = {
                                            val apikey = boat.seaRatesApiKey
                                            val dest = goalPort ?: return@MapScreen
                                            uiScope.launch {
                                                loadingPortInfo = true
                                                val info = withContext(Dispatchers.IO) {
                                                    PortInfoService.fetchPortInfo(apikey, dest.unlocode ?: dest.name)
                                                }
                                                loadingPortInfo = false
                                                if (info != null) portInfoToShow = info
                                                else {
                                                    // fallback minimale
                                                    portInfoToShow = PortInfo(
                                                        title = dest.name,
                                                        country = dest.country,
                                                        UNLOCODE = dest.unlocode,
                                                        description = "Info dettagliate non disponibili.",
                                                        website = null, phone = null, email = null, address = null
                                                    )
                                                }
                                            }
                                        }
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
                                            val uri = GpxExporter.exportToDownloads(
                                                this@MainActivity,
                                                "SailPilot-Route",
                                                path
                                            )
                                            Toast.makeText(
                                                this@MainActivity,
                                                if (uri != null) "GPX salvato in Download."
                                                else "Export GPX fallito.",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    )
                                }

                                "Instruments" -> {
                                    val latText = liveLatLon?.let {
                                        (if (it.lat >= 0) "N " else "S ") + String.format("%.5f°", abs(it.lat))
                                    }
                                    val lonText = liveLatLon?.let {
                                        (if (it.lon >= 0) "E " else "W ") + String.format("%.5f°", abs(it.lon))
                                    }
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
                                    // versione attuale del tuo RegattaPanel (dati “generali”)
                                    RegattaPanel(
                                        sogKn = sogKn,
                                        cogDeg = cogDeg,
                                        brgToWpDeg = brgToWp,
                                        distToWpNm = distToWpNm,
                                        etaToWpText = etaToWpText
                                    )
                                }

                                "Sails" -> {
                                    // Sail Advisor: suggerimenti vele (TWS/TWA manuali fino a collegamento strumenti)
                                    SailAdvisorPanel(
                                        settings = regSettings,
                                        boatClass = selectedClass,
                                        twsKnFromSensors = null,  // collega a DataBus quando disponibile
                                        twaDegFromSensors = null  // collega a DataBus quando disponibile
                                    )
                                }

                                "Connections" -> {
                                    ConnectionsPanel(appScope = uiScope)
                                }
                            }
                        }

                        // puntini del pager
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
                                        .clickable { uiScopePager.launch { pagerState.animateScrollToPage(i) } }
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

                    // --- Dialog IMPOSTAZIONI (generali) ---
                    if (showSettings) {
                        SettingsDialog(
                            draftMInit = boat.draftM,
                            lengthMInit = boat.lengthM,
                            beamMInit = boat.beamM,
                            seaRatesApiKeyInit = boat.seaRatesApiKey,
                            onDismiss = { showSettings = false },
                            onSave = { d, l, b, k ->
                                uiScope.launch {
                                    boatRepo.saveDraft(d); boatRepo.saveLength(l); boatRepo.saveBeam(b); boatRepo.saveApiKey(k)
                                    showSettings = false
                                    Toast.makeText(this@MainActivity, "Impostazioni salvate.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // --- Dialog IMPOSTAZIONI REGATTA ---
                    if (showRegattaSettings) {
                        RegattaBoatDialog(
                            classes = boatClasses,
                            current = regSettings,
                            onDismiss = { showRegattaSettings = false },
                            onSave = { s ->
                                uiScope.launch {
                                    regRepo.save(s)
                                    showRegattaSettings = false
                                    Toast.makeText(this@MainActivity, "Impostazioni Regatta salvate.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }

                    // --- Dialog SAIL TO PORT ---
                    if (showSailToPort) {
                        SailToPortDialog(
                            ports = ports,
                            onDismiss = { showSailToPort = false },
                            onConfirm = { fromPort, useGps, toPort ->
                                val s = if (useGps) {
                                    val loc = getLastKnownLocation()
                                    if (loc != null) LatLon(loc.latitude, loc.longitude) else null
                                } else {
                                    LatLon(fromPort!!.lat, fromPort.lon)
                                }
                                val g = LatLon(toPort.lat, toPort.lon)
                                if (s == null) {
                                    Toast.makeText(this@MainActivity, "Posizione GPS non disponibile.", Toast.LENGTH_LONG).show()
                                } else {
                                    start = s; goal = g
                                    startPort = if (useGps) null else fromPort
                                    goalPort = toPort
                                    path = emptyList()
                                    recalcRouteAsync(s, g)
                                }
                                showSailToPort = false
                            }
                        )
                    }

                    // --- Scheda INFO porto ---
                    if (portInfoToShow != null) {
                        val p = portInfoToShow!!
                        AlertDialog(
                            onDismissRequest = { portInfoToShow = null },
                            confirmButton = { TextButton(onClick = { portInfoToShow = null }) { Text("OK") } },
                            title = { Text("Port Info — ${p.title}") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (loadingPortInfo) androidx.compose.material3.LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    if (p.UNLOCODE != null) Text("UNLOCODE: ${p.UNLOCODE}")
                                    if (!p.country.isNullOrBlank()) Text("Paese: ${p.country}")
                                    if (!p.address.isNullOrBlank()) Text("Indirizzo: ${p.address}")
                                    if (!p.phone.isNullOrBlank()) Text("Tel: ${p.phone}")
                                    if (!p.email.isNullOrBlank()) Text("Email: ${p.email}")
                                    if (!p.website.isNullOrBlank()) Text("Web: ${p.website}")
                                    if (!p.description.isNullOrBlank()) Text(p.description!!)
                                }
                            }
                        )
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
    val y = kotlin.math.sin(dλ) * kotlin.math.cos(φ2)
    val x = kotlin.math.cos(φ1) * kotlin.math.sin(φ2) - kotlin.math.sin(φ1) * kotlin.math.cos(φ2) * kotlin.math.cos(dλ)
    var deg = Math.toDegrees(kotlin.math.atan2(y, x))
    if (deg < 0) deg += 360.0
    return deg
}

enum class PickMode { NONE, PICK_START, PICK_GOAL }
