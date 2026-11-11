package com.wavora.app.render

import com.wavora.logger.Logger
import java.awt.Color
import java.awt.Frame
import java.awt.Robot
import java.awt.Window
import java.io.File
import javax.swing.JOptionPane
import javax.swing.SwingUtilities
import kotlin.math.abs

/**
 * Verifica que la ventana REALMENTE se vea cuando se usa Direct3D.
 *
 * El bug viejo no era un crash: la app corría pero la ventana quedaba en
 * blanco/transparente. Eso no se puede detectar mirando excepciones, así que
 * se leen 3 píxeles de la barra de título (CustomTitleBar pinta #0B0B0F
 * sólido): si ninguno tiene ese color con la ventana activa y al frente, lo
 * que hay en pantalla no es Wavora.
 *
 * Devuelve true (se ve), false (no se ve) o null (no se puede saber ahora:
 * minimizada, sin foco, tapada, etc.; se reintenta).
 */
object WindowRenderCheck {
    private const val TAG = "WindowRenderCheck"
    private val TITLE_BAR = Color(0x0B, 0x0B, 0x0F)
    private const val TOLERANCE = 12

    /** Centro vertical de CustomTitleBar (44dp de alto). En Windows, 1dp = 1 unidad AWT. */
    private const val TITLE_BAR_CENTER_Y = 22

    fun check(window: Window): Boolean? =
        try {
            when {
                !window.isShowing || !window.isActive -> null
                window is Frame && (window.extendedState and Frame.ICONIFIED) != 0 -> null
                window.width < 300 -> null
                else -> {
                    val robot = Robot()
                    val origin = window.locationOnScreen
                    val matches =
                        listOf(0.3, 0.5, 0.7).count { fraction ->
                            val x = origin.x + (window.width * fraction).toInt()
                            val y = origin.y + TITLE_BAR_CENTER_Y
                            robot.getPixelColor(x, y).isClose(TITLE_BAR)
                        }
                    matches > 0
                }
            }
        } catch (e: Throwable) {
            Logger.w(TAG, "No se pudo verificar la ventana: ${e.message}")
            null
        }

    private fun Color.isClose(other: Color): Boolean =
        abs(red - other.red) <= TOLERANCE &&
            abs(green - other.green) <= TOLERANCE &&
            abs(blue - other.blue) <= TOLERANCE

    /**
     * Aviso nativo (Swing, NO Compose): se ve aunque Compose no esté pintando.
     * Ofrece reiniciar ya en modo compatible.
     */
    fun showFallbackDialog(onExit: () -> Unit) {
        SwingUtilities.invokeLater {
            val options = arrayOf("Reiniciar ahora", "Más tarde")
            val choice =
                JOptionPane.showOptionDialog(
                    null,
                    "Wavora no se pudo mostrar bien con la aceleración por hardware en esta PC.\n" +
                        "La próxima vez se va a abrir en modo compatible (sin aceleración).\n\n" +
                        "Podés volver a activarla en Ajustes > Aceleración por hardware.",
                    "Wavora",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.WARNING_MESSAGE,
                    null,
                    options,
                    options[0],
                )
            if (choice == 0) {
                AppRestart.scheduleRelaunch()
                onExit()
            }
        }
    }
}

/**
 * Reinicio de la app (para aplicar un cambio de aceleración por hardware).
 *
 * Wavora tiene una guarda de instancia única: si se lanzara la nueva instancia
 * mientras esta sigue viva, la nueva solo le pediría a esta que se muestre y
 * se cerraría. Por eso se lanza a través de `cmd` con ~2 s de espera, y esta
 * instancia se cierra enseguida. Los procesos hijos heredan la identidad del
 * paquete MSIX, así que la nueva instancia arranca igual que desde el menú
 * Inicio.
 */
object AppRestart {
    private const val TAG = "AppRestart"

    /**
     * Cierre ordenado de la app (libera el reproductor y sale). Lo registra
     * WavoraMainWindow en DesktopApp.kt; si no está, se sale del proceso.
     */
    @Volatile
    var exitHandler: (() -> Unit)? = null

    /** Relanza (si se puede) y cierra esta instancia. */
    fun restartNow() {
        scheduleRelaunch()
        val exit = exitHandler
        if (exit != null) exit() else kotlin.system.exitProcess(0)
    }

    /** Devuelve false si no se puede relanzar (ej. corriendo desde el IDE): el usuario la abre a mano. */
    fun scheduleRelaunch(): Boolean {
        if (!RenderConfig.isWindows) return false
        return try {
            val exe = ProcessHandle.current().info().command().orElse(null) ?: return false
            val name = File(exe).name.lowercase()
            if (!name.endsWith(".exe") || name == "java.exe" || name == "javaw.exe") return false
            // ping como espera: `timeout` falla sin consola. Mismo armado de
            // comando como string único que ya usa WindowsProtocolRegistrar.
            ProcessBuilder("cmd.exe", "/c", "ping -n 3 127.0.0.1 >nul & start \"\" \"$exe\"")
                .redirectErrorStream(true)
                .start()
            true
        } catch (e: Exception) {
            Logger.e(TAG, "No se pudo relanzar la app: ${e.message}", e)
            false
        }
    }
}
