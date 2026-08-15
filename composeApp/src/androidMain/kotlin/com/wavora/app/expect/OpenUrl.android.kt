package com.wavora.app.expect

import android.content.Intent
import android.content.Intent.FLAG_ACTIVITY_NEW_TASK
import androidx.appcompat.app.AppCompatActivity
import androidx.core.net.toUri
import org.koin.mp.KoinPlatform.getKoin

actual fun openUrl(url: String) {
    val context: AppCompatActivity = getKoin().get()
    val browserIntent =
        Intent(
            Intent.ACTION_VIEW,
            url.toUri(),
        )
    browserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(browserIntent)
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    val context: AppCompatActivity = getKoin().get()
    val shareIntent = Intent(Intent.ACTION_SEND)
    shareIntent.type = "text/plain"
    shareIntent.putExtra(Intent.EXTRA_TEXT, url)
    shareIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
    val chooserIntent =
        Intent.createChooser(shareIntent, title)
    context.startActivity(chooserIntent)
}

actual fun shareLogs(): Boolean {
    val context: AppCompatActivity = getKoin().get()
    // El archivo REAL que está escribiendo el writer activo (instalado en
    // WavoraApplication.onCreate()) - no uno recalculado acá, que apuntaría
    // a un timestamp distinto que todavía no existe en disco.
    val logFile = com.wavora.app.diagnostics.AuditFileLogWriter.activeLogFile
    if (logFile == null || !logFile.exists() || logFile.length() == 0L) return false

    return try {
        val uri =
            androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.FileProvider",
                logFile,
            )
        val shareIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Wavora - log de diagnóstico")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                setFlags(FLAG_ACTIVITY_NEW_TASK)
            }
        val chooserIntent = Intent.createChooser(shareIntent, "Compartir log de Wavora")
        chooserIntent.setFlags(FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooserIntent)
        true
    } catch (e: Exception) {
        false
    }
}