package com.wavora.app.render

import com.sun.jna.platform.win32.Advapi32Util
import com.sun.jna.platform.win32.WinReg
import com.wavora.logger.Logger
import java.lang.management.ManagementFactory
import java.util.prefs.Preferences

/**
 * Aceleración por hardware en Windows (v2.3).
 *
 * ANTES: en Windows se forzaba `skiko.renderApi=SOFTWARE` para TODAS las PCs,
 * porque en algunas (Intel integradas, AMD viejas, algunas NVIDIA) la ventana
 * salía en blanco/invisible. Funcionaba, pero toda la UI (incluidos los blur)
 * se dibujaba con la CPU en todas las máquinas, también en las que no tenían
 * ningún problema.
 *
 * AHORA, antes de crear la primera ventana (lo que sigue tarda < 5 ms, el
 * usuario no lo nota), se decide qué usar:
 *
 * 1. Lo que eligió el usuario en Ajustes > Aceleración por hardware
 *    (Automático / Activada / Desactivada). `--software-render` en la línea de
 *    comandos fuerza software siempre (salida de emergencia).
 * 2. Si el último arranque con aceleración nunca se confirmó (la app se
 *    colgó, crasheó o se cerró antes de verificar que la ventana se veía), se
 *    vuelve a software automáticamente y se recuerda.
 * 3. En Automático se mira la placa de video desde el registro de Windows
 *    (instantáneo, sin abrir procesos ni PowerShell): si no hay una GPU real
 *    con driver (adaptador básico de Microsoft, escritorio remoto, máquina
 *    virtual) o el driver es anterior a [MIN_DRIVER_YEAR] -los casos donde
 *    aparecía la ventana en blanco- se usa software; si no, Direct3D.
 *
 * Una vez abierta la ventana con Direct3D, [WindowRenderCheck] mira unos
 * pocos píxeles de la barra de título: si no se ve lo que la app dibujó
 * (ventana en blanco), se marca la aceleración como fallida, se avisa con un
 * diálogo nativo (Swing, visible aunque Compose no pinte) y el siguiente
 * arranque ya es en software.
 *
 * Todo se guarda en java.util.prefs (registro del usuario), que se puede leer
 * antes de Koin/DataStore, justo cuando hace falta.
 *
 * En macOS/Linux no cambia nada respecto de antes.
 */
object RenderConfig {
    private const val TAG = "RenderConfig"
    private const val MIN_DRIVER_YEAR = 2018

    private const val KEY_MODE = "mode"
    private const val KEY_PROBE_PENDING = "probePending"
    private const val KEY_HW_BLOCKED = "hardwareBlocked"
    private const val KEY_BLOCK_REASON = "blockReason"
    private const val KEY_LAST_API = "lastApi"

    enum class Mode { AUTO, HARDWARE, SOFTWARE }

    enum class Api { DIRECT3D, SOFTWARE }

    data class Decision(
        val api: Api,
        val reason: String,
    )

    data class MachineInfo(
        val gpus: List<GpuInfo>,
        val totalRamMb: Long,
        val cpuCores: Int,
        val remoteSession: Boolean,
    ) {
        /** PC modesta: con poca CPU, dibujar por software es justamente lo que más pesa. */
        val isLowEnd: Boolean get() = (totalRamMb in 1..4608) || cpuCores <= 2
    }

    data class GpuInfo(
        val name: String,
        val driverYear: Int?,
        val isVirtualOrBasic: Boolean,
    )

    private val prefs: Preferences by lazy { Preferences.userRoot().node("Wavora/Render") }

    val isWindows: Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    /** Decisión de este arranque (null hasta llamar a [decideAndApply], o fuera de Windows). */
    @Volatile
    var current: Decision? = null
        private set

    @Volatile
    var machine: MachineInfo? = null
        private set

    /** true si este arranque usa Direct3D y todavía falta confirmar que la ventana se ve. */
    @Volatile
    var awaitingConfirmation: Boolean = false
        private set

    /**
     * Motivo por el que en el arranque anterior se desactivó sola la
     * aceleración (para mostrarlo en Ajustes / un aviso), o null.
     */
    @Volatile
    var fallbackNotice: String? = null
        private set

    var mode: Mode
        get() = runCatching { Mode.valueOf(prefs.get(KEY_MODE, Mode.AUTO.name)) }.getOrDefault(Mode.AUTO)
        set(value) {
            runCatching {
                prefs.put(KEY_MODE, value.name)
                // Elegir algo a mano "perdona" un fallo anterior: el usuario manda.
                if (value != Mode.AUTO) {
                    prefs.putBoolean(KEY_HW_BLOCKED, false)
                    prefs.remove(KEY_BLOCK_REASON)
                }
                prefs.flush()
            }
        }

    val hardwareBlocked: Boolean get() = runCatching { prefs.getBoolean(KEY_HW_BLOCKED, false) }.getOrDefault(false)

    val blockReason: String? get() = runCatching { prefs.get(KEY_BLOCK_REASON, null) }.getOrNull()

    /** API que efectivamente reportó Skia la última vez que se verificó (informativo). */
    val lastApi: String? get() = runCatching { prefs.get(KEY_LAST_API, null) }.getOrNull()

    /**
     * Se llama UNA vez, al principio de runDesktopApp(), antes de crear
     * cualquier ventana. Setea `skiko.renderApi` y devuelve la decisión.
     * Nunca tira excepción: ante cualquier problema, software (lo de antes).
     */
    fun decideAndApply(args: Array<String>): Decision? {
        if (!isWindows) return null
        val decision =
            try {
                decide(args)
            } catch (e: Throwable) {
                Decision(Api.SOFTWARE, "error al decidir (${e.message}) - se usa software")
            }
        val apiProperty = if (decision.api == Api.DIRECT3D) "DIRECT3D" else "SOFTWARE"
        System.setProperty("skiko.renderApi", apiProperty)
        current = decision
        awaitingConfirmation = decision.api == Api.DIRECT3D
        if (awaitingConfirmation) {
            // Marca "en prueba": si esta sesión muere antes de confirmar, el
            // próximo arranque lo ve y vuelve a software solo.
            runCatching {
                prefs.putBoolean(KEY_PROBE_PENDING, true)
                prefs.flush()
            }
        }
        Logger.w(TAG, "Render: $apiProperty - ${decision.reason} (modo=${mode.name}, equipo=${machine})")
        return decision
    }

    private fun decide(args: Array<String>): Decision {
        val info = readMachineInfo().also { machine = it }

        if (args.any { it.equals("--software-render", ignoreCase = true) }) {
            return Decision(Api.SOFTWARE, "--software-render en la línea de comandos")
        }

        // ¿El arranque anterior con aceleración quedó sin confirmar? => falló.
        if (prefs.getBoolean(KEY_PROBE_PENDING, false)) {
            markBlocked("el arranque anterior con aceleración no llegó a mostrarse bien")
            if (mode == Mode.HARDWARE) prefs.put(KEY_MODE, Mode.AUTO.name)
            prefs.putBoolean(KEY_PROBE_PENDING, false)
            prefs.flush()
            fallbackNotice = blockReason
            return Decision(Api.SOFTWARE, "fallback automático: ${blockReason}")
        }

        return when (mode) {
            Mode.SOFTWARE -> Decision(Api.SOFTWARE, "elegido por el usuario")
            Mode.HARDWARE -> Decision(Api.DIRECT3D, "elegido por el usuario")
            Mode.AUTO -> autoDecision(info)
        }
    }

    private fun autoDecision(info: MachineInfo): Decision {
        if (hardwareBlocked) return Decision(Api.SOFTWARE, "aceleración desactivada antes: ${blockReason ?: "falló"}")
        if (info.remoteSession) return Decision(Api.SOFTWARE, "sesión de escritorio remoto")
        val realGpus = info.gpus.filterNot { it.isVirtualOrBasic }
        if (realGpus.isEmpty()) return Decision(Api.SOFTWARE, "no hay placa de video con driver")
        val newestDriver = realGpus.mapNotNull { it.driverYear }.maxOrNull()
        if (newestDriver != null && newestDriver < MIN_DRIVER_YEAR) {
            return Decision(Api.SOFTWARE, "driver de video de $newestDriver (anterior a $MIN_DRIVER_YEAR)")
        }
        val suffix = if (info.isLowEnd) " - PC modesta: la GPU alivia mucho a la CPU" else ""
        return Decision(Api.DIRECT3D, "GPU ${realGpus.first().name}$suffix")
    }

    /** La ventana se vio bien con aceleración: se confirma para los próximos arranques. */
    fun confirmHardwareWorks(reportedApi: String?) {
        if (!awaitingConfirmation) return
        awaitingConfirmation = false
        runCatching {
            prefs.putBoolean(KEY_PROBE_PENDING, false)
            if (reportedApi != null) prefs.put(KEY_LAST_API, reportedApi)
            prefs.flush()
        }
        Logger.w(TAG, "Aceleración por hardware confirmada (Skia reporta: $reportedApi)")
    }

    /** La ventana NO se ve con aceleración: el próximo arranque va en software. */
    fun reportHardwareFailure(reason: String) {
        awaitingConfirmation = false
        runCatching {
            markBlocked(reason)
            if (mode == Mode.HARDWARE) prefs.put(KEY_MODE, Mode.AUTO.name)
            prefs.putBoolean(KEY_PROBE_PENDING, false)
            prefs.flush()
        }
        Logger.e(TAG, "Aceleración por hardware desactivada: $reason")
    }

    private fun markBlocked(reason: String) {
        prefs.putBoolean(KEY_HW_BLOCKED, true)
        prefs.put(KEY_BLOCK_REASON, reason)
    }

    fun clearNotice() {
        fallbackNotice = null
    }

    // ---------------------------------------------------------------------
    // Detección rápida del equipo (sin procesos externos)
    // ---------------------------------------------------------------------

    private const val DISPLAY_CLASS_KEY =
        "SYSTEM\\CurrentControlSet\\Control\\Class\\{4d36e968-e325-11ce-bfc1-08002be10318}"

    private val VIRTUAL_OR_BASIC =
        listOf(
            "microsoft basic", "microsoft remote display", "remote desktop", "virtual display",
            "hyper-v", "vmware", "virtualbox", "parsec", "citrix", "meta virtual", "iddsample",
            "spacedesk", "dummy", "indirect display",
        )

    fun readMachineInfo(): MachineInfo {
        val gpus = runCatching { readGpus() }.getOrDefault(emptyList())
        val ramMb =
            runCatching {
                (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)
                    ?.totalMemorySize
                    ?.div(1024 * 1024)
            }.getOrNull() ?: 0L
        val remote = System.getenv("SESSIONNAME")?.startsWith("RDP", ignoreCase = true) == true
        return MachineInfo(
            gpus = gpus,
            totalRamMb = ramMb,
            cpuCores = Runtime.getRuntime().availableProcessors(),
            remoteSession = remote,
        )
    }

    private fun readGpus(): List<GpuInfo> {
        val root = WinReg.HKEY_LOCAL_MACHINE
        val subKeys = Advapi32Util.registryGetKeys(root, DISPLAY_CLASS_KEY)
        return subKeys
            .filter { it.length == 4 && it.all(Char::isDigit) } // 0000, 0001... (no "Properties")
            .mapNotNull { sub ->
                val key = "$DISPLAY_CLASS_KEY\\$sub"
                runCatching {
                    if (!Advapi32Util.registryValueExists(root, key, "DriverDesc")) return@runCatching null
                    val name = Advapi32Util.registryGetStringValue(root, key, "DriverDesc")
                    val date =
                        if (Advapi32Util.registryValueExists(root, key, "DriverDate")) {
                            Advapi32Util.registryGetStringValue(root, key, "DriverDate")
                        } else {
                            null
                        }
                    GpuInfo(
                        name = name,
                        driverYear = date?.let(::parseDriverYear),
                        isVirtualOrBasic = VIRTUAL_OR_BASIC.any { name.contains(it, ignoreCase = true) },
                    )
                }.getOrNull()
            }
    }

    /** DriverDate viene como "M-D-YYYY" (ej. "6-21-2023"). */
    internal fun parseDriverYear(raw: String): Int? =
        raw
            .trim()
            .split('-', '/', '.')
            .lastOrNull()
            ?.trim()
            ?.toIntOrNull()
            ?.takeIf { it in 1990..2100 }
}
