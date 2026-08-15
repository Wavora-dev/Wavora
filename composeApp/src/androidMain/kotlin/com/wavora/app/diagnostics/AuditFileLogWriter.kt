package com.wavora.app.diagnostics

import android.content.Context
import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// Puerto a Android de composeApp/src/jvmMain/kotlin/com/wavora/app/diagnostics/
// AuditFileLogWriter.kt (Desktop). Android nunca tuvo un LogWriter de archivo -
// Kermit ahí solo mandaba todo a Logcat, que es inútil para pedirle un log a
// un usuario de la app que no tiene ADB ni PC (ej: el problema de Chromecast,
// que solo se puede reproducir en el celular de un usuario real). Este writer
// deja el mismo rastro persistente que ya existe en Desktop, pero en
// almacenamiento interno de la app (`context.filesDir/logs`), y
// SettingScreen.kt expone un botón "Compartir logs de diagnóstico" que
// dispara el share sheet estándar de Android sobre el archivo de la sesión
// actual - así cualquier usuario puede mandarlo por WhatsApp/mail sin tocar
// una computadora.
//
// VERIFICAR al compilar: misma salvedad que la versión de Desktop - la firma
// de LogWriter.log(...) corresponde a kermit 2.1.0 (gradle/libs.versions.toml),
// no se pudo compilar en el entorno de auditoría (sin Android SDK) para
// confirmarlo con el IDE.
class AuditFileLogWriter(
    context: Context,
) : LogWriter() {
    companion object {
        // internal (filesDir), NO external: no requiere permisos de
        // almacenamiento en ninguna versión de Android, y ya está cubierto
        // por el <files-path> del FileProvider existente (provider_paths.xml)
        // para poder compartirlo sin exponer el resto de filesDir.
        fun logDir(context: Context): File = File(context.filesDir, "logs").apply { mkdirs() }

        // AUDIT FIX: el archivo real que está escribiendo la instancia
        // activa del writer, NO un timestamp recalculado en cada llamada -
        // si `shareLogs()` (OpenUrl.android.kt) generara su propio nombre de
        // archivo con la hora actual, apuntaría a un archivo que todavía no
        // existe (el real se creó al arrancar la app, con OTRO timestamp).
        // `null` hasta que el writer se instala en WavoraApplication.onCreate().
        @Volatile
        var activeLogFile: File? = null
            private set
    }

    // Un archivo por sesión de la app (no por día) - mismo criterio que la
    // versión de Desktop.
    private val logFile: File =
        run {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
            File(logDir(context), "wavora-android-$ts.log").also { activeLogFile = it }
        }

    // autoFlush = true: si la app se va a background y el sistema la mata
    // (muy común en un celular random de un usuario, no bajo nuestro
    // control), el archivo tiene que tener escrito hasta el último evento
    // antes de eso - es justo la evidencia que se necesita en ese caso.
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
