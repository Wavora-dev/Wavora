package com.wavora.app.diagnostics

import com.wavora.logger.Logger

private const val TAG = "StartupTiming"

// Instrumentación del Problema 1 (arranque "congelado"). Mide cuánto tarda
// cada etapa real del bloque de inicio en DesktopApp.kt (Koin, VersionManager,
// Sentry, MediaPlayerHandler, WindowsProtocolRegistrar, SharedViewModel), en
// vez de tratar el arranque como una caja negra. Con esto, la próxima vez que
// el primer inicio se sienta lento, el log dice EXACTAMENTE qué etapa se
// llevó el tiempo — no hace falta adivinar.
object StartupTiming {
    private val stageStarts = mutableMapOf<String, Long>()
    private val appStartNanos = System.nanoTime()

    fun begin(stage: String) {
        stageStarts[stage] = System.nanoTime()
        Logger.d(TAG, "-> INICIO  '$stage' (t+${elapsedMs()}ms desde el arranque)")
    }

    fun end(stage: String) {
        val start = stageStarts[stage]
        val durationMs = if (start != null) (System.nanoTime() - start) / 1_000_000 else -1
        Logger.d(TAG, "<- FIN     '$stage' (duró ${durationMs}ms, t+${elapsedMs()}ms desde el arranque)")
    }

    /** Para bloques que no vale la pena envolver con begin/end por separado. */
    inline fun <T> measure(
        stage: String,
        block: () -> T,
    ): T {
        begin(stage)
        try {
            return block()
        } finally {
            end(stage)
        }
    }

    fun elapsedMs(): Long = (System.nanoTime() - appStartNanos) / 1_000_000
}
