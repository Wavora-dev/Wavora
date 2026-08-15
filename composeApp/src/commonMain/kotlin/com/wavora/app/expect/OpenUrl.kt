package com.wavora.app.expect

expect fun openUrl(url: String)

expect fun shareUrl(
    title: String,
    url: String,
)

/** Comparte el log de diagnóstico de la sesión actual (ver
 * `AuditFileLogWriter`) por el share sheet nativo, para poder pedirle un log
 * a cualquier usuario de la app sin que necesite ADB ni una PC. En Android
 * abre el chooser de compartir sobre el archivo; en Desktop (que ya tiene
 * acceso directo al log en `%LOCALAPPDATA%/Wavora/logs`) abre esa carpeta en
 * el explorador de archivos en su lugar. Retorna `false` si no hay ningún
 * log todavía o si falló al intentar compartir/abrir. */
expect fun shareLogs(): Boolean