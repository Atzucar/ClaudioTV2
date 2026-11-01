package com.simo.autoflow

import java.util.Locale

object EventArtwork {
    fun drawable(item: Item, sport: String): Int {
        val competition = item.competition.lowercase(Locale.ROOT)
        val title = item.title.lowercase(Locale.ROOT)
        val meta = "$competition $title"
        return when {
            "motogp" in meta || sport == "MotoGP" -> R.drawable.sport_moto
            sport == "Tenis" || "atp" in meta || "wta" in meta -> R.drawable.sport_tennis
            "cricket" in meta || "críquet" in meta || item.category.equals("Cricket", true) -> R.drawable.sport_cricket
            sport != "Fútbol" && !item.category.equals("Fútbol", true) -> R.drawable.sport_football
            "europa league" in meta || "conference league" in meta -> R.drawable.bg_europa
            "champions" in competition || "champions league" in title -> R.drawable.bg_champions
            "serie a" in meta || "italia" in competition -> R.drawable.bg_serie_a
            "laliga" in meta || "la liga" in meta || "españa" in competition -> R.drawable.bg_laliga
            "premier league" in meta || "inglaterra" in competition -> R.drawable.bg_premier
            "bundesliga" in meta || "alemania" in competition -> R.drawable.bg_bundesliga
            else -> R.drawable.sport_football
        }
    }
}
