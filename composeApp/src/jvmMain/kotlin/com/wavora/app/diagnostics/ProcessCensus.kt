package com.wavora.app.diagnostics

import com.wavora.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "ProcessCensus"

// Instrumentación del Problema 2 (varios "wavora.exe" en el primer arranque).
// Usa java.lang.ProcessHandle (Java 9+, sin dependencias nuevas) para volcar
// PID, PID del padre y línea de comandos completa de todo proceso relevante.
//
// VERIFICAR: ProcessHandle.Info.commandLine() puede devolver Optional.empty()
// en Windows para procesos ajenos sin privilegios suficientes — se maneja
// explícitamente, no se asume vacío silenciosamente.
object ProcessCensus {
    fun snapshot(label: String, needles: List<String> = listOf("wavora", "vlc", "jcef", "cef_", "chrome")) {
        try {
            val all = ProcessHandle.allProcesses().toList()
            val matches = all.filter { ph ->
                val cmd = ph.info().commandLine().orElse("") + " " + ph.info().command().orElse("")
                needles.any { needle -> cmd.contains(needle, ignoreCase = true) }
            }
            Logger.d(TAG, "===== SNAPSHOT [$label] - ${matches.size} proceso(s) relevante(s) de ${all.size} totales =====")
            if (matches.isEmpty()) {
                Logger.d(TAG, "(ninguno coincide con $needles en este instante)")
            }
            matches.sortedBy { it.pid() }.forEach { ph ->
                val info = ph.info()
                val pid = ph.pid()
                val parentPid = ph.parent().map { it.pid() }.orElse(-1L)
                val cmd = info.command().orElse("?")
                val cmdLine = info.commandLine().orElse("(no se pudo leer commandLine)")
                val startInstant = info.startInstant().orElse(null)
                val cpuDuration = info.totalCpuDuration().orElse(null)
                Logger.d(
                    TAG,
                    "PID=$pid PPID=$parentPid start=$startInstant cpuAcumulado=$cpuDuration cmd=$cmd cmdLine=$cmdLine",
                )
            }
            Logger.d(TAG, "===== FIN SNAPSHOT [$label] =====")
        } catch (e: Throwable) {
            Logger.e(TAG, "No se pudo tomar el snapshot de procesos: ${e.message}", e)
        }
    }

    /** Snapshots repetidos durante los primeros [totalMs] ms — cubre la ventana del primer arranque. */
    fun startPeriodicSnapshots(
        scope: CoroutineScope,
        totalMs: Long = 120_000,
        intervalMs: Long = 5_000,
    ) {
        scope.launch(Dispatchers.Default) {
            var elapsed = 0L
            while (isActive && elapsed < totalMs) {
                snapshot(label = "t+${elapsed / 1000}s")
                delay(intervalMs)
                elapsed += intervalMs
            }
        }
    }
}
