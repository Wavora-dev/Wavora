package com.wavora.app.expect

import multiplatform.network.cmptoast.showToast
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.Clipboard
import java.awt.datatransfer.StringSelection
import java.net.URI

actual fun openUrl(url: String) {
    if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
        Desktop.getDesktop().browse(URI(url))
    }
}

actual fun shareUrl(
    title: String,
    url: String,
) {
    val stringSelection = StringSelection(url)
    val clipboard: Clipboard = Toolkit.getDefaultToolkit().systemClipboard
    clipboard.setContents(stringSelection, null)
    showToast("Copied to clipboard")
}

// Desktop ya tiene acceso directo al log en disco (AuditFileLogWriter,
// %LOCALAPPDATA%/Wavora/logs) - no hace falta un share sheet acá, solo abrir
// esa carpeta en el explorador de archivos. shareLogs() en Android es el que
// resuelve el caso real que motivó esto: pedirle un log a un usuario de la
// app que no tiene ni ADB ni PC.
actual fun shareLogs(): Boolean {
    if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) return false
    return try {
        Desktop.getDesktop().open(com.wavora.app.diagnostics.AuditFileLogWriter.logDir)
        true
    } catch (e: Exception) {
        false
    }
}