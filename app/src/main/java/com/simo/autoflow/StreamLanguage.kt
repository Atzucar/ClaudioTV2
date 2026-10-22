package com.simo.autoflow

/** Idioma declarado por la fuente. Nunca deducirlo del hash AceStream. */
object StreamLanguage {
    private val labels = mapOf(
        "es" to Pair("🇪🇸", "Español"),
        "en" to Pair("🇬🇧", "Inglés"),
        "fr" to Pair("🇫🇷", "Francés"),
        "de" to Pair("🇩🇪", "Alemán"),
        "it" to Pair("🇮🇹", "Italiano"),
        "pt" to Pair("🇵🇹", "Portugués"),
        "ru" to Pair("🇷🇺", "Ruso"),
        "ar" to Pair("🇸🇦", "Árabe"),
        "tr" to Pair("🇹🇷", "Turco"),
        "nl" to Pair("🇳🇱", "Neerlandés"),
        "pl" to Pair("🇵🇱", "Polaco")
    )

    fun label(code: String?): String {
        val normalized = code?.lowercase()?.trim()?.substringBefore('-') ?: ""
        val pair = labels[normalized] ?: return "🌐  Idioma desconocido"
        return "${pair.first}  ${pair.second}"
    }
}
