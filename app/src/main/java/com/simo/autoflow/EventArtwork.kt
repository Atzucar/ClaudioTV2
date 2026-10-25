package com.simo.autoflow

import java.util.Locale

/** Distinct locally bundled thematic artwork, selected only by explicit competition metadata. */
object EventArtwork {
    fun drawable(item: Item, sport: String): Int {
        val competition = item.competition.lowercase(Locale.ROOT)
        return when {
            "champions" in competition -> R.drawable.bg_champions
            "europa league" in competition -> R.drawable.bg_europa
            "conference" in competition -> R.drawable.bg_europa
            "serie a" in competition -> R.drawable.bg_serie_a
            "laliga" in competition || "la liga" in competition -> R.drawable.bg_laliga
            "premier league" in competition -> R.drawable.bg_premier
            "bundesliga" in competition -> R.drawable.bg_bundesliga
            sport == "Tenis" -> R.drawable.sport_tennis
            sport == "MotoGP" -> R.drawable.sport_moto
            item.title.contains("cricket", true) -> R.drawable.sport_cricket
            else -> R.drawable.sport_football
        }
    }
}
