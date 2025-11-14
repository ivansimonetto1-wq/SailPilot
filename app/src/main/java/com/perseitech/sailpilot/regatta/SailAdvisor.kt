package com.perseitech.sailpilot.regatta

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt

/**
 * Singolo punto di polare: TWS, TWA, boat speed target.
 */
data class PolarPoint(
    val tws: Double,      // true wind speed [kn]
    val twa: Double,      // true wind angle (0..180) [deg]
    val boatSpeed: Double // boat speed target [kn]
)

/**
 * Normalizza TWA in [-180, +180].
 */
private fun normalizeTwa(twa: Double): Double {
    var x = twa
    while (x < -180.0) x += 360.0
    while (x > 180.0) x -= 360.0
    return x
}

/**
 * Modello polare con interpolazione semplice.
 */
class PolarModel(private val points: List<PolarPoint>) {

    private val grid: Map<Int, List<PolarPoint>> =
        points.groupBy { it.tws.roundToInt() }

    /** restituisce speed target per (tws,twa) oppure null se non c’è copertura. */
    fun targetSpeed(tws: Double, twa: Double): Double? {
        if (points.isEmpty()) return null
        val twaAbs = abs(normalizeTwa(twa))
        val twsKey = tws.roundToInt()
        val row = grid[twsKey] ?: points.filter { it.tws.roundToInt() == twsKey }
        if (row.isEmpty()) return null

        // prendo i due punti più vicini in TWA e interpolo linearmente
        val sorted = row.sortedBy { abs(it.twa - twaAbs) }
        val p1 = sorted[0]
        val p2 = sorted.getOrNull(1) ?: p1
        if (p1.twa == p2.twa) return p1.boatSpeed

        val w = (twaAbs - p1.twa) / (p2.twa - p1.twa)
        return p1.boatSpeed + (p2.boatSpeed - p1.boatSpeed) * w
    }

    /**
     * Target TWA di bolina per dato TWS:
     * scelgo il punto <=90° che massimizza VMG sopravvento.
     */
    fun targetUpwindTwa(tws: Double): Double? {
        val key = tws.roundToInt()
        val rows = points.filter { it.tws.roundToInt() == key && it.twa <= 90.0 }
        if (rows.isEmpty()) return null
        return rows.maxByOrNull { p ->
            val rad = Math.toRadians(p.twa)
            p.boatSpeed * cos(rad)
        }?.twa
    }

    /**
     * Target TWA di poppa per dato TWS:
     * scelgo il punto >=90° che massimizza VMG sottovento.
     */
    fun targetDownwindTwa(tws: Double): Double? {
        val key = tws.roundToInt()
        val rows = points.filter { it.tws.roundToInt() == key && it.twa >= 90.0 }
        if (rows.isEmpty()) return null
        // VMG sottovento = Vb * cos(180 - TWA)
        return rows.maxByOrNull { p ->
            val rad = Math.toRadians(180.0 - p.twa)
            p.boatSpeed * cos(rad)
        }?.twa
    }
}

/**
 * Repository di polari: al momento solo una polare generica.
 * Non dipende da BoatClass: in futuro potrai fare mapping per classe.
 */
object PolarRepo {

    private val genericModel: PolarModel

    init {
        val genericCruiser = listOf(
            // TWS 6 kn
            PolarPoint(6.0, 40.0, 5.2),
            PolarPoint(6.0, 60.0, 5.8),
            PolarPoint(6.0, 90.0, 6.0),
            PolarPoint(6.0, 120.0, 5.7),
            PolarPoint(6.0, 150.0, 5.1),

            // TWS 12 kn
            PolarPoint(12.0, 38.0, 7.2),
            PolarPoint(12.0, 60.0, 7.9),
            PolarPoint(12.0, 90.0, 8.1),
            PolarPoint(12.0, 120.0, 7.7),
            PolarPoint(12.0, 150.0, 7.0),

            // TWS 20 kn
            PolarPoint(20.0, 40.0, 8.1),
            PolarPoint(20.0, 60.0, 8.7),
            PolarPoint(20.0, 90.0, 8.8),
            PolarPoint(20.0, 120.0, 8.1),
            PolarPoint(20.0, 150.0, 7.2),
        )
        genericModel = PolarModel(genericCruiser)
    }

    /**
     * Per ora ignora la classe.
     * In futuro potrai mappare BoatClass -> modello specifico.
     */
    fun forClass(@Suppress("UNUSED_PARAMETER") boatClass: BoatClass?): PolarModel? = genericModel
}

/**
 * Output del Sail Advisor, pronto per la UI.
 */
data class SailAdvice(
    val headline: String,
    val details: List<String>
)

/**
 * Logica di scelta vele (versione generica, indipendente dai campi di BoatClass/RegattaSettings).
 *
 * BoatClass e RegattaSettings sono accettati come contesto ma non vengono letti in dettaglio,
 * così non abbiamo problemi anche se le loro proprietà cambiano.
 */
object SailAdvisor {

    fun suggest(
        twsKn: Double?,
        twaDeg: Double?,
        settings: RegattaSettings? = null,   // non usato in dettaglio (per ora)
        boatClass: BoatClass? = null         // idem
    ): SailAdvice {
        if (twsKn == null || twaDeg == null) {
            return SailAdvice(
                headline = "Dati vento non disponibili",
                details = listOf("Serve almeno TWS e TWA (da strumenti o inseriti a mano).")
            )
        }

        val twaAbs = abs(normalizeTwa(twaDeg))
        val polars = PolarRepo.forClass(boatClass)
        val speedTarget = polars?.targetSpeed(twsKn, twaAbs)

        val legType = when {
            twaAbs < 60.0      -> "bolina"
            twaAbs <= 120.0    -> "traverso"
            else               -> "poppa"
        }

        // Soglie di vento generiche (kn)
        val reef1Tws = 18.0
        val reef2Tws = 24.0
        val stormTws = 30.0
        val lightWindThreshold = 7.0
        val heavyWindThreshold = 20.0
        val safetyMarginPercent = 10.0

        val base = mutableListOf<String>()

        // --- Main & reefing ---
        val mainConfig = when {
            twsKn >= stormTws -> "Main molto ridotta / trysail, fiocco di fortuna."
            twsKn >= reef2Tws -> "Secondo terzarolo sulla randa, fiocco piccolo / tormentina."
            twsKn >= reef1Tws -> "Primo terzarolo sulla randa, fiocco medio."
            else              -> "Randa piena."
        }
        base += "Randa: $mainConfig"

        // --- Headsail / kite ---
        val headsail = when {
            legType == "bolina" && twsKn < lightWindThreshold ->
                "Genoa grande / Code 0 (se disponibile) per massimizzare VMG di bolina."
            legType == "bolina" ->
                "Fiocco da vento medio; concentrati su twist e carrello genoa."

            legType == "traverso" && twsKn in 8.0..18.0 ->
                "Jib + Code 0 / gennaker asimmetrico, se consentito dalla classe."
            legType == "traverso" && twsKn > heavyWindThreshold ->
                "Fiocco pesante o tormentina, niente kite."

            legType == "poppa" && twsKn in 6.0..20.0 ->
                "Spi/gennaker principale; lavora con angoli di 140–150° TWA."
            legType == "poppa" && twsKn > heavyWindThreshold ->
                "Spi ridotto o niente spi; meglio sicurezza che una straorzata."

            else ->
                "Fiocco / genoa standard."
        }
        base += "Vela di prua / spi: $headsail"

        // --- Polari: target angoli & velocità ---
        if (polars != null) {
            val up = polars.targetUpwindTwa(twsKn)
            val down = polars.targetDownwindTwa(twsKn)
            speedTarget?.let {
                val safeSpeed = it * (1.0 - safetyMarginPercent / 100.0)
                base += "Target boat speed (polare, -${safetyMarginPercent.toInt()}% safety): ${"%.1f".format(safeSpeed)} kn."
            }
            up?.let {
                base += "Target TWA di bolina ≈ ${it.roundToInt()}° (miglior VMG sopravvento)."
            }
            down?.let {
                base += "Target TWA di poppa ≈ ${it.roundToInt()}° (miglior VMG sottovento)."
            }
        } else {
            base += "Polari specifiche non caricate – uso regole generiche."
        }

        // --- Nota sulla classe, se presente ---
        boatClass?.let { bc ->
            base += "Classe: ${bc.toString()}."
        }

        val headline = when (legType) {
            "bolina" -> when {
                twsKn < lightWindThreshold ->
                    "Bolina, vento leggero: spingi con genoa grande o Code 0."
                twsKn > heavyWindThreshold ->
                    "Bolina dura: reef e fiocco piccolo, massima sicurezza."
                else ->
                    "Bolina: assetto equilibrato, lavora di fino."
            }
            "traverso" -> when {
                twsKn < lightWindThreshold ->
                    "Traverso leggero: vele piene, cerca angolo di potenza."
                twsKn > heavyWindThreshold ->
                    "Traverso forte: riduci tela e controlla stabilità."
                else ->
                    "Traverso: molto aggressivo con Code 0 / gennaker."
            }
            else -> when {
                twsKn < lightWindThreshold ->
                    "Poppa leggera: kite grande e rollio controllato."
                twsKn > heavyWindThreshold ->
                    "Poppa forte: attenzione alle straorzate, meglio ridurre."
                else ->
                    "Poppa: spingi con gennaker/spi ma resta sotto controllo."
            }
        }

        return SailAdvice(
            headline = headline,
            details = base
        )
    }
}
