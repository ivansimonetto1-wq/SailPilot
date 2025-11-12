package com.perseitech.sailpilot.io

import com.perseitech.sailpilot.routing.LatLon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlin.math.max

/**
 * Bus globale con MERGE per sorgente.
 * Ogni campo conserva: valore, priorità sorgente e timestamp.
 *
 * Priorità (più alto vince):
 *  - SIGNAL_K = 3
 *  - NMEA_0183 = 2
 *  - ANDROID_GPS = 1
 */
object DataBus {

    enum class Source(val prio: Int) { ANDROID_GPS(1), NMEA_0183(2), SIGNAL_K(3) }

    data class NavData(
        val timeMillis: Long = System.currentTimeMillis(),
        val position: LatLon? = null,   // lat/lon
        val sogKn: Double? = null,      // Speed Over Ground [kn]
        val cogDeg: Double? = null,     // Course Over Ground [deg]
        val headingDeg: Double? = null, // Compass heading (mag/true) [deg]
        val awaDeg: Double? = null,     // Apparent Wind Angle [deg]
        val awsKn: Double? = null,      // Apparent Wind Speed [kn]
        val depthM: Double? = null      // Depth below transducer [m]
    )

    private data class Slot<T>(var value: T?, var prio: Int, var t: Long)
    private val pos = Slot<LatLon>(null, 0, 0L)
    private val sog = Slot<Double>(null, 0, 0L)
    private val cog = Slot<Double>(null, 0, 0L)
    private val hdg = Slot<Double>(null, 0, 0L)
    private val awa = Slot<Double>(null, 0, 0L)
    private val aws = Slot<Double>(null, 0, 0L)
    private val dep = Slot<Double>(null, 0, 0L)

    private val _nav = MutableStateFlow(NavData())
    val nav: StateFlow<NavData> = _nav

    /** Applica un parziale con priorità della sorgente. */
    fun apply(source: Source, partial: NavData) {
        val p = source.prio
        val t = partial.timeMillis

        fun <T> set(slot: Slot<T>, v: T?) {
            if (v == null) return
            if (p > slot.prio || (p == slot.prio && t >= slot.t)) {
                slot.value = v; slot.prio = p; slot.t = t
            }
        }

        set(pos, partial.position)
        set(sog, partial.sogKn)
        set(cog, partial.cogDeg)
        set(hdg, partial.headingDeg)
        set(awa, partial.awaDeg)
        set(aws, partial.awsKn)
        set(dep, partial.depthM)

        _nav.update {
            NavData(
                timeMillis = max(it.timeMillis, t),
                position   = pos.value,
                sogKn      = sog.value,
                cogDeg     = cog.value,
                headingDeg = hdg.value,
                awaDeg     = awa.value,
                awsKn      = aws.value,
                depthM     = dep.value
            )
        }
    }

    /** Comodo per chi non specifica: assume NMEA_0183. */
    fun apply(partial: NavData) = apply(Source.NMEA_0183, partial)

    /** Reset (opzionale). */
    fun clear() {
        listOf(pos, sog, cog, hdg, awa, aws, dep).forEach { it.value = null; it.prio = 0; it.t = 0L }
        _nav.value = NavData()
    }
}
