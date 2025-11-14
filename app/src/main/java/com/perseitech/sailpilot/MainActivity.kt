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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.perseitech.sailpilot.weather.OpenMeteoClient
import com.perseitech.sailpilot.ui.WeatherSnapshot

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
            val ctx = LocalContext.current

            // ---- THEME ----
            val appUiRepo = remember { AppUiSettingsRepository(this@MainActivity) }
            val appUi by appUiRepo.flow.collectAsState(initial = AppUiSettings())

            val colorScheme = if (appUi.darkTheme) {
                val primary = when (appUi.nightAccent) {
                    NightAccent.RED   -> Color(0xFFFF4444)
                    NightAccent.GREEN -> Color(0xFF00E676)
                }
                darkColorScheme(
                    primary = primary,
                    onPrimary = Color.Black,
                    secondary = Color(0xFF90CAF9),
                    background = Color(0xFF000814),
                    surface = Color(0xFF001322)
                )
            } else {
                lightColorScheme(
                    primary = Color(0xFF01579B),
                    secondary = Color(0xFF00BCD4),
                    background = Color(0xFFE0F7FA),
                    surface = Color(0xFFFFFFFF)
                )
            }

            MaterialTheme(colorScheme = colorScheme) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    // ---- REGATTA SETTINGS ----
                    val regattaRepo = remember { RegattaSettingsRepository(this@MainActivity) }
                    val regatta by regattaRepo.flow.collectAsState()
                    val selectedClass = runCatching {
                        RegattaPolars.ClassId.valueOf(regatta.classId)
                    }.getOrElse { RegattaPolars.ClassId.GENERIC_MONOHULL }
                    val isafCountdownEnabled = regatta.isafCountdownEnabled
                    var showRegattaSettings by remember { mutableStateOf(false) }

                    // ---- MODE SELECTION (splash) ----
                    var hasSelectedMode by rememberSaveable { mutableStateOf(false) }
                    var appMode by remember { mutableStateOf(AppMode.NAVIGATION) }
                    val toggleMode = {
                        appMode = if (appMode == AppMode.NAVIGATION) AppMode.REGATTA else AppMode.NAVIGATION
                    }

                    if (!hasSelectedMode) {
                        ModeSelectionScreen(
                            onSelectNavigation = {
                                appMode = AppMode.NAVIGATION
                                hasSelectedMode = true
                            },
                            onSelectRegatta = {
                                appMode = AppMode.REGATTA
                                hasSelectedMode = true
                            }
                        )
                        return@Surface
                    }

                    // -------- ROUTE / MAP STATE --------
                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

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
                    val twaDeg = nav.twaDeg

                    // Starting line
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

                    // Dialogs / info porto / tema
                    var showSettings by remember { mutableStateOf(false) }
                    var showSailToPort by remember { mutableStateOf(false) }
                    var showAppTheme by remember { mutableStateOf(false) }
                    var startPort by remember { mutableStateOf<Port?>(null) }
                    var goalPort by remember { mutableStateOf<Port?>(null) }
                    var portInfoToShow by remember { mutableStateOf<PortInfo?>(null) }
                    var loadingPortInfo by remember { mutableStateOf(false) }

                    // Velocità stimata
                    var unitMode by remember { mutableStateOf(UnitMode.NAUTICAL) }
                    var speed by remember { mutableStateOf(5.0) }

                    // tack angle dinamico da polari
                    val tackAngle = RegattaPolars.tackAngleDeg(ctx, selectedClass, twsKn)

                    // ---- WEATHER FALLBACK (Open-Meteo) ----
                    var externalWeather by remember { mutableStateOf<WeatherSnapshot?>(null) }
                    var externalWeatherLoading by remember { mutableStateOf(false) }

                    LaunchedEffect(liveLatLon, twsKn, twdDeg) {
                        if ((twsKn == null || twdDeg == null) &&
                            !externalWeatherLoading &&
                            externalWeather == null
                        ) {
                            val pos: LatLon? = liveLatLon
                                ?: getLastKnownLocation()?.let { loc ->
                                    LatLon(loc.latitude, loc.longitude)
                                }

                            if (pos != null) {
                                externalWeatherLoading = true
                                val snap = withContext(Dispatchers.IO) {
                                    OpenMeteoClient.fetchSnapshot(pos.lat, pos.lon)
                                }
                                externalWeather = snap
                                externalWeatherLoading = false
                            }
                        }
                    }

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

                    // --- Weather snapshot per pannello Meteo ---
                    val weatherSnapshot: WeatherSnapshot =
                        if (twsKn != null || twdDeg != null) {
                            WeatherSnapshot(
                                twsKn = twsKn,
                                twdDeg = twdDeg,
                                sogKn = sogKn,
                                cogDeg = cogDeg,
                                seaState = null,
                                tide = null,
                                providerName = "Onboard instruments",
                                isForecast = false
                            )
                        } else {
                            externalWeather?.copy(
                                sogKn = sogKn ?: externalWeather?.sogKn,
                                cogDeg = cogDeg ?: externalWeather?.cogDeg
                            ) ?: WeatherSnapshot(
                                twsKn = null,
                                twdDeg = null,
                                sogKn = sogKn,
                                cogDeg = cogDeg,
                                seaState = null,
                                tide = null,
                                providerName = "Nessun dato meteo",
                                isForecast = false
                            )
                        }

                    // ---- PAGINE (ordine diverso per Navigation / Regatta) ----
                    val titles = if (appMode == AppMode.NAVIGATION) {
                        listOf(
                            "Mappa",
                            "Control",
                            "Instruments",
                            "Weather",
                            "Compass",
                            "Tools",
                            "Connections",
                            "Regatta Map"
                        )
                    } else {
                        listOf(
                            "Regatta",
                            "Weather",
                            "Instruments",
                            "Compass",
                            "Tools",
                            "Connections",
                            "Regatta Map",
                            "Mappa"
                        )
                    }

                    // **FIX**: usare direttamente rememberPagerState (non dentro remember)
                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        pageCount = { titles.size }
                    )
                    val pagerScope = rememberCoroutineScope()
                    fun goTo(title: String) {
                        val i = titles.indexOf(title)
                        if (i >= 0) pagerScope.launch { pagerState.animateScrollToPage(i) }
                    }

                    // ---- TAB in alto ----
                    TabRow(selectedTabIndex = pagerState.currentPage) {
                        titles.forEachIndexed { i, t ->
                            Tab(
                                selected = pagerState.currentPage == i,
                                onClick = { pagerScope.launch { pagerState.animateScrollToPage(i) } },
                                text = { Text(t) }
                            )
                        }
                    }

                    // =================== CONTENUTO PAGINE ===================
                    Box(Modifier.fillMaxSize()) {
                        HorizontalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize(),
                            userScrollEnabled = false   // niente swipe orizzontale: mappa libera
                        ) { page ->
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
                                        if (appMode == AppMode.NAVIGATION) goTo("Control") else goTo("Regatta")
                                    },
                                    onOpenTools = { goTo("Tools") },
                                    onOpenSettings = { showSettings = true },
                                    onOpenSailToPort = { showSailToPort = true },
                                    onOpenRegattaSettings = { showRegattaSettings = true },
                                    onOpenAppTheme = { showAppTheme = true },
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

                                "Weather" -> {
                                    WeatherPanel(
                                        modeLabel = if (appMode == AppMode.NAVIGATION) "Navigation" else "Regatta",
                                        snapshot = weatherSnapshot
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

                                "Regatta" -> {
                                    RegattaOverviewPanel(
                                        sogKn = sogKn,
                                        cogDeg = cogDeg,
                                        twsKn = weatherSnapshot.twsKn,
                                        twdDeg = weatherSnapshot.twdDeg,
                                        twaDeg = twaDeg,
                                        // **FIX**: niente displayName, uso name “pulito”
                                        classLabel = selectedClass.name.replace('_', ' '),
                                        onOpenRegattaMap = { goTo("Regatta Map") },
                                        onOpenSailAdvisor = { /* futuro */ }
                                    )
                                }

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

                        // ---------- NAV DOTS + FRECCE ----------
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            IconButton(
                                onClick = {
                                    val current = pagerState.currentPage
                                    if (current > 0) {
                                        pagerScope.launch {
                                            pagerState.animateScrollToPage(current - 1)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronLeft,
                                    contentDescription = "Previous page"
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
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
                                            .clickable {
                                                pagerScope.launch {
                                                    pagerState.animateScrollToPage(i)
                                                }
                                            }
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    val current = pagerState.currentPage
                                    val maxIndex = titles.lastIndex
                                    if (current < maxIndex) {
                                        pagerScope.launch {
                                            pagerState.animateScrollToPage(current + 1)
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.ChevronRight,
                                    contentDescription = "Next page"
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

                    // --- Dialog IMPOSTAZIONI BARCA ---
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

                    // --- Dialog TEMA APP ---
                    if (showAppTheme) {
                        AppThemeDialog(
                            current = appUi,
                            onDismiss = { showAppTheme = false },
                            onSave = { dark, accent ->
                                appUiRepo.saveDarkTheme(dark)
                                appUiRepo.saveNightAccent(accent)
                                showAppTheme = false
                            }
                        )
                    }

                    // --- Dialog REGATTA SETTINGS ---
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

                    // --- INFO PORTO ---
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
