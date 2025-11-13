package com.perseitech.sailpilot.io

import com.perseitech.sailpilot.routing.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.abs

/**
 * Semplice “event bus” in memoria con StateFlow.
 * Espone helper per aggiornare i singoli campi.
 */
object DataBus {
    private val _nav = MutableStateFlow(NavData())
    val nav: StateFlow<NavData> = _nav

    private val mu = Mutex()

    suspend fun update(block: (NavData) -> NavData) = mu.withLock {
        _nav.value = block(_nav.value)
    }

    // Helper comodi (non-suspend per semplicità dove non hai scope)
    fun updatePosition(p: LatLon?) { _nav.value = _nav.value.copy(position = p) }
    fun updateCogSog(cogDeg: Double? = _nav.value.cogDeg, sogKn: Double? = _nav.value.sogKn) {
        _nav.value = _nav.value.copy(cogDeg = cogDeg, sogKn = sogKn)
    }
    fun updateHeading(hdgDeg: Double?) { _nav.value = _nav.value.copy(headingDeg = hdgDeg) }
    fun updateTrueWind(twsKn: Double? = _nav.value.twsKn, twdDeg: Double? = _nav.value.twdDeg, twaDeg: Double? = _nav.value.twaDeg) {
        _nav.value = _nav.value.copy(twsKn = twsKn, twdDeg = twdDeg, twaDeg = twaDeg)
    }
    fun updateDepth(depthM: Double?) { _nav.value = _nav.value.copy(depthM = depthM) }

    // Merge regole semplici: preferisci valori “più freschi e non-null”.
    fun mergeCogSog(cog: Double?, sog: Double?) = updateCogSog(cog ?: _nav.value.cogDeg, sog ?: _nav.value.sogKn)
    fun mergeHeading(hdg: Double?) = updateHeading(hdg ?: _nav.value.headingDeg)
    fun mergeTrueWind(tws: Double?, twd: Double?, twa: Double?) = updateTrueWind(
        tws ?: _nav.value.twsKn,
        twd ?: _nav.value.twdDeg,
        twa ?: _nav.value.twaDeg
    )
    fun mergeDepth(m: Double?) = updateDepth(m ?: _nav.value.depthM)
}
