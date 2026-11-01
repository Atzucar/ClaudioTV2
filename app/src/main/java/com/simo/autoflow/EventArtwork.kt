package com.simo.autoflow

import java.util.Locale

/** Elige el fondo de la tarjeta segun competicion, categoria y titulo del evento. */
object EventArtwork {
    private fun has(text: String, vararg terms: String) = terms.any { it in text }

    fun drawable(item: Item, sport: String): Int {
        val competition = item.competition.lowercase(Locale.ROOT)
        val category = item.category.lowercase(Locale.ROOT)
        val title = item.title.lowercase(Locale.ROOT)
        val meta = " $competition $category $title "
        return when {
            // Motor
            has(meta, "motogp", "moto gp", "motociclismo", "moto2", "moto3", "superbike") || sport == "MotoGP" -> R.drawable.sport_moto
            has(meta, "fórmula 1", "formula 1", " f1 ", "gran premio", "grand prix", " gp ", "automovilismo",
                " f2 ", " f3 ", " f4 ", "nascar", "indycar", "rally", "wec ", "le mans", "dtm") -> R.drawable.sport_f1
            // Otros deportes
            sport == "Tenis" || has(meta, "tenis", "tennis", " atp", " wta", "wimbledon", "roland garros") -> R.drawable.sport_tennis
            has(meta, "cricket", "críquet") -> R.drawable.sport_cricket
            has(meta, "baloncesto", "basketball", " nba", "euroliga", "euroleague", "acb", "eurocup") -> R.drawable.sport_basket
            has(meta, "hockey", " nhl", " khl") -> R.drawable.sport_hockey
            has(meta, "voleibol", "volleyball", "voley", "vóley") -> R.drawable.sport_volley
            // Futbol por competicion
            has(meta, "champions") -> R.drawable.bg_champions
            has(meta, "conference league") -> R.drawable.bg_conference
            has(meta, "europa league", "liga europa") -> R.drawable.bg_europa
            has(meta, "serie a", "italia", "coppa italia") -> R.drawable.bg_serie_a
            has(meta, "laliga", "la liga", "primera división", "españa", "copa del rey") -> R.drawable.bg_laliga
            has(meta, "premier league", "inglaterra", "fa cup") -> R.drawable.bg_premier
            has(meta, "bundesliga", "alemania", "dfb") -> R.drawable.bg_bundesliga
            has(meta, "ligue 1", "francia", "liga francesa") -> R.drawable.bg_ligue1
            has(meta, "liga portugal", "primeira liga", "portugal", "liga portuguesa") -> R.drawable.bg_portugal
            sport == "Fútbol" || has(meta, "fútbol", "futbol", "football", "soccer") -> R.drawable.sport_football
            else -> R.drawable.sport_generic
        }
    }
}
