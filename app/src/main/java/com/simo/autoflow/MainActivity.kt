package com.simo.autoflow

import android.app.Activity
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

object Session { var items: List<Item> = emptyList() }

/** Portada visual para Fire TV: tarjetas grandes y navegacion con el mando. */
class MainActivity : Activity() {
    private lateinit var chips: LinearLayout
    private lateinit var highlights: LinearLayout
    private lateinit var secondary: LinearLayout
    private lateinit var updated: TextView
    private lateinit var notice: TextView
    private var filter = "Todo"
    private var firstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF06111F.toInt())
            setPadding(Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 16), Ui.dp(this@MainActivity, 30), Ui.dp(this@MainActivity, 12))
        }
        val header = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }, Ui.lp(Ui.dp(this, 42), Ui.dp(this, 42), r = Ui.dp(this, 10)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        brand.addView(Ui.tv(this, "ClaudioTV", 23f, Ui.TEXT, true))
        brand.addView(Ui.tv(this, "DEPORTE EN DIRECTO  ·  ACESTREAM", 9f, Ui.SOFT))
        header.addView(brand, Ui.lp(0, -2, 1f))
        header.addView(filterChip(this, "↻  Recargar", false) { refresh() })
        root.addView(header, Ui.lp(-1, -2, b = Ui.dp(this, 12)))

        notice = Ui.tv(this, "", 12f, Ui.RED).apply { visibility = View.GONE }
        root.addView(notice)
        val body = ScrollView(this).apply { isFillViewport = false; isFocusable = false }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        chips = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        column.addView(horizontal(chips), Ui.lp(-1, -2, b = Ui.dp(this, 18)))
        column.addView(Ui.tv(this, "Eventos destacados", 23f, Ui.TEXT, true), Ui.lp(-1, -2, b = Ui.dp(this, 10)))
        highlights = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        column.addView(horizontal(highlights), Ui.lp(-1, -2, b = Ui.dp(this, 18)))
        column.addView(Ui.tv(this, "Más eventos", 19f, Ui.TEXT, true), Ui.lp(-1, -2, b = Ui.dp(this, 10)))
        secondary = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(secondary)
        body.addView(column)
        root.addView(body, Ui.lp(-1, 0, 1f))
        val footer = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        footer.addView(Ui.tv(this, "← → Categorías y eventos     OK  Ver enlaces", 11f, Ui.MUTED), Ui.lp(0, -2, 1f))
        updated = Ui.tv(this, "", 11f, Ui.SOFT)
        footer.addView(updated)
        root.addView(footer, Ui.lp(-1, -2, t = Ui.dp(this, 8)))
        setContentView(root)
        buildChips()
        refresh()
    }

    private fun horizontal(child: LinearLayout) = HorizontalScrollView(this).apply {
        isHorizontalScrollBarEnabled = false
        addView(child)
        descendantFocusability = ViewGroup.FOCUS_AFTER_DESCENDANTS
    }

    private fun refresh() {
        updated.text = "Actualizando catálogo…"
        thread {
            val result = runCatching { Catalog.load(this) }
            runOnUiThread {
                result.onSuccess { r ->
                    Session.items = r.items
                    notice.text = r.warning ?: ""
                    notice.visibility = if (r.warning.isNullOrBlank()) View.GONE else View.VISIBLE
                    updated.text = "${r.items.size} eventos · ${r.source} · ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                    buildChips()
                    buildEvents()
                }.onFailure { e ->
                    notice.text = "No se pudo leer el catálogo: ${e.message}"
                    notice.visibility = View.VISIBLE
                    updated.text = "Error de catálogo"
                }
            }
        }
    }

    private fun buildChips() {
        chips.removeAllViews()
        SportCategory.options.forEach { cat ->
            val count = if (cat == "Todo") Session.items.size else Session.items.count { SportCategory.of(it) == cat }
            val icon = when(cat) { "Fútbol" -> "⚽"; "Tenis" -> "●"; "MotoGP" -> "◆"; "Otros" -> "•••"; else -> "▦" }
            chips.addView(filterChip(this, "$icon  ${if (cat == "Todo") "Todos" else cat}  $count", filter == cat) {
                filter = cat
                buildChips()
                buildEvents()
                chips.getChildAt(SportCategory.options.indexOf(cat))?.requestFocus()
            }, Ui.lp(-2, -2, r = Ui.dp(this, 10)))
        }
    }

    private fun buildEvents() {
        highlights.removeAllViews()
        secondary.removeAllViews()
        val shown = Session.items.withIndex().filter { filter == "Todo" || SportCategory.of(it.value) == filter }
        if (shown.isEmpty()) {
            secondary.addView(Ui.tv(this, "No hay eventos AceStream en esta categoría.", 17f, Ui.SOFT))
            return
        }
        shown.take(5).forEachIndexed { n, indexed ->
            val (index, item) = indexed
            val sport = SportCategory.of(item)
            val drawable = when(sport) {
                "Fútbol" -> R.drawable.sport_football
                "Tenis" -> R.drawable.sport_tennis
                "MotoGP" -> R.drawable.sport_moto
                else -> if (item.title.contains("india", true) || item.title.contains("cricket", true)) R.drawable.sport_cricket else R.drawable.sport_football
            }
            val card = FrameLayout(this).apply {
                isFocusable = true
                isClickable = true
                background = Ui.rounded(this@MainActivity, Ui.CARD, 14)
                clipToOutline = true
            }
            card.addView(ImageView(this).apply {
                setImageResource(drawable)
                scaleType = ImageView.ScaleType.CENTER_CROP
                alpha = 0.82f
            }, FrameLayout.LayoutParams(-1, -1))
            card.addView(View(this).apply {
                background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                    intArrayOf(0x14000000, 0x8806111F.toInt(), 0xFF06111F.toInt()))
            }, FrameLayout.LayoutParams(-1, -1))
            val text = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 8), Ui.dp(this@MainActivity, 12), Ui.dp(this@MainActivity, 12))
            }
            text.addView(Ui.tv(this, sport.uppercase(Locale.getDefault()), 10f, 0xFF66CFFF.toInt(), true))
            text.addView(Ui.tv(this, item.title, 18f, Ui.WHITE, true), Ui.lp(-1, -2, t = Ui.dp(this, 5)))
            text.addView(Ui.tv(this, "${item.links.size} enlace(s) AceStream${item.time?.takeIf { it.isNotBlank() }?.let { "  ·  $it" } ?: ""}", 11f, Ui.SOFT), Ui.lp(-1, -2, t = Ui.dp(this, 7)))
            card.addView(text, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
            card.setOnFocusChangeListener { _, focused ->
                card.foreground = if (focused) Ui.rounded(this, 0x00000000, 14, 0xFF20C9FF.toInt(), 3) else null
                card.scaleX = if (focused) 1.025f else 1f
                card.scaleY = if (focused) 1.025f else 1f
            }
            card.setOnClickListener { open(index) }
            highlights.addView(card, Ui.lp(Ui.dp(this, 250), Ui.dp(this, 230), r = Ui.dp(this, 12)))
            if (n == 0 && firstLoad) { card.requestFocus(); firstLoad = false }
        }
        shown.drop(5).forEach { (idx, item) ->
            val row = FocusRow(this, SportCategory.of(item).uppercase(Locale.getDefault()), item.title,
                "${item.links.size} ▶", item.time?.takeIf { it.isNotBlank() })
            row.setOnClickListener { open(idx) }
            secondary.addView(row, Ui.lp(-1, -2, b = Ui.dp(this, 7)))
        }
    }

    private fun open(index: Int) {
        startActivity(Intent(this, LinksActivity::class.java).putExtra("i", index))
    }
}
