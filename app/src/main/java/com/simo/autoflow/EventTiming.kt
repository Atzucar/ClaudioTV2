package com.simo.autoflow

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Compatible with older Fire OS/Android API levels without java.time. */
object EventTiming {
    fun minutesUntil(startAt: String?): Long? {
        if (startAt.isNullOrBlank()) return null
        val patterns = listOf("yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX", "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")
        for (pattern in patterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }
                val parsed: Date = parser.parse(startAt) ?: continue
                val delta = parsed.time - System.currentTimeMillis()
                // Round upward: 30m01s is not within 30m, 0m20s displays 1m.
                return if (delta >= 0) (delta + 59999L) / 60000L else -1L
            } catch (e: Exception) { }
        }
        return null
    }

    fun isUpcoming(startAt: String?): Boolean = minutesUntil(startAt)?.let { it in 0..30 } ?: false
    fun subtitle(item: Item): String {
        val n = minutesUntil(item.startAt)
        return if (n != null && n in 0..30) "Comienza en $n min" else ""
    }
}
