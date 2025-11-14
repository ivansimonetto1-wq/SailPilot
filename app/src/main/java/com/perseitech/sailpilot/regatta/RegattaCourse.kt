package com.perseitech.sailpilot.regatta

import com.perseitech.sailpilot.routing.LatLon

/**
 * Descrive il campo di regata:
 *  - committee: barca comitato (inizio linea di partenza)
 *  - pin: fine linea di partenza
 *  - marks: boe intermedie in ordine di passaggio
 */
data class RegattaCourse(
    val committee: LatLon? = null,
    val pin: LatLon? = null,
    val marks: List<LatLon> = emptyList()
)
