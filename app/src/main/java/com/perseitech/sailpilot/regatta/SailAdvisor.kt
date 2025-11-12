package com.perseitech.sailpilot.regatta

import kotlin.math.abs

data class SailAdvice(
    val headline: String,
    val details: List<String>
)

/**
 * Semplice regole TWS/TWA:
 *  - bolina 30–55: Jib/Genoa (1=light, 2=med, 3=heavy)
 *  - traverso 55–110: Jib/Code0/Asy A3
 *  - lasco 110–150: A2/A3
 *  - poppa 150–180: A2/A4 / S2
 * La scelta dipende anche da materiale e classe (placeholder semplice).
 */
object SailAdvisor {
    fun suggest(
        twsKn: Double?, twaDeg: Double?, // se null, fornisco “inserisci dati”
        settings: RegattaSettings,
        boatClass: BoatClass?
    ): SailAdvice {
        if (twsKn == null || twaDeg == null) {
            return SailAdvice("Dati vento mancanti", listOf("Inserisci TWS/TWA o collega strumenti."))
        }

        val inv = settings.inventory.ifEmpty {
            setOf("J1","J2","J3","Code0","A2","A3","A4","Staysail")
        }

        val t = abs(((twaDeg % 360) + 360) % 360.0).let { if (it>180) 360-it else it }
        val out = mutableListOf<String>()

        when {
            t < 40 -> { // stretta
                out += when {
                    twsKn < 10 -> "J1 (light #1)"
                    twsKn < 18 -> "J2 (medium #2)"
                    else       -> "J3 (heavy #3)"
                }.takeIf { inv.contains(it.substringBefore(" ")) } ?: "Jib adeguato (J1/J2/J3)"
                if (inv.contains("Staysail") && twsKn>18) out += "Staysail con vento forte"
            }
            t < 60 -> {
                out += if (twsKn < 14 && inv.contains("Code0")) "Code0" else "J2/J3"
            }
            t < 110 -> {
                out += if (twsKn < 16) "A3 (reacher asy)" else "J2/J3 / A3 ridotto"
                if (inv.contains("Code0") && twsKn < 10) out += "Alternativa: Code0"
            }
            t < 150 -> {
                out += if (twsKn < 16) "A2 (runner asy)" else "A4 (heavy asy)"
            }
            else -> {
                out += if (twsKn < 18) "A2 / S2" else "A4 / S4"
            }
        }

        val head = buildString {
            append("${"%.0f".format(twsKn)} kn  ")
            append("${"%.0f".format(twaDeg)}° TWA")
            if (boatClass != null) append("  •  ${boatClass.name}")
        }

        // piccola nota materiale
        val matNote = when (settings.sailMaterial) {
            SailMaterial.DACRON -> "Dacron: prediligi vele più piene (J1/J2) con aria leggera."
            SailMaterial.LAMINATE, SailMaterial.MEMBRANE, SailMaterial._3DL, SailMaterial.OTHER -> "Laminato/Membrane: tieni profili piatti quando TWS cresce."
        }

        return SailAdvice(headline = head, details = out + matNote)
    }
}
