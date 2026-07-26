package com.wavora.app

import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime

/**
 * Thin entry point for Wavora Desktop.
 *
 * All window setup, VLC bootstrap, Sentry init, Koin loading, deep link
 * handling, mini-player wiring, and tray integration live in
 * `composeApp/src/jvmMain/.../main.kt` as `fun runDesktopApp()`. That keeps
 * the shared module self-contained (it can still be launched directly
 * during development) while letting this :desktopApp module own the JVM
 * launcher class Conveyor / compose.desktop point `mainClass` at.
 *
 * The :composeApp KMP library exposes runDesktopApp() publicly so this
 * stub can delegate without duplicating any window-construction logic.
 */
fun main(args: Array<String>) {
    // AUDIT INSTRUMENTATION - PUNTO 1 (pedido explícito, solo evidencia, sin
    // fix funcional): marca si el proceso llega hasta acá, justo antes de
    // llamar a runDesktopApp(). Se escribe con un FileWriter propio - no con
    // Logger/Kermit - porque el log writer recién se instala DENTRO de
    // runDesktopApp(); si el proceso muriera o se desviara antes de eso,
    // este es el único registro que quedaría. Reusa la misma carpeta que
    // AuditFileLogWriter (com.wavora.app.diagnostics.AuditFileLogWriter.logDir)
    // para que todo el rastro de una misma corrida quede junto.
    try {
        val dir = com.wavora.app.diagnostics.AuditFileLogWriter.logDir
        dir.mkdirs()
        val handle = ProcessHandle.current()
        val info = handle.info()
        val pid = handle.pid()
        val ppid = handle.parent().map { it.pid() }.orElse(-1L)
        val execPath = info.command().orElse("(no disponible)")
        val fullCommandLine = info.commandLine().orElse("(no disponible)")
        val marker = File(dir, "early-marker-$pid.log")
        PrintWriter(FileWriter(marker, true), true).use { w ->
            w.println("${LocalDateTime.now()} [main] PUNTO 1 - ANTES de llamar a runDesktopApp()")
            w.println("  PID=$pid PPID=$ppid")
            w.println("  Ejecutable=$execPath")
            w.println("  Command line completa=$fullCommandLine")
            w.println("  args recibidos por main()=${args.joinToString(" ")}")
        }
    } catch (e: Throwable) {
        System.err.println("No se pudo escribir el marcador temprano (PUNTO 1): ${e.message}")
    }

    // FIX (auditoría de estabilidad Desktop, evidencia acumulada - ver
    // comentario largo abajo antes de tocar esto): si este proceso fue
    // arrancado por CEF con flags de subproceso (--type=gpu-process,
    // --type=utility, --type=renderer, etc.), NO debe ejecutar la app
    // completa. Confirmado con Process Monitor (evento "Process Create",
    // columna Path a nivel de sistema operativo, no un log de la propia
    // app) que browser_subprocess_path apunta correctamente a
    // jcef_helper.exe (verificado existente en disco, tamaño válido) pero
    // libcef.dll de todas formas relanza Wavora.exe para el 100% de los
    // subprocesos observados (277/277). KCEF no expone ninguna función
    // pública equivalente a CefExecuteProcess() para que este proceso
    // pueda servir de subproceso nativo real - por lo tanto no hay forma,
    // con esta versión de la librería, de que este relanzamiento funcione
    // como un subproceso legítimo de Chromium. Antes de este fix, cada uno
    // de estos relanzamientos ejecutaba la app Kotlin/Compose completa
    // (Koin, VLC, Sentry, ventana, y un KCEF.init() propio) - eso es la
    // causa confirmada (log "Flags de CEF" en DesktopApp.kt, 306/306 casos)
    // de la multiplicación de procesos wavora.exe y el consumo de CPU
    // reportado. Salir acá no interrumpe nada que antes funcionara: estos
    // procesos ya estaban rotos, esto solo evita que hagan trabajo inútil.
    if (args.any { it.startsWith("--type=") }) {
        return
    }

    runDesktopApp(args)
}
