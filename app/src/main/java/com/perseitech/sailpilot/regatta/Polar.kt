package com.perseitech.sailpilot.regatta

/** Placeholder per polari/angoli di virata di base. */
object Polar {
    /** Angolo di virata “base” se non abbiamo polari di classe. */
    fun defaultTackAngleDeg(twsKn: Double?): Double =
        when {
            twsKn == null -> 90.0
            twsKn < 6.0 -> 95.0
            twsKn < 12.0 -> 90.0
            twsKn < 20.0 -> 85.0
            else -> 80.0
        }
}
