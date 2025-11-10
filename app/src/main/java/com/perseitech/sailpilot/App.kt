package com.perseitech.sailpilot

import android.app.Application
import org.osmdroid.config.Configuration

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Configuration.getInstance().userAgentValue = "SailPilot/1.0 (osmdroid)"
    }
}
