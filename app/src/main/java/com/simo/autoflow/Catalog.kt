
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

        fun bundled(): List<Item> = try {
            val text = ctx.assets.open("catalog.json")
                .bufferedReader()
                .use { it.readText() }

            parseJson(text)
        } catch (e: Exception) {
            emptyList()
        }

        if (Config.CATALOG_URL.isBlank()) {
            return CatalogResult(
                bundled(),
                "No hay URL de catalogo configurada",
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

            try {
                if (conn.responseCode != 200) {
                    error("HTTP ${conn.responseCode}")
                }

                val text = conn.inputStream
                    .bufferedReader()
                    .use { it.readText() }

                val items = parseJson(text)

                CatalogResult(
                    items,
                    if (items.isEmpty())
                        "No hay eventos AceStream disponibles"
                    else null,
                    "online"
                )
            } finally {
                conn.disconnect()
            }

        } catch (e: Exception) {
            CatalogResult(
                bundled(),
                "Error de catalogo: ${e.message}",
                "ejemplo"
            )
        }
    }

    private fun parseJson(text: String): List<Item> {

        val root = JSONTokener(text.trim()).nextValue()

        val events = when (root) {
            is JSONArray -> root
            is JSONObject -> {
                root.optJSONArray("events")
                    ?: root.optJSONArray("items")
                    ?: return emptyList()
            }
            else -> return emptyList()
        }

        val result = mutableListOf<Item>()

        val acePattern = Regex(
            "^acestream://[a-fA-F0-9]{40}$",
            RegexOption.IGNORE_CASE
        )

        for (i in 0 until events.length()) {

            val event = events.optJSONObject(i)
                ?: continue

            val title = event.optString("title")
                .ifBlank { event.optString("name") }
                .trim()

            if (title.isBlank()) continue

            val entries = event.optJSONArray("links")
                ?: event.optJSONArray("channels")
                ?: continue

            val links = mutableListOf<Link>()
            val seen = mutableSetOf<String>()

            for (j in 0 until entries.length()) {

                val entry = entries.optJSONObject(j)

                val rawUrl = if (entry != null) {
                    entry.optString("url")
                } else {
                    entries.optString(j)
                }

                val aceUrl = rawUrl.trim()

                if (!acePattern.matches(aceUrl)) continue
                if (!seen.add(aceUrl.lowercase())) continue

                links.add(
                    Link(
                        name = entry?.optString("name")
                            ?.takeIf { it.isNotBlank() }
                            ?: "AceStream ${j + 1}",
                        url = aceUrl,
                        quality = entry?.optString("quality")
                            ?.takeIf { it.isNotBlank() },
                        peers = if (
                            entry != null &&
                            !entry.isNull("peers")
                        ) entry.optInt("peers")
                        else null
                    )
                )
            }

            if (links.isEmpty()) continue

            result.add(
                Item(
                    title = title,
                    type = event.optString("type", "evento"),
                    time = event.optString("time"),
                    links = links
                )
            )
        }

        return result
    }
}

