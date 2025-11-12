package com.perseitech.sailpilot.io.nmea

import com.perseitech.sailpilot.io.DataBus
import com.perseitech.sailpilot.io.DataBus.Source
import com.perseitech.sailpilot.io.NavData
import com.perseitech.sailpilot.routing.LatLon

/**
 * Parser essenziale NMEA0183.
 * Frasi supportate:
 *  - RMC: pos, sog, cog
 *  - GLL: pos
 *  - HDG/HDM/HDT: heading
 *  - VHW: sog (speed water; usata come fallback)
 *  - MWV: vento apparente (angolo/speed)
 *  - DBT: profondità
 *  - XTE: cross-track error (non pubblicato nel bus NavData per ora)
 *  - RMB: distanza/rotta WP (qui non pubblichiamo; la rotta app già la conosce)
 */
object Nmea0183Parser {

    fun feed(lineRaw: String) {
        val line = lineRaw.trim().removeSuffix("\r")
        if (line.isEmpty()) return
        if (!line.startsWith("\$") && !line.startsWith("!")) return

        val star = line.lastIndexOf('*')
        val body = if (star > 0) line.substring(1, star) else line.substring(1)
        val fields = body.split(',')
        if (fields.isEmpty()) return

        val talkerType = fields[0]          // es. GPRMC, IIMWV, HCHDG
        val type = talkerType.takeLast(3).uppercase()

        when (type) {
            "RMC" -> parseRMC(fields)
            "GLL" -> parseGLL(fields)
            "HDG", "HDM", "HDT" -> parseHDx(fields, type)
            "VHW" -> parseVHW(fields)
            "MWV" -> parseMWV(fields)
            "DBT" -> parseDBT(fields)
            "XTE" -> parseXTE(fields) // (tenuto per future UI)
            "RMB" -> parseRMB(fields) // (tenuto per future UI)
        }
    }

    private fun parseRMC(f: List<String>) {
        // $--RMC,hhmmss.sss,A,llll.ll,a,yyyyy.yy,a,x.x,x.x,ddmmyy,x.x,a*hh
        if (f.size < 12) return
        val status = f[2]
        if (status != "A") return
        val lat = parseLat(f.getOrNull(3), f.getOrNull(4)) ?: return
        val lon = parseLon(f.getOrNull(5), f.getOrNull(6)) ?: return
        val sogKn = f.getOrNull(7)?.toDoubleOrNull()
        val cogDeg = f.getOrNull(8)?.toDoubleOrNull()
        DataBus.apply(Source.NMEA_0183, NavData(position = LatLon(lat, lon), sogKn = sogKn, cogDeg = cogDeg))
    }

    private fun parseGLL(f: List<String>) {
        // $--GLL,llll.ll,a,yyyyy.yy,a,hhmmss.ss,A
        if (f.size < 7) return
        val status = f.getOrNull(6)
        if (status != "A") return
        val lat = parseLat(f.getOrNull(1), f.getOrNull(2)) ?: return
        val lon = parseLon(f.getOrNull(3), f.getOrNull(4)) ?: return
        DataBus.apply(Source.NMEA_0183, NavData(position = LatLon(lat, lon)))
    }

    private fun parseHDx(f: List<String>, type: String) {
        // HDG: $--HDG,x.x, ... | HDM: $--HDM,x.x | HDT: $--HDT,x.x,T
        val v = f.getOrNull(1)?.toDoubleOrNull() ?: return
        DataBus.apply(Source.NMEA_0183, NavData(headingDeg = v))
    }

    private fun parseVHW(f: List<String>) {
        // $--VHW,x.x,T,x.x,M,x.x,N,x.x,K
        val sogKn = f.getOrNull(7)?.toDoubleOrNull()
        if (sogKn != null) DataBus.apply(Source.NMEA_0183, NavData(sogKn = sogKn))
    }

    private fun parseMWV(f: List<String>) {
        // $--MWV,angle,ref,speed,unit,status
        val angle = f.getOrNull(1)?.toDoubleOrNull()
        val speed = f.getOrNull(3)?.toDoubleOrNull()
        val unit = f.getOrNull(4)
        val status = f.getOrNull(5)
        if (status != "A") return
        val awsKn = when (unit) {
            "N" -> speed
            "M" -> speed?.times(1.9438445)
            "K" -> speed?.times(0.539957)
            else -> null
        }
        if (angle != null || awsKn != null) {
            DataBus.apply(Source.NMEA_0183, NavData(awaDeg = angle, awsKn = awsKn))
        }
    }

    private fun parseDBT(f: List<String>) {
        // $--DBT,xx.x,f,xx.x,M,xx.x,F
        val depthM = f.getOrNull(3)?.toDoubleOrNull()
        if (depthM != null) DataBus.apply(Source.NMEA_0183, NavData(depthM = depthM))
    }

    private fun parseXTE(@Suppress("UNUSED_PARAMETER") f: List<String>) { /* in futuro */ }
    private fun parseRMB(@Suppress("UNUSED_PARAMETER") f: List<String>) { /* in futuro */ }

    private fun parseLat(v: String?, hemi: String?): Double? {
        // ddmm.mmmm + N/S
        val s = v ?: return null
        if (s.length < 4) return null
        val deg = s.substring(0, 2).toIntOrNull() ?: return null
        val min = s.substring(2).toDoubleOrNull() ?: return null
        var out = deg + min / 60.0
        if (hemi == "S") out = -out
        return out
    }
    private fun parseLon(v: String?, hemi: String?): Double? {
        // dddmm.mmmm + E/W
        val s = v ?: return null
        if (s.length < 5) return null
        val deg = s.substring(0, 3).toIntOrNull() ?: return null
        val min = s.substring(3).toDoubleOrNull() ?: return null
        var out = deg + min / 60.0
        if (hemi == "W") out = -out
        return out
    }
}
