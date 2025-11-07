package com.perseitech.sailpilot.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.perseitech.sailpilot.R

class LocationService : Service() {

    private lateinit var fused: FusedLocationProviderClient
    private lateinit var request: LocationRequest

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    companion object {
        private const val CHANNEL_ID = "sailpilot.location"
        private const val NOTIF_ID = 101
        private val _lastLocation = MutableStateFlow<Location?>(null)
        /** Leggilo da qualsiasi punto dell’app */
        val lastLocation = _lastLocation.asStateFlow()
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(NOTIF_ID, notification("Avvio localizzazione…"))

        fused = LocationServices.getFusedLocationProviderClient(this)
        request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L)
            .setMinUpdateIntervalMillis(750L)
            .setMinUpdateDistanceMeters(1f)
            .build()

        fused.requestLocationUpdates(request, callback, mainLooper)
    }

    private val callback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            _lastLocation.value = loc
            val txt = "Lat ${"%.5f".format(loc.latitude)}, Lon ${"%.5f".format(loc.longitude)}, SOG ${"%.1f".format(loc.speed * 1.94384)} kn"
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .notify(NOTIF_ID, notification(txt))
        }
    }

    override fun onDestroy() {
        fused.removeLocationUpdates(callback)
        scope.cancel()
        super.onDestroy()
    }

    private fun notification(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("SailPilot — Tracking")
            .setContentText(text)
            .setOngoing(true)
            .build()

    private fun createChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Location", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
