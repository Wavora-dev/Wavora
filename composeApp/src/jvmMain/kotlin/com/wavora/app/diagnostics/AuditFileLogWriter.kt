package com.wavora.app.diagnostics

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Logging persistente en Desktop (pedido explícito de devgodot: hoy Logger.kt
// envuelve co.touchlab.kermit.Logger sin ningún LogWriter de archivo — Kermit
// usa su writer por default en JVM, que solo imprime a stdout, así que entre
// sesiones no queda ningún rastro. Este LogWriter cubre eso.
//
// VERIFICAR al compilar: la firma de LogWriter.log(...) corresponde a kermit
// 2.1.0 (gradle/libs.versions.toml:59). No se pudo compilar en el entorno de
// auditoría (sin Windows/GUI) para confirmarlo con el IDE.
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

    // autoFlush = true: si el proceso queda "Suspendido" o hay que matarlo a
    // la fuerza, el archivo tiene que seguir teniendo lo escrito hasta ese
    // instante — es la evidencia que se necesita justo en esos casos.
    private val writer: PrintWriter by lazy {
        PrintWriter(FileWriter(logFile, true), true)
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
        synchronized(this) {
            writer.println(line)
            throwable?.let { writer.println(it.stackTraceToString()) }
        }
    }
}
