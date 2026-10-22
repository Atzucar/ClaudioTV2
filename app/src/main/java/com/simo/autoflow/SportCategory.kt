package com.simo.autoflow

import java.util.Locale

/** Prefiere la categoria del catalogo; los titulos solo permiten una clasificacion aproximada. */
object SportCategory {
    val options = listOf("Todo", "Fútbol", "Tenis", "MotoGP", "Otros")

    fun of(item: Item): String {
        val raw = (item.category + " " + item.title).lowercase(Locale.ROOT)
        val explicit = item.category.trim().lowercase(Locale.ROOT)
        if (explicit.contains("motogp") || explicit.contains("motocicl") || explicit.contains("motocross")) return "MotoGP"
        if (explicit.contains("tenis") || explicit.contains("tennis") || explicit.contains("atp") || explicit.contains("wta")) return "Tenis"
        if (explicit.contains("futbol") || explicit.contains("fútbol") || explicit.contains("football") || explicit.contains("soccer")) return "Fútbol"
        if (explicit.isNotEmpty() && explicit != "evento" && explicit != "evento deportivo") return "Otros"
        if (listOf("motogp", "moto gp", "motociclismo", "motocross").any { raw.contains(it) }) return "MotoGP"
        if (listOf("tennis", "tenis", " atp ", " wta ", "wimbledon", "roland garros", "us open tennis").any { raw.contains(it) }) return "Tenis"
        if (listOf("champions league", "europa league", "premier league", "la liga", "laliga", "bundesliga", "serie a", "copa del rey", "celtic", "celta", "barcelona", "real sociedad", "atletico", "atlético", "ajax", "atalanta", "bournemouth", "milan", "milán", "borussia", "dortmund", "betis", "sevilla", "fenerbahce", "benfica", "benfica", "manchester", "liverpool", "arsenal", "juventus", "inter ", "psg", "monaco", "mónaco", "montpellier", "twente", "crystal palace", "hoffenheim", "anderlecht", "estrella roja").any { raw.contains(it) }) return "Fútbol"
        return "Otros"
    }
}
