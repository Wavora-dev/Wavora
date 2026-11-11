package com.wavora.app.diagnostics

import java.util.prefs.Preferences

/**
 * Modo diagnóstico de Desktop (v2.3).
 *
 * ANTES, en todas las instalaciones y siempre:
 *  - se escribían a disco TODOS los logs (incluidos los de debug del
 *    reproductor, varios por segundo), uno por uno y con flush inmediato;
 *  - CpuThreadSampler medía la CPU de cada hilo cada 5 s mientras la app
 *    estuviera abierta (y activaba la medición de CPU por hilo de la JVM);
 *  - ProcessCensus recorría todos los procesos del sistema cada 5 s durante
 *    los primeros 2 minutos (justo cuando la app compite por CPU al arrancar).
 *
 * Eso era instrumentación para investigar bugs puntuales. AHORA queda detrás
 * de este interruptor:
 *  - apagado (por defecto): al archivo solo van Warn/Error, sin samplers;
 *  - encendido: exactamente lo de antes (Debug + samplers), para pedirle un
 *    log detallado a un usuario.
 *
 * Se activa con Ajustes > Modo diagnóstico (requiere reiniciar), con la
 * variable de entorno WAVORA_DIAGNOSTICS=1 o con el argumento --diagnostics.
 * Se guarda en java.util.prefs porque se necesita antes de Koin/DataStore.
 */
object DiagnosticsConfig {
    private const val KEY_ENABLED = "enabled"

    private val prefs: Preferences by lazy { Preferences.userRoot().node("Wavora/Diagnostics") }

    /** Lo elegido en Ajustes (se aplica en el próximo arranque). */
    var enabledSetting: Boolean
        get() = runCatching { prefs.getBoolean(KEY_ENABLED, false) }.getOrDefault(false)
        set(value) {
            runCatching {
                prefs.putBoolean(KEY_ENABLED, value)
                prefs.flush()
            }
        }

    /** Si ESTE arranque corre con diagnóstico completo. Se fija una vez en [resolve]. */
    @Volatile
    var isActive: Boolean = false
        private set

    fun resolve(args: Array<String>): Boolean {
        isActive =
            enabledSetting ||
            System.getenv("WAVORA_DIAGNOSTICS") == "1" ||
            args.any { it.equals("--diagnostics", ignoreCase = true) }
        return isActive
    }
}
