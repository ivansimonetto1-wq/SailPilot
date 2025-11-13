package com.perseitech.sailpilot.io

import com.perseitech.sailpilot.routing.LatLon

data class NavData(
    val position: LatLon? = null,     // GPS o altra fonte
    val cogDeg: Double? = null,       // Course over ground
    val sogKn: Double? = null,        // Speed over ground (kn)
    val headingDeg: Double? = null,   // Heading (mag/true a seconda della fonte)
    val twsKn: Double? = null,        // True Wind Speed
    val twdDeg: Double? = null,       // True Wind Direction (° true)
    val twaDeg: Double? = null,       // True Wind Angle (relativo prua, +dx / -sx)
    val depthM: Double? = null        // Profondità (metri)
)
