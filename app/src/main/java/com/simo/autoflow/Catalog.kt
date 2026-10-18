
package com.simo.autoflow

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.HttpURLConnection
import java.net.URL

data class Link(
    val name: String,
    val url: String,
    val quality: String?,
    val peers: Int?
)

data class Item(
    val title: String,
    val type: String,
    val time: String?,
    val links: List<Link>
)

data class CatalogResult(
    val items: List<Item>,
    val warning: String?,
    val source: String
)

object Catalog {

    fun load(ctx: Context): CatalogResult {

        fun bundled() = try {
            parseJson(
                ctx.assets.open("catalog.json")
                    .bufferedReader()
                    .readText()
            )
        } catch (e: Exception) {
            emptyList()
        }

        if (Config.CATALOG_URL.isBlank()) {
            return CatalogResult(
                bundled(),
                "No hay CATALOG_URL configurada.",
                "ejemplo"
            )
        }

        return try {
            val conn = URL(
                Config.CATALOG_URL +
                "?t=" + System.currentTimeMillis()
            ).openConnection() as HttpURLConnection

            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.useCaches = false

            conn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Android TV) Gecko/120.0"
            )

            if (conn.responseCode != 200) {
                error("HTTP ${conn.responseCode}")
            }

            val rawText = conn.inputStream
                .bufferedReader()
                .use { it.readText() }

            val itemsList = parseJson(rawText)

            if (itemsList.isEmpty()) {
                CatalogResult(
                    bundled(),
                    "El archivo online no contiene eventos válidos.",
                    "online"
                )
            } else {
                CatalogResult(
                    itemsList,
                    null,
                    "online"
                )
            }

        } catch (e: Exception) {
            CatalogResult(
                bundled(),
                "Error de red: ${e.message}",
                "ejemplo"
            )
        }
    }

    private fun parseJson(text: String): List<Item> {

        val root = JSONTokener(text.trim()).nextValue()

        val array = when (root) {
            is JSONArray -> root

            is JSONObject -> when {
                root.opt("events") is JSONArray ->
                    root.getJSONArray("events")

                root.opt("items") is JSONArray ->
                    root.getJSONArray("items")

                else -> return emptyList()
            }

            else -> return emptyList()
        }

        val result = mutableListOf<Item>()
        val seen = mutableSetOf<String>()

        val acePattern = Regex(
            "^acestream://[a-fA-F0-9]{40}$",
            RegexOption.IGNORE_CASE
        )

        for (i in 0 until array.length()) {

            val event = array.optJSONObject(i) ?: continue

            val title = event.optString("title")
                .ifBlank { event.optString("name") }
                .trim()

            if (title.isBlank()) continue

            val links = when {
                event.opt("links") is JSONArray ->
                    event.optJSONArray("links")

                event.opt("channels") is JSONArray ->
                    event.optJSONArray("channels")

                else -> null
            } ?: continue

            val validLinks = mutableListOf<Link>()

            for (j in 0 until links.length()) {

                val entry = links.optJSONObject(j)

                val rawUrl = entry?.optString("url")
                    ?: links.optString(j)

                val normalized = rawUrl.trim()

                if (!acePattern.matches(normalized) ||
                    !seen.add("$title|$normalized")
                ) continue

                validLinks.add(
                    Link(
                        entry?.optString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: "Canal ${j + 1}",

                        normalized,

                        entry?.optString("quality"),

                        if (entry?.has("peers") == true)
                            entry.optInt("peers")
                        else null
                    )
                )
            }

            if (validLinks.isEmpty()) continue

            result.add(
                Item(
                    title,
                    "evento",
                    event.optString("time"),
                    validLinks
                )
            )
        }

        return result
    }
}
