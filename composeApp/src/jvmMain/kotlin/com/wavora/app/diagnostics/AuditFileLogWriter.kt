package com.wavora.app.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Logging persistente en Desktop (pedido explícito de devgodot: hoy Logger.kt
// envuelve co.touchlab.kermit.Logger sin ningún LogWriter de archivo — Kermit
// usa su writer por default en JVM, que solo imprime a stdout, así que entre
// sesiones no queda ningún rastro. Este LogWriter cubre eso.
//
// OPTIMIZACIÓN (v2.3): antes cada línea se escribía con un PrintWriter con
// autoFlush=true DESDE EL HILO QUE LOGUEABA (UI, hilo de VLC, etc.): una
// escritura + flush a disco sincrónica por cada log, incluidos los de debug
// del reproductor. Ahora:
//  - las líneas se encolan y las escribe un único hilo de fondo con buffer
//    (ver [AsyncLogFile]); el que loguea nunca toca el disco;
//  - Error/Assert se escriben y se vacían a disco antes de volver, así un
//    crash (CrashDialog hace exitProcess) nunca pierde su stack trace;
//  - en un cierre normal, un shutdown hook vacía lo pendiente;
//  - se borran los logs viejos (ver [AsyncLogFile.pruneOldLogs]).
// El nivel mínimo (Warn en uso normal, Debug con el modo diagnóstico) lo
// decide [DiagnosticsConfig] en DesktopApp.kt, no este writer.
class AuditFileLogWriter : LogWriter() {
    companion object {
        val logDir: File by lazy {
            val base = System.getenv("LOCALAPPDATA")?.let { File(it, "Wavora/logs") }
                ?: File(System.getProperty("user.home"), ".wavora/logs")
            base.apply { mkdirs() }
        }

        // Un archivo por sesión de la app (no por día), para poder mandar
        // "el log de esta corrida" sin tener que recortarlo a mano.
        val logFile: File by lazy {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            File(logDir, "wavora-$ts.log")
        }
    }

    private val output: AsyncLogFile by lazy {
        AsyncLogFile.pruneOldLogs(logDir, prefix = "wavora-", keep = logFile)
        AsyncLogFile(logFile)
    }

    private val timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    override fun log(
        severity: Severity,
        message: String,
        tag: String,
        throwable: Throwable?,
    ) {
        val ts = LocalDateTime.now().format(timeFmt)
        val threadName = Thread.currentThread().name
        val line = "$ts [$threadName] ${severity.name.padEnd(5)} $tag: $message"
        val text = if (throwable != null) line + "\n" + throwable.stackTraceToString() else line
        output.write(text, flushNow = severity >= Severity.Error)
    }
}
