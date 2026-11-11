package com.wavora.app.input

import com.wavora.logger.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.Window
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import java.util.prefs.Preferences

/**
 * Atajos de teclado configurables de la ventana principal (v2.3.1).
 *
 * POR QUÉ NO ANDABAN (v2.3.0): estaban en el `onKeyEvent` de la ventana de
 * Compose, que solo recibe teclas cuando algún elemento de la UI tiene el foco.
 * Usando la app con el mouse casi nunca hay un elemento enfocado, así que las
 * teclas no llegaban a ningún lado.
 *
 * AHORA se escuchan con un `KeyEventDispatcher` de AWT, que recibe TODAS las
 * teclas de la app antes que Compose, y se filtran a la ventana principal.
 * Cuando una tecla coincide con un atajo, se consume (Compose no la ve).
 *
 * Para no interferir con la escritura (búsqueda, nombres de playlist, etc.),
 * un atajo tiene que usar Ctrl o Alt, o ser una tecla F1–F12: así nunca choca
 * con escribir texto. Por eso los atajos de "una sola tecla" (como Espacio
 * solo) no se permiten.
 *
 * Todo es configurable en Ajustes › Rendimiento (PC) › Atajos de teclado:
 * activar/desactivar, cambiar cada combinación, quitarla o volver a los
 * valores por defecto. Se guarda en java.util.prefs (Wavora/Shortcuts).
 */
object KeyboardShortcuts {
    private const val TAG = "KeyboardShortcuts"
    private const val KEY_ENABLED = "enabled"

    enum class Action {
        PLAY_PAUSE,
        NEXT,
        PREVIOUS,
        VOLUME_UP,
        VOLUME_DOWN,
        LIKE,
        MINI_PLAYER,
    }

    /** Combinación: código de tecla AWT + modificadores (máscara *_DOWN_MASK). */
    data class Binding(
        val keyCode: Int,
        val modifiers: Int,
    ) {
        /** Texto para mostrar, en el idioma del sistema (ej. "Ctrl+Alt+Right"). */
        fun label(): String {
            val mods = InputEvent.getModifiersExText(modifiers)
            val key = KeyEvent.getKeyText(keyCode)
            return if (mods.isBlank()) key else "$mods+$key"
        }

        fun serialize(): String = "$keyCode,$modifiers"

        companion object {
            fun parse(raw: String?): Binding? {
                if (raw.isNullOrBlank()) return null
                val parts = raw.split(',')
                val code = parts.getOrNull(0)?.toIntOrNull() ?: return null
                val mods = parts.getOrNull(1)?.toIntOrNull() ?: return null
                return Binding(code, mods)
            }
        }
    }

    private const val CTRL = InputEvent.CTRL_DOWN_MASK
    private const val ALT = InputEvent.ALT_DOWN_MASK
    private const val SHIFT = InputEvent.SHIFT_DOWN_MASK
    private const val META = InputEvent.META_DOWN_MASK
    private const val RELEVANT_MODIFIERS = CTRL or ALT or SHIFT or META

    /**
     * Valores por defecto elegidos para no chocar con la edición de texto:
     * Ctrl+←/→ en un campo de texto salta de palabra, por eso siguiente/anterior
     * y volumen van con Alt.
     */
    val defaults: Map<Action, Binding?> =
        mapOf(
            Action.PLAY_PAUSE to Binding(KeyEvent.VK_SPACE, CTRL),
            Action.NEXT to Binding(KeyEvent.VK_RIGHT, ALT),
            Action.PREVIOUS to Binding(KeyEvent.VK_LEFT, ALT),
            Action.VOLUME_UP to Binding(KeyEvent.VK_UP, ALT),
            Action.VOLUME_DOWN to Binding(KeyEvent.VK_DOWN, ALT),
            Action.LIKE to Binding(KeyEvent.VK_L, CTRL),
            Action.MINI_PLAYER to Binding(KeyEvent.VK_M, CTRL),
        )

    private val prefs: Preferences by lazy { Preferences.userRoot().node("Wavora/Shortcuts") }

    private val _enabled = MutableStateFlow(readEnabled())
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private val _bindings = MutableStateFlow(readBindings())
    val bindings: StateFlow<Map<Action, Binding?>> = _bindings.asStateFlow()

    /** Acción que está esperando una combinación nueva (pantalla de Ajustes), o null. */
    private val _capturing = MutableStateFlow<Action?>(null)
    val capturing: StateFlow<Action?> = _capturing.asStateFlow()

    /** Último aviso al capturar (ej. "usá Ctrl o Alt"), para mostrar en Ajustes. */
    private val _captureError = MutableStateFlow<CaptureError?>(null)
    val captureError: StateFlow<CaptureError?> = _captureError.asStateFlow()

    enum class CaptureError { NEEDS_MODIFIER }

    @Volatile
    private var mainWindow: Window? = null

    @Volatile
    private var handler: ((Action) -> Unit)? = null

    @Volatile
    private var installed = false

    private val dispatcher =
        KeyEventDispatcher { event ->
            try {
                dispatch(event)
            } catch (e: Throwable) {
                Logger.e(TAG, "Error procesando atajo: ${e.message}", e)
                false
            }
        }

    /** Lo llama DesktopApp cuando la ventana principal existe y la app está lista. */
    fun install(
        window: Window,
        onAction: (Action) -> Unit,
    ) {
        mainWindow = window
        handler = onAction
        if (!installed) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(dispatcher)
            installed = true
        }
    }

    fun uninstall() {
        if (installed) {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(dispatcher)
            installed = false
        }
        handler = null
        mainWindow = null
    }

    private fun dispatch(event: KeyEvent): Boolean {
        val window = mainWindow ?: return false
        // Solo teclas de la ventana principal (no el mini player, ni el login).
        val component = event.component ?: return false
        val source = (component as? Window) ?: javax.swing.SwingUtilities.getWindowAncestor(component)
        if (source !== window) return false

        val capture = _capturing.value
        if (capture != null) return handleCapture(capture, event)

        if (!_enabled.value || event.id != KeyEvent.KEY_PRESSED) return false
        val pressed = Binding(event.keyCode, event.modifiersEx and RELEVANT_MODIFIERS)
        val action = _bindings.value.entries.firstOrNull { it.value == pressed }?.key ?: return false
        handler?.invoke(action) ?: return false
        event.consume()
        return true
    }

    private fun handleCapture(
        action: Action,
        event: KeyEvent,
    ): Boolean {
        // Mientras se captura, la ventana no recibe ninguna tecla.
        if (event.id != KeyEvent.KEY_PRESSED) return true
        val code = event.keyCode
        if (code == KeyEvent.VK_ESCAPE) {
            cancelCapture()
            return true
        }
        // Se espera a la tecla "real" (las de modificador solas no cuentan).
        if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT || code == KeyEvent.VK_SHIFT ||
            code == KeyEvent.VK_META || code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_UNDEFINED
        ) {
            return true
        }
        val mods = event.modifiersEx and RELEVANT_MODIFIERS
        val isFunctionKey = code in KeyEvent.VK_F1..KeyEvent.VK_F12
        if (!isFunctionKey && (mods and (CTRL or ALT or META)) == 0) {
            _captureError.value = CaptureError.NEEDS_MODIFIER
            return true
        }
        setBinding(action, Binding(code, mods))
        _capturing.value = null
        _captureError.value = null
        return true
    }

    fun startCapture(action: Action) {
        _captureError.value = null
        _capturing.value = action
    }

    fun cancelCapture() {
        _capturing.value = null
        _captureError.value = null
    }

    fun setEnabled(value: Boolean) {
        _enabled.value = value
        runCatching {
            prefs.putBoolean(KEY_ENABLED, value)
            prefs.flush()
        }
    }

    /** Asigna (o quita, con null) un atajo. Si la combinación la usaba otra acción, se le quita a esa. */
    fun setBinding(
        action: Action,
        binding: Binding?,
    ) {
        val updated = _bindings.value.toMutableMap()
        if (binding != null) {
            updated.entries.filter { it.key != action && it.value == binding }.forEach { updated[it.key] = null }
        }
        updated[action] = binding
        _bindings.value = updated
        saveBindings(updated)
    }

    fun restoreDefaults() {
        _bindings.value = defaults
        saveBindings(defaults)
        setEnabled(true)
    }

    private fun readEnabled(): Boolean = runCatching { prefs.getBoolean(KEY_ENABLED, true) }.getOrDefault(true)

    private fun readBindings(): Map<Action, Binding?> =
        Action.entries.associateWith { action ->
            val raw = runCatching { prefs.get(action.name, null) }.getOrNull()
            when {
                raw == null -> defaults[action] // nunca configurado: valor por defecto
                raw.isEmpty() -> null // quitado a propósito por el usuario
                else -> Binding.parse(raw) ?: defaults[action]
            }
        }

    private fun saveBindings(map: Map<Action, Binding?>) {
        runCatching {
            map.forEach { (action, binding) -> prefs.put(action.name, binding?.serialize() ?: "") }
            prefs.flush()
        }
    }
}
