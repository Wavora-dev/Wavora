package com.wavora.app.diagnostics

import com.wavora.logger.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.management.ManagementFactory
import java.lang.management.ThreadMXBean

private const val TAG = "CpuThreadSampler"

// Instrumentación del Problema 3 (CPU 40-50% sostenido). Mide tiempo de CPU
// REAL por hilo con ThreadMXBean.getThreadCpuTime (misma técnica que usa
// VisualVM/JFR), tomando dos muestras y restando — da el % real de CPU que
// cada hilo consumió en la ventana, no una estimación. Con el fix de la
// sección 1 aplicado, esto sirve además para CONFIRMAR que ya no aparecen
// threads "VLC-Player-Thread" o similares acumulando tiempo de forma anómala,
// y para revisar si queda algo más por identificar.
object CpuThreadSampler {
    private val bean: ThreadMXBean = ManagementFactory.getThreadMXBean()
    private val osBean = ManagementFactory.getOperatingSystemMXBean()

    fun startSampling(
        scope: CoroutineScope,
        intervalMs: Long = 5_000,
        topN: Int = 8,
    ) {
        if (!bean.isThreadCpuTimeSupported) {
            Logger.w(TAG, "ThreadMXBean.isThreadCpuTimeSupported()=false en esta JVM - sampler no disponible")
            return
        }
        bean.isThreadCpuTimeEnabled = true

        scope.launch(Dispatchers.Default) {
            var prev: Map<Long, Long> = snapshotCpuTimes()
            var prevWallNs = System.nanoTime()
            while (isActive) {
                delay(intervalMs)
                val now = snapshotCpuTimes()
                val nowWallNs = System.nanoTime()
                val wallElapsedNs = (nowWallNs - prevWallNs).coerceAtLeast(1)

                val deltas =
                    now.mapNotNull { (id, cpuNs) ->
                        val prevNs = prev[id] ?: return@mapNotNull null
                        val deltaNs = cpuNs - prevNs
                        if (deltaNs <= 0) return@mapNotNull null
                        id to deltaNs
                    }.sortedByDescending { it.second }

                val processCpuPct = readProcessCpuLoadPercent()
                val header =
                    if (processCpuPct != null) "CPU proceso total = %.1f%%".format(processCpuPct) else "CPU proceso total = no disponible"
                Logger.d(TAG, "===== SAMPLE ($header, ventana=${wallElapsedNs / 1_000_000}ms) =====")
                deltas.take(topN).forEach { (id, deltaNs) ->
                    val info = bean.getThreadInfo(id)
                    val name = info?.threadName ?: "thread-$id (murió entre muestras)"
                    val state = info?.threadState?.name ?: "?"
                    val pctOfWindow = 100.0 * deltaNs / wallElapsedNs
                    Logger.d(
                        TAG,
                        "  id=$id name=\"$name\" state=$state cpuEnVentana=${deltaNs / 1_000_000}ms (%.1f%% de un core)".format(pctOfWindow),
                    )
                }
                Logger.d(TAG, "===== FIN SAMPLE =====")

                prev = now
                prevWallNs = nowWallNs
            }
        }
    }

    private fun snapshotCpuTimes(): Map<Long, Long> =
        // AUDIT FIX (compilación real, ver log): allThreadIds es LongArray, y
        // el stdlib de Kotlin no genera mapNotNull para arrays primitivos
        // (sí existe para Map/Iterable/Array<T>/Sequence/CharSequence, que
        // eran justamente los candidatos no aplicables que listaba el
        // compilador). toList() lo convierte a List<Long>, que sí lo tiene.
        bean.allThreadIds.toList().mapNotNull { id ->
            val cpuNs = bean.getThreadCpuTime(id)
            if (cpuNs < 0) null else id to cpuNs
        }.toMap()

    private fun readProcessCpuLoadPercent(): Double? =
        try {
            val method = osBean.javaClass.methods.firstOrNull { it.name == "getProcessCpuLoad" }
            method?.isAccessible = true
            val load = method?.invoke(osBean) as? Double
            if (load != null && load >= 0.0) load * 100.0 else null
        } catch (e: Throwable) {
            null
        }
}
