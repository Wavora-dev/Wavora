package com.wavora.app.input

import com.sun.jna.Native
import com.sun.jna.platform.win32.Kernel32
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.wavora.logger.Logger
import java.util.prefs.Preferences
import javax.swing.SwingUtilities

/**
 * Teclas multimedia del teclado (▶︎⏸ / ⏭ / ⏮ / ⏹) en Windows, funcionando
 * aunque Wavora esté minimizada o en la bandeja (v2.3).
 *
 * Usa `RegisterHotKey` (API estándar de Windows para atajos globales), NO un
 * hook de teclado: no lee ninguna otra tecla y no dispara alertas de antivirus.
 * Si otra app (ej. Spotify) ya tiene registrada una tecla, esa tecla queda para
 * ella y se deja constancia en el log; las demás funcionan igual.
 *
 * Se puede apagar en Ajustes > Teclas multimedia (por ejemplo si usás otro
 * reproductor al mismo tiempo y querés que las teclas vayan a ese).
 */
object GlobalMediaKeys {
    private const val TAG = "GlobalMediaKeys"
    private const val KEY_ENABLED = "mediaKeysEnabled"

    private const val WM_HOTKEY = 0x0312
    private const val WM_QUIT = 0x0012
    private const val MOD_NOREPEAT = 0x4000

    private const val VK_MEDIA_NEXT_TRACK = 0xB0
    private const val VK_MEDIA_PREV_TRACK = 0xB1
    private const val VK_MEDIA_STOP = 0xB2
    private const val VK_MEDIA_PLAY_PAUSE = 0xB3

    enum class Action { PLAY_PAUSE, NEXT, PREVIOUS, STOP }

    private val keys =
        mapOf(
            1 to (VK_MEDIA_PLAY_PAUSE to Action.PLAY_PAUSE),
            2 to (VK_MEDIA_NEXT_TRACK to Action.NEXT),
            3 to (VK_MEDIA_PREV_TRACK to Action.PREVIOUS),
            4 to (VK_MEDIA_STOP to Action.STOP),
        )

    private val prefs: Preferences by lazy { Preferences.userRoot().node("Wavora/Input") }

    var enabledSetting: Boolean
        get() = runCatching { prefs.getBoolean(KEY_ENABLED, true) }.getOrDefault(true)
        set(value) {
            runCatching {
                prefs.putBoolean(KEY_ENABLED, value)
                prefs.flush()
            }
        }

    private val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    @Volatile
    private var thread: Thread? = null

    @Volatile
    private var threadId: Int = 0

    @Volatile
    private var handler: ((Action) -> Unit)? = null

    /** Arranca (si está habilitado y estamos en Windows). Se puede llamar varias veces. */
    @Synchronized
    fun start(onAction: (Action) -> Unit) {
        handler = onAction
        if (!isWindows || !enabledSetting || thread != null) return
        thread =
            Thread({ loop() }, "Wavora-MediaKeys").apply {
                isDaemon = true
                start()
            }
    }

    @Synchronized
    fun stop() {
        val id = threadId
        if (id != 0) {
            runCatching { User32Ext.INSTANCE.PostThreadMessageW(id, WM_QUIT, WinDef.WPARAM(0), WinDef.LPARAM(0)) }
        }
        thread = null
        threadId = 0
    }

    /** Para el switch de Ajustes: guarda y aplica en el momento. */
    fun setEnabled(enabled: Boolean) {
        enabledSetting = enabled
        if (enabled) handler?.let { start(it) } else stop()
    }

    private fun loop() {
        try {
            val user32 = User32.INSTANCE
            // RegisterHotKey con hWnd = null asocia las teclas a ESTE hilo: los
            // WM_HOTKEY llegan a su cola de mensajes, que se lee abajo.
            threadId = Kernel32.INSTANCE.GetCurrentThreadId()
            val registered =
                keys.filter { (id, pair) ->
                    val ok = user32.RegisterHotKey(null, id, MOD_NOREPEAT, pair.first)
                    if (!ok) Logger.w(TAG, "La tecla ${pair.second} ya la usa otra app; se deja para ella")
                    ok
                }.keys
            if (registered.isEmpty()) return

            val msg = WinUser.MSG()
            while (user32.GetMessage(msg, null, 0, 0) > 0) {
                if (msg.message == WM_HOTKEY) {
                    val action = keys[msg.wParam.toInt()]?.second ?: continue
                    val h = handler ?: continue
                    // Al hilo de UI, igual que un click en el reproductor.
                    SwingUtilities.invokeLater { runCatching { h(action) } }
                }
            }
            registered.forEach { user32.UnregisterHotKey(null, it) }
        } catch (e: Throwable) {
            Logger.e(TAG, "Teclas multimedia desactivadas por un error: ${e.message}", e)
        } finally {
            threadId = 0
        }
    }
}

/**
 * PostThreadMessageW declarado a mano (nombre exacto de la DLL) para no
 * depender de que la versión de jna-platform lo exponga en su User32.
 */
@Suppress("FunctionName")
private interface User32Ext : StdCallLibrary {
    fun PostThreadMessageW(
        idThread: Int,
        msg: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
    ): Boolean

    companion object {
        val INSTANCE: User32Ext by lazy { Native.load("user32", User32Ext::class.java) }
    }
}
