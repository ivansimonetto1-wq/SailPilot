package com.perseitech.sailpilot

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import org.osmdroid.config.Configuration
import com.perseitech.sailpilot.routing.*
import com.perseitech.sailpilot.ui.MapScreen

class MainActivity : ComponentActivity() {

    private fun getLastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        for (p in providers.reversed()) {
            if (
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
            ) {
                return null
            }
            val loc = lm.getLastKnownLocation(p)
            if (loc != null) return loc
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // osmdroid user agent
        Configuration.getInstance().userAgentValue = "SailPilot/1.0 (osmdroid)"

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // --- STATE ---
                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

                    // Carica WKT una sola volta
                    val landWkt by remember {
                        mutableStateOf(
                            runCatching {
                                assets.open("coast.wkt").bufferedReader().use { it.readText() }
                            }.getOrElse { "" }
                        )
                    }

                    // Indice WKT per test puntuale (vincolo "solo mare" al tap)
                    val landIndex by remember(landWkt) { mutableStateOf(WktLandIndex.parse(landWkt)) }

                    // Lambda: consentire tap SOLO in mare
                    val isSea: (LatLon) -> Boolean = { p -> !landIndex.contains(p) }

                    // Router per il calcolo rotta (A* su griglia mare)
                    val router = remember { SeaRouter() }

                    // Launcher per permission GPS
                    val permLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            val loc = getLastKnownLocation()
                            if (loc != null) {
                                start = LatLon(loc.latitude, loc.longitude)
                                pickMode = PickMode.PICK_GOAL
                                Toast.makeText(
                                    this,
                                    "Partenza da GPS impostata. Long-tap: seleziona ARRIVO (solo mare).",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                Toast.makeText(this, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Permesso posizione negato.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    MapScreen(
                        start = start,
                        goal = goal,
                        path = path,
                        pickMode = pickMode,
                        isSea = isSea,
                        onRequestPickStart = {
                            pickMode = PickMode.PICK_START
                            Toast.makeText(
                                this,
                                "Long-tap per scegliere la PARTENZA (solo mare).",
                                Toast.LENGTH_LONG
                            ).show()
                        },
                        onRequestGpsStart = {
                            when {
                                ActivityCompat.checkSelfPermission(
                                    this,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED -> {
                                    val loc = getLastKnownLocation()
                                    if (loc != null) {
                                        start = LatLon(loc.latitude, loc.longitude)
                                        goal = null
                                        path = emptyList()
                                        pickMode = PickMode.PICK_GOAL
                                        Toast.makeText(
                                            this,
                                            "Partenza da GPS impostata. Long-tap: seleziona ARRIVO (solo mare).",
                                            Toast.LENGTH_LONG
                                        ).show()
                                    } else {
                                        Toast.makeText(this, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                        onClearRoute = {
                            start = null
                            goal = null
                            path = emptyList()
                            pickMode = PickMode.NONE
                        },
                        onPointPicked = { picked ->
                            // Blocca i tap su terra
                            if (!isSea(picked)) {
                                Toast.makeText(this, "Seleziona solo punti in MARE.", Toast.LENGTH_SHORT).show()
                                return@MapScreen
                            }
                            when (pickMode) {
                                PickMode.PICK_START -> {
                                    start = picked
                                    goal = null
                                    path = emptyList()
                                    pickMode = PickMode.PICK_GOAL
                                    Toast.makeText(
                                        this,
                                        "Partenza impostata. Long-tap: seleziona ARRIVO (solo mare).",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                                PickMode.PICK_GOAL -> {
                                    goal = picked
                                    pickMode = PickMode.NONE
                                    val s = start
                                    val g = goal
                                    if (s != null && g != null) {
                                        // Costruisci bbox attorno ai due punti
                                        val bbox = BBox.around(s, g)
                                        // Calcola rotta evitando terra
                                        val route = router.route(
                                            landWkt = landWkt,
                                            start = s,
                                            goal = g,
                                            bbox = bbox,
                                            padMeters = 800.0,       // aumenta se zona stretta
                                            targetCellMeters = 180.0 // più piccolo = più preciso
                                        )
                                        if (route == null) {
                                            Toast.makeText(
                                                this,
                                                "Nessun percorso marino trovato (allarga pad o riduci cella).",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        } else {
                                            path = route
                                            Toast.makeText(
                                                this,
                                                "Rotta calcolata evitando la terra.",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                }
                                else -> {
                                    // fuori modalità di pick: ignora
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class PickMode { NONE, PICK_START, PICK_GOAL }
