package com.perseitech.sailpilot.io.nmea

import com.perseitech.sailpilot.routing.LatLon
import kotlin.math.abs

/**
 * Parser minimale per le frasi usate più spesso.
 * Non verifica checksum (si può aggiungere facilmente).
 */
object Nmea0183Parser {

    fun parse(line: String): Result {
        if (!line.startsWith("$")) return Result()
        val star = line.indexOf('*')
        val body = if (star > 0) line.substring(1, star) else line.substring(1)
        val f = body.split(',')
        val talker = body.take(2)
        val type = body.dropWhile { it != ',' }.let { if (it.isNotEmpty()) f.firstOrNull()?.takeLast(3) else null } // grezzo

        // Meglio: controlla prefissi del body
        return when {
            body.contains("GGA") -> parseGGA(f)
            body.contains("GLL") -> parseGLL(f)
            body.contains("RMC") -> parseRMC(f)
            body.contains("VTG") -> parseVTG(f)
            body.contains("HDT") -> parseHDT(f)
            body.contains("HDG") -> parseHDG(f)
            body.contains("MWV") -> parseMWV(f)
            body.contains("DBT") -> parseDBT(f)
            else -> Result()
        }
    }

    data class Result(
        val position: LatLon? = null,
        val cogDeg: Double? = null,
        val sogKn: Double? = null,
        val headingDeg: Double? = null,
        val twsKn: Double? = null,
        val twdDeg: Double? = null,
        val twaDeg: Double? = null,
        val depthM: Double? = null
    )

    private fun dmmToDeg(dmm: String, hemi: String?): Double? {
        // ddmm.mmmm or dddmm.mmmm
        if (dmm.isBlank()) return null
        val dot = dmm.indexOf('.')
        val mStart = (dot - 2).coerceAtLeast(0)
        val deg = dmm.substring(0, mStart).toIntOrNull() ?: return null
        val min = dmm.substring(mStart).toDoubleOrNull() ?: return null
        var out = deg + (min / 60.0)
        if (hemi != null && (hemi.equals("S", true) || hemi.equals("W", true))) out = -out
        return out
    }

    private fun parseRMC(f: List<String>) = Result(
        position = runCatching {
            val lat = dmmToDeg(f.getOrNull(3) ?: "", f.getOrNull(4))
            val lon = dmmToDeg(f.getOrNull(5) ?: "", f.getOrNull(6))
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }.getOrNull(),
        sogKn = f.getOrNull(7)?.toDoubleOrNull(),
        cogDeg = f.getOrNull(8)?.toDoubleOrNull()
    )

    private fun parseVTG(f: List<String>) = Result(
        cogDeg = f.getOrNull(1)?.toDoubleOrNull(),
        sogKn = f.getOrNull(7)?.toDoubleOrNull()
    )

    private fun parseGGA(f: List<String>) = Result(
        position = runCatching {
            val lat = dmmToDeg(f.getOrNull(2) ?: "", f.getOrNull(3))
            val lon = dmmToDeg(f.getOrNull(4) ?: "", f.getOrNull(5))
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }.getOrNull()
    )

    private fun parseGLL(f: List<String>) = Result(
        position = runCatching {
            val lat = dmmToDeg(f.getOrNull(1) ?: "", f.getOrNull(2))
            val lon = dmmToDeg(f.getOrNull(3) ?: "", f.getOrNull(4))
            if (lat != null && lon != null) LatLon(lat, lon) else null
        }.getOrNull()
    )

    private fun parseHDT(f: List<String>) = Result(
        headingDeg = f.getOrNull(1)?.toDoubleOrNull()
    )

    private fun parseHDG(f: List<String>) = Result(
        headingDeg = f.getOrNull(1)?.toDoubleOrNull()
    )

    private fun parseMWV(f: List<String>): Result {
        // $--MWV,angle,ref,speed,units,status
        val ang = f.getOrNull(1)?.toDoubleOrNull()
        val ref = f.getOrNull(2)        // R=relative (apparent), T=true
        val spd = f.getOrNull(3)?.toDoubleOrNull()
        val units = f.getOrNull(4)
        val status = f.getOrNull(5)
        val ok = status.equals("A", true) || status.isNullOrBlank()

        if (!ok) return Result()

        val speedKn = when (units?.uppercase()) {
            "N" -> spd
            "M" -> spd?.times(1.943844) // m/s -> kn
            "K" -> spd?.times(0.539957) // km/h -> kn
            else -> spd
        }

        return if (ref.equals("T", true))
            Result(twsKn = speedKn, twdDeg = ang)
        else
            Result(twaDeg = ang, twsKn = speedKn)  // relativo
    }

    private fun parseDBT(f: List<String>): Result {
        // $--DBT,feet,f,M,meters,m,F,fathoms,f
        val meters = f.getOrNull(3)?.toDoubleOrNull()
        return Result(depthM = meters)
    }
}
