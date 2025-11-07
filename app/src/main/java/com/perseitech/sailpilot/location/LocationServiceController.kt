package com.perseitech.sailpilot.location

import android.content.Context
import android.content.Intent
import android.os.Build

class LocationServiceController(private val ctx: Context) {
    fun start() {
        val i = Intent(ctx, LocationService::class.java)
        if (Build.VERSION.SDK_INT >= 26) ctx.startForegroundService(i) else ctx.startService(i)
    }
    fun stop() {
        ctx.stopService(Intent(ctx, LocationService::class.java))
    }
}
