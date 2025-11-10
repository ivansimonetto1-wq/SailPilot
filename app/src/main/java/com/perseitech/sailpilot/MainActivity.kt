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
import com.perseitech.sailpilot.routing.LatLon
import com.perseitech.sailpilot.ui.MapScreen
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {

    private fun getLastKnownLocation(): Location? {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        val providers = lm.getProviders(true)
        for (p in providers.reversed()) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return null
            }
            val loc = lm.getLastKnownLocation(p)
            if (loc != null) return loc
        }
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Necessario per osmdroid
        Configuration.getInstance().userAgentValue = "SailPilot/1.0 (osmdroid)"

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {

                    var start by remember { mutableStateOf<LatLon?>(null) }
                    var goal by remember { mutableStateOf<LatLon?>(null) }
                    var path by remember { mutableStateOf<List<LatLon>>(emptyList()) }
                    var pickMode by remember { mutableStateOf(PickMode.NONE) }

                    // TODO: rimpiazza con il vero controllo mare (LandMask.isSea)
                    val isSea: (LatLon) -> Boolean = { _ -> true }

                    val permLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        if (granted) {
                            val loc = getLastKnownLocation()
                            if (loc != null) {
                                start = LatLon(loc.latitude, loc.longitude)
                                // dopo aver messo la partenza, si chiede l'arrivo
                                pickMode = PickMode.PICK_GOAL
                                Toast.makeText(this, "Partenza da GPS impostata. Long-tap: seleziona ARRIVO (solo mare).", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "GPS non disponibile.", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            Toast.makeText(this, "Permesso posizione negato.", Toast.LENGTH_SHORT).show()
                        }
                    }

                    MapScreen(
                        start = start,             // LatLon?
                        goal  = goal,              // LatLon?
                        path  = path,              // List<LatLon>
                        pickMode = pickMode,
                        isSea = isSea,             // (LatLon) -> Boolean
                        onRequestPickStart = {
                            // Attiva modalità scelta partenza
                            goal = null
                            path = emptyList()
                            pickMode = PickMode.PICK_START
                            Toast.makeText(this, "Long-tap per scegliere la PARTENZA (solo mare).", Toast.LENGTH_LONG).show()
                        },
                        onRequestGpsStart = {
                            // Prova ad usare la posizione corrente come partenza
                            when {
                                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED -> {
                                    val loc = getLastKnownLocation()
                                    if (loc != null) {
                                        start = LatLon(loc.latitude, loc.longitude)
                                        pickMode = PickMode.PICK_GOAL
                                        Toast.makeText(this, "Partenza da GPS impostata. Long-tap: seleziona ARRIVO (solo mare).", Toast.LENGTH_LONG).show()
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
                            when (pickMode) {
                                PickMode.PICK_START -> {
                                    // Verifica mare se vuoi anche qui (isSea(picked))
                                    start = picked
                                    goal = null
                                    path = emptyList()
                                    pickMode = PickMode.PICK_GOAL
                                    Toast.makeText(this, "Partenza impostata. Long-tap: seleziona ARRIVO (solo mare).", Toast.LENGTH_LONG).show()
                                }
                                PickMode.PICK_GOAL -> {
                                    // Verifica mare se vuoi anche qui (isSea(picked))
                                    goal = picked
                                    pickMode = PickMode.NONE

                                    // DEMO: rotta retta. Sostituisci con SeaRouter.route(...)
                                    val s = start
                                    val g = goal
                                    if (s != null && g != null) {
                                        path = listOf(s, g)
                                        Toast.makeText(this, "Arrivo impostato. Rotta creata (demo).", Toast.LENGTH_SHORT).show()
                                    }
                                }
                                else -> Unit
                            }
                        }
                    )
                }
            }
        }
    }
}

enum class PickMode { NONE, PICK_START, PICK_GOAL }
