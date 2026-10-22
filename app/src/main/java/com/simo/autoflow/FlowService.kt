package com.simo.autoflow

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.net.Uri
import android.content.Context
import android.media.AudioManager
import android.view.KeyEvent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import kotlinx.coroutines.*

class FlowService : AccessibilityService() {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(i: Intent?): Boolean { instance = null; scope.cancel(); return super.onUnbind(i) }
    @Volatile private var awaitingAceStream = false
    @Volatile private var inAceStream = false
    @Volatile private var disconnecting = false

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString()?.lowercase() ?: return
        val ace = pkg.contains("acestream") || pkg.contains("aceplayer")
        if (ace && awaitingAceStream) inAceStream = true
        // Solo actuar al salir de un reproductor que hemos visto abrir.
        if (inAceStream && !ace && (pkg == packageName || pkg.contains("launcher") || pkg.contains("firelauncher"))) {
            inAceStream = false
            awaitingAceStream = false
            sendMediaStop()
            // La conexion VPN fue iniciada por ClaudioTV: intentar desconectar
            // cuando Ace Stream abandona el primer plano.
            if (!disconnecting && vpnOk) {
                val goHome = pkg.contains("launcher")
                disconnecting = true
                scope.launch {
                    try { disconnectRussia(goHome) }
                    catch (ex: Exception) { Log.w(TAG, "No se pudo desconectar ProtonVPN: ${ex.message}") }
                    finally { disconnecting = false }
                }
            }
        }
    }

    fun sendMediaStop() {
        // Peticion estandar a la sesion multimedia activa; el reproductor puede ignorarla.
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_STOP))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_STOP))
    }
    // Fire TV puede entregar ATRAS al servicio solo si el usuario ha habilitado
    // el filtrado de teclas para esta accesibilidad. No consumimos la pulsacion:
    // Ace Stream Media debe poder volver normalmente.
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (inAceStream && event.action == KeyEvent.ACTION_DOWN &&
            (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_HOME)) {
            sendMediaStop()
            Log.i(TAG, "Solicitada parada multimedia al salir de Ace Stream")
        }
        return false
    }

    private suspend fun disconnectRussia(returnHome: Boolean) {
        launchApp(Config.PROTON_PKG)
        delay(1300)
        // Asegurar que el texto corresponde a Proton VPN (no al reproductor).
        val disconnectTexts = Config.CONNECTED_TEXTS.filter {
            it.equals("Disconnect", true) || it.equals("Desconectar", true)
        }
        val button = waitFor(disconnectTexts, 6500)
        if (button != null) {
            click(button)
            vpnOk = false // Indicador local; confirmar el estado real en Proton VPN
            Log.i(TAG, "Desconexion VPN solicitada")
        } else {
            Log.w(TAG, "No se encontro boton de desconexion de ProtonVPN")
        }
        if (returnHome) performGlobalAction(GLOBAL_ACTION_HOME)
        else launchApp(packageName)
    }

    override fun onInterrupt() {}

    fun cancel() { job?.cancel() }

    fun runFlow(streamUrl: String) {
        job?.cancel()
        awaitingAceStream = false
        inAceStream = false
        for (i in steps.indices) steps[i] = PENDING
        lastError = null; notifyChange()
        job = scope.launch {
            var cur = 0
            try {
                cur = 0; set(0, ACTIVE)
                launchApp(Config.PROTON_PKG)
                delay(4000)
                val already = waitFor(Config.CONNECTED_TEXTS, 3000) != null && findText(Config.COUNTRY_TEXTS) != null
                set(0, DONE)
                cur = 1; set(1, ACTIVE)
                if (!already) connectVpn()
                vpnOk = true
                set(1, DONE)
                cur = 2; set(2, ACTIVE)
                playStream(streamUrl)
                set(2, DONE)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Flujo fallido: ${e.message}")
                lastError = e.message; set(cur, ERROR)
            }
        }
    }

    private fun set(i: Int, st: Int) { steps[i] = st; notifyChange() }
    private fun notifyChange() { onChange?.invoke() }

    private suspend fun connectVpn() {
        // Buscar Rusia en la lista (desplazando) y pulsarla.
        val country = scrollFind(Config.COUNTRY_TEXTS) ?: error("No encuentro Rusia en ProtonVPN")
        click(country)
        delay(1500)
        // Algunas versiones conectan al tocar el pais; si no, pulsar Connect.
        waitFor(Config.CONNECT_TEXTS, 5000)?.let { click(it) }
        waitFor(Config.CONNECTED_TEXTS, Config.VPN_TIMEOUT_MS) ?: error("VPN no conecto")
        delay(2000)
    }

    private suspend fun playStream(url: String) {
        awaitingAceStream = true
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        // Si Fire TV pregunta con que app abrirlo, elegir AceStream.
        waitFor(listOf("Ace Stream", "AceStream", "Ace Player"), 6000)?.let { click(it) }
    }

    // ---- utilidades ----
    private fun launchApp(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg) ?: packageManager.getLaunchIntentForPackage(pkg)
            ?: error("App no instalada: $pkg")
        startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
    }

    private fun findText(texts: List<String>): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        for (t in texts) root.findAccessibilityNodeInfosByText(t).firstOrNull()?.let { return it }
        return null
    }

    private suspend fun waitFor(texts: List<String>, timeout: Long = Config.STEP_TIMEOUT_MS): AccessibilityNodeInfo? {
        val end = System.currentTimeMillis() + timeout
        while (System.currentTimeMillis() < end) { findText(texts)?.let { return it }; delay(500) }
        return null
    }

    private suspend fun scrollFind(texts: List<String>): AccessibilityNodeInfo? {
        repeat(25) {
            findText(texts)?.let { return it }
            val sc = findScrollable(rootInActiveWindow)
            if (sc != null) sc.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) else return null
            delay(700)
        }
        return null
    }

    private fun findScrollable(n: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (n == null) return null
        if (n.isScrollable) return n
        for (i in 0 until n.childCount) findScrollable(n.getChild(i))?.let { return it }
        return null
    }

    private fun click(n: AccessibilityNodeInfo) {
        var c: AccessibilityNodeInfo? = n
        while (c != null) {
            if (c.isClickable && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
            c = c.parent
        }
        n.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
    }

    companion object {
        const val TAG = "AutoFlow"
        const val PENDING = 0; const val ACTIVE = 1; const val DONE = 2; const val ERROR = 3
        val STEP_NAMES = listOf("Abriendo ProtonVPN", "Conectando a Rusia", "Abriendo AceStream")
        val steps = IntArray(3)
        @Volatile var lastError: String? = null
        @Volatile var vpnOk = false
        var instance: FlowService? = null
        var onChange: (() -> Unit)? = null
    }
}
