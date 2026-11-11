package com.wavora.app.diagnostics

import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Archivo de log escrito por un único hilo de fondo.
 *
 * - [write] solo encola: el hilo que loguea (UI, VLC, etc.) nunca espera al disco.
 * - El hilo escribe con buffer y vacía a disco cuando la cola queda vacía,
 *   así que en uso normal hay pocas escrituras reales (no una por línea).
 * - `flushNow = true` (errores) escribe la línea y vacía TODO lo pendiente a
 *   disco antes de volver: lo que pasó justo antes de un crash queda guardado.
 * - Un shutdown hook vacía lo pendiente al cerrar la app.
 *
 * Si el disco falla, se deja de escribir en silencio: el log nunca puede
 * tumbar la app.
 */
class AsyncLogFile(
    private val file: File,
) {
    private val queue = LinkedBlockingQueue<String>(MAX_QUEUED_LINES)
    private val lock = Any()

    @Volatile
    private var broken = false

    private val writer: BufferedWriter? =
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8), 32 * 1024)
        } catch (e: Exception) {
            broken = true
            null
        }

    private val thread =
        Thread({ drainLoop() }, "Wavora-LogWriter").apply {
            isDaemon = true
            priority = Thread.MIN_PRIORITY
            start()
        }

    init {
        try {
            Runtime.getRuntime().addShutdownHook(Thread({ flushPending() }, "Wavora-LogFlush"))
        } catch (_: Exception) {
            // Ya se está cerrando la JVM: nada que hacer.
        }
    }

    fun write(
        text: String,
        flushNow: Boolean = false,
    ) {
        if (broken) return
        if (flushNow) {
            synchronized(lock) {
                drainQueueLocked()
                writeLocked(text)
                flushLocked()
            }
        } else {
            // Si la cola se llena (ráfaga enorme de logs) se descarta la línea en
            // vez de bloquear al hilo que loguea.
            queue.offer(text)
        }
    }

    private fun drainLoop() {
        while (!broken) {
            try {
                val first = queue.poll(1, TimeUnit.SECONDS) ?: continue
                synchronized(lock) {
                    writeLocked(first)
                    drainQueueLocked()
                    flushLocked()
                }
            } catch (_: InterruptedException) {
                return
            } catch (_: Exception) {
                broken = true
            }
        }
    }

    private fun flushPending() {
        try {
            synchronized(lock) {
                drainQueueLocked()
                flushLocked()
            }
        } catch (_: Exception) {
        }
    }

    private fun drainQueueLocked() {
        while (true) {
            val next = queue.poll() ?: return
            writeLocked(next)
        }
    }

    private fun writeLocked(text: String) {
        val w = writer ?: return
        try {
            w.write(text)
            w.newLine()
        } catch (_: Exception) {
            broken = true
        }
    }

    private fun flushLocked() {
        try {
            writer?.flush()
        } catch (_: Exception) {
            broken = true
        }
    }

    companion object {
        private const val MAX_QUEUED_LINES = 20_000
        private const val MAX_LOG_FILES = 10
        private const val MAX_LOG_AGE_MS = 14L * 24 * 60 * 60 * 1000

        /**
         * Antes se creaba un archivo por sesión y nunca se borraba ninguno.
         * Ahora se conservan los últimos [MAX_LOG_FILES] y nada de más de 14 días.
         */
        fun pruneOldLogs(
            dir: File,
            prefix: String,
            keep: File,
        ) {
            try {
                val now = System.currentTimeMillis()
                val logs =
                    dir
                        .listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(".log") }
                        ?.filter { it.absolutePath != keep.absolutePath }
                        ?.sortedByDescending { it.lastModified() }
                        ?: return
                logs.forEachIndexed { index, f ->
                    // index + 1 porque el archivo de esta sesión también cuenta.
                    if (index + 1 >= MAX_LOG_FILES || now - f.lastModified() > MAX_LOG_AGE_MS) {
                        f.delete()
                    }
                }
            } catch (_: Exception) {
            }
        }
    }
}
