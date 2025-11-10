package com.perseitech.sailpilot.routing

/** Singleton opzionale per compatibilità con vecchio codice che usava `cfg`. */
object RoutingGlobals {
    lateinit var cfg: GridConfig

    val isReady: Boolean
        get() = ::cfg.isInitialized
}
