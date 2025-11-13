package com.perseitech.sailpilot

import android.Manifest
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.config.Configuration
import kotlin.math.abs

import com.perseitech.sailpilot.export.GpxExporter
import com.perseitech.sailpilot.location.LocationService
import com.perseitech.sailpilot.location.LocationServiceController
import com.perseitech.sailpilot.ports.*
import com.perseitech.sailpilot.routing.*
import com.perseitech.sailpilot.settings.*
import com.perseitech.sailpilot.ui.*

import com.perseitech.sailpilot.io.DataBus
import com.perseitech.sailpilot.io.NavData
import com.perseitech.sailpilot.regatta.RegattaPolars

class MainActivity : ComponentActivity() {

    private fun getLastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        for (p in providers.reversed()) {
            if (
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) return null
            val loc = lm.getLastKnownLocation(p)
            if (loc != null) return loc
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().userAgentValue = "SailPilot/1.0 (osmdroid)"

        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    val ctx = LocalContext.current

                    // Regatta settings (classe + countdown)
                    val regattaRepo = remember { RegattaSettingsRepository(this@MainActivity) }
                    val regatta by regattaRepo.flow.collectAsState()
                    val selectedClass = runCatching {
                        RegattaPolars.ClassId.valueOf(regatta.classId)
                    }.getOrElse { RegattaPolars.ClassId.GENERIC_MONOHULL }
                    val isafCountdownEnabled = regatta.isafCountdownEnabled
                    var showRegattaSettings by remember { mutableStateOf(false) }

                    // -------- Stato rotta / mappa --------
                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

                    // Modalità app
                    var appMode by remember { mutableStateOf(AppMode.NAVIGATION) }
                    val toggleMode = {
                        appMode = if (appMode == AppMode.NAVIGATION) AppMode.REGATTA else AppMode.NAVIGATION
                    }

                    // Tracking / GPS locale
                    var trackingEnabled by remember { mutableStateOf(false) }
                    val controller = remember { LocationServiceController(this) }
                    val lastLoc by LocationService.lastLocation.collectAsState(initial = null)
                    val liveLatLonGps: LatLon? = lastLoc?.let { LatLon(it.latitude, it.longitude) }
                    val sogKnGps: Double? = lastLoc?.speed?.takeIf { it > 0 }?.let { it * 1.9438445 }
                    val cogDegGps: Double? = lastLoc?.bearing?.toDouble()

                    // DataBus (NMEA / Signal K)
                    val nav by DataBus.nav.collectAsState(initial = NavData())
                    val liveLatLon = nav.position ?: liveLatLonGps
                    val sogKn = nav.sogKn ?: sogKnGps
                    val cogDeg = nav.cogDeg ?: cogDegGps
                    val hdgDeg = nav.headingDeg
                    val twsKn = nav.twsKn
                    val twdDeg = nav.twdDeg

                    // Starting line (Regatta Map)
                    var committee by remember { mutableStateOf<LatLon?>(null) }
                    var pin by remember { mutableStateOf<LatLon?>(null) }

                    // Costa / router
                    val landWkt by remember {
                        mutableStateOf(
                            runCatching { assets.open("coast.wkt").bufferedReader().use { it.readText() } }
                                .getOrElse { "" }
                        )
                    }
                    val landIndex by remember(landWkt) { mutableStateOf(WktLandIndex.parse(landWkt)) }
                    val isSea: (LatLon) -> Boolean = { p -> !landIndex.contains(p) }
                    val router = remember { SeaRouter() }

                    val uiScope = rememberCoroutineScope()
                    var isRouting by remember { mutableStateOf(false) }

                    // Boat settings / Ports
                    val boatRepo = remember { BoatSettingsRepository(this@MainActivity) }
                    val boat by boatRepo.flow.collectAsState(initial = BoatSettings())
                    val ports by produceState<List<Port>>(initialValue = emptyList()) {
                        value = PortsRepository.loadPorts(this@MainActivity)
                    }

                    // Dialogs / info porto
                    var showSettings by remember { mutableStateOf(false) }
                    var showSailToPort by remember { mutableStateOf(false) }
                    var startPort by remember { mutableStateOf<Port?>(null) }
                    var goalPort by remember { mutableStateOf<Port?>(null) }
                    var portInfoToShow by remember { mutableStateOf<PortInfo?>(null) }
                    var loadingPortInfo by remember { mutableStateOf(false) }

                    // Velocità stimata
                    var unitMode by remember { mutableStateOf(UnitMode.NAUTICAL) }
                    var speed by remember { mutableStateOf(5.0) }

                    // tack angle dinamico da polari
                    val tackAngle = RegattaPolars.tackAngleDeg(ctx, selectedClass, twsKn)

                    // Permesso GPS
                    val permLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            val loc = getLastKnownLocation()
                            if (loc != null) {
                                start = LatLon(loc.latitude, loc.longitude)
                                pickMode = PickMode.PICK_GOAL
                                Toast.makeText(
                                    this@MainActivity,
                                    "Partenza da GPS impostata. Long-tap per ARRIVO.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this@MainActivity, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this@MainActivity, "Permesso posizione negato.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    // --- Routing helper ---
                    fun recalcRouteAsync(s: LatLon, g: LatLon) {
                        if (landWkt.isBlank()) {
                            Toast.makeText(this, "Coast WKT non valido.", Toast.LENGTH_LONG).show()
                            return
                        }
                        uiScope.launch {
                            isRouting = true
                            val route = withContext(Dispatchers.Default) {
                                val bbox = BBox.around(s, g)
                                router.route(landWkt, s, g, bbox, padMeters = 2000.0, targetCellMeters = 160.0)
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
                        Toast.makeText(this@MainActivity, "Ottimizzazione (stub) eseguita.", Toast.LENGTH_SHORT).show()
                    }

                    // --- Pagine ---
                    val titles = listOf(
                        "Mappa",
                        "Control",
                        "Instruments",
                        "Compass",
                        "Tools",
                        "Connections",
                        "Regatta Map"
                    )
                    val pagerState = rememberPagerState(initialPage = 0, pageCount = { titles.size })
                    val pagerScope = rememberCoroutineScope()
                    fun goTo(title: String) {
                        val i = titles.indexOf(title)
                        if (i >= 0) pagerScope.launch { pagerState.animateScrollToPage(i) }
                    }

                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { i, t ->
                            Tab(
                                selected = pagerState.currentPage == i,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(i) } },
                                text = { Text(t) }
                            )
                        }
                    }

                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                            when (titles[page]) {
                                "Mappa" -> MapScreen(
                                    start = start,
                                    goal = goal,
                                    path = path,
                                    pickMode = pickMode,
                                    isSea = isSea,
                                    liveLocation = liveLatLon,
                                    trackingEnabled = trackingEnabled,
                                    onToggleTracking = {
                                        if (trackingEnabled) {
                                            controller.stop(); trackingEnabled = false
                                        } else {
                                            controller.start(); trackingEnabled = true
                                        }
                                    },
                                    onRequestPickStart = { pickMode = PickMode.PICK_START },
                                    onRequestGpsStart = {
                                        permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    },
                                    onClearRoute = {
                                        start = null; goal = null; path = emptyList(); pickMode = PickMode.NONE
                                        startPort = null; goalPort = null
                                    },
                                    onPointPicked = { p ->
                                        if (!isSea(p)) {
                                            Toast.makeText(
                                                this@MainActivity,
                                                "Seleziona solo punti in MARE.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            return@MapScreen
                                        }
                                        when (pickMode) {
                                            PickMode.PICK_START -> {
                                                start = p; startPort = null
                                                goal = null; goalPort = null
                                                path = emptyList(); pickMode = PickMode.PICK_GOAL
                                            }
                                            PickMode.PICK_GOAL -> {
                                                goal = p; goalPort = null; pickMode = PickMode.NONE
                                                start?.let { recalcRouteAsync(it, p) }
                                            }
                                            else -> {}
                                        }
                                    },
                                    onOptimizeRoute = { optimizeRouteIfAny() },
                                    estSpeedKnots = (if (unitMode == UnitMode.NAUTICAL) speed else speed / 1.852).toInt(),
                                    onChangeSpeed = { newKn ->
                                        speed = if (unitMode == UnitMode.NAUTICAL) newKn.toDouble() else newKn * 1.852
                                    },
                                    isRouting = isRouting,
                                    forceLightBasemap = false,
                                    onToggleForceLight = null,
                                    appMode = appMode,
                                    onToggleMode = { toggleMode() },
                                    onOpenConnections = { goTo("Connections") },
                                    onOpenControlOrRegatta = {
                                        if (appMode == AppMode.NAVIGATION) goTo("Control") else goTo("Regatta Map")
                                    },
                                    onOpenTools = { goTo("Tools") },
                                    onOpenSettings = { showSettings = true },
                                    onOpenSailToPort = { showSailToPort = true },
                                    onOpenRegattaSettings = { showRegattaSettings = true },
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
                                            portInfoToShow = info ?: PortInfo(
                                                title = dest.name,
                                                country = dest.country,
                                                UNLOCODE = dest.unlocode,
                                                description = "Info dettagliate non disponibili.",
                                                website = null,
                                                phone = null,
                                                email = null,
                                                address = null
                                            )
                                        }
                                    }
                                )

                                "Control" -> Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(Color.Black)
                                ) {
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

                                "Instruments" -> {
                                    val latText = liveLatLon?.let {
                                        (if (it.lat >= 0) "N " else "S ") +
                                                String.format("%.5f°", abs(it.lat))
                                    }
                                    val lonText = liveLatLon?.let {
                                        (if (it.lon >= 0) "E " else "W ") +
                                                String.format("%.5f°", abs(it.lon))
                                    }
                                    InstrumentPanel(
                                        headingDeg = hdgDeg ?: cogDeg,
                                        sogKn = sogKn,
                                        cogDeg = cogDeg,
                                        latText = latText,
                                        lonText = lonText,
                                        distNextNm = null,
                                        brgNextDeg = null,
                                        etaNextText = null
                                    )
                                }

                                "Compass" -> {
                                    CompassPanel(
                                        cogDeg = cogDeg,
                                        courseToWpDeg = null,
                                        sogKn = sogKn,
                                        etaText = null
                                    )
                                }

                                "Tools" -> ToolsPage(
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

                                "Connections" -> ConnectionsPanel(appScope = uiScope)

                                "Regatta Map" -> {
                                    RegattaMapPage(
                                        live = liveLatLon,
                                        sogKn = sogKn,
                                        twdDeg = twdDeg,
                                        tackAngleDeg = tackAngle,
                                        cogDeg = cogDeg,
                                        hdgDeg = hdgDeg,
                                        committee = committee,
                                        pin = pin,
                                        isafCountdownEnabled = isafCountdownEnabled,
                                        onSetCommitteeFromGps = { liveLatLon?.let { committee = it } },
                                        onSetPinFromGps = { liveLatLon?.let { pin = it } },
                                        onClearLine = { committee = null; pin = null }
                                    )
                                }
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

                    // --- Dialog IMPOSTAZIONI ---
                    if (showSettings) {
                        SettingsDialog(
                            draftMInit = boat.draftM,
                            lengthMInit = boat.lengthM,
                            beamMInit = boat.beamM,
                            seaRatesApiKeyInit = boat.seaRatesApiKey,
                            onDismiss = { showSettings = false },
                            onSave = { d, l, b, k ->
                                uiScope.launch {
                                    boatRepo.saveDraft(d)
                                    boatRepo.saveLength(l)
                                    boatRepo.saveBeam(b)
                                    boatRepo.saveApiKey(k)
                                    showSettings = false
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Impostazioni salvate.",
                                        Toast.LENGTH_SHORT
                                    ).show()
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
                                    Toast.makeText(
                                        this@MainActivity,
                                        "Posizione GPS non disponibile.",
                                        Toast.LENGTH_LONG
                                    ).show()
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

                    // --- Dialog Regatta Settings ---
                    if (showRegattaSettings) {
                        RegattaSettingsDialog(
                            currentClass = selectedClass,
                            isafCountdownEnabledInit = isafCountdownEnabled,
                            onDismiss = { showRegattaSettings = false },
                            onSave = { newClass, enabled ->
                                regattaRepo.saveClassId(newClass.name)
                                regattaRepo.saveIsafCountdownEnabled(enabled)
                                showRegattaSettings = false
                            }
                        )
                    }

                    // --- INFO porto ---
                    portInfoToShow?.let { p ->
                        AlertDialog(
                            onDismissRequest = { portInfoToShow = null },
                            confirmButton = {
                                TextButton(onClick = { portInfoToShow = null }) {
                                    Text("OK")
                                }
                            },
                            title = { Text("Port Info — ${p.title}") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    if (loadingPortInfo) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    p.UNLOCODE?.let { Text("UNLOCODE: $it") }
                                    p.country?.let { if (it.isNotBlank()) Text("Paese: $it") }
                                    p.address?.let { if (it.isNotBlank()) Text("Indirizzo: $it") }
                                    p.phone?.let { if (it.isNotBlank()) Text("Tel: $it") }
                                    p.email?.let { if (it.isNotBlank()) Text("Email: $it") }
                                    p.website?.let { if (it.isNotBlank()) Text("Web: $it") }
                                    p.description?.let { if (it.isNotBlank()) Text(it) }
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
