package com.wavora.app.expect

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Environment
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.wavora.logger.Logger
import org.koin.mp.KoinPlatform.getKoin
import java.io.File

private const val TAG = "AppUpdate"
private const val APK_FILE_NAME = "wavora-update.apk"

actual fun supportsInAppUpdate(): Boolean = true

actual fun currentDeviceAbis(): List<String> = android.os.Build.SUPPORTED_ABIS?.toList() ?: emptyList()

// sha256 is unused on Android - PackageInstaller already verifies the
// APK's own signature, see the expect declaration's doc for why a
// separate hash check isn't needed here.
actual fun installUpdate(downloadUrl: String, versionName: String, sha256: String?, onError: (String) -> Unit) {
    val context: AppCompatActivity = getKoin().get()

    // Per-app "install unknown apps" permission (API 26+, matches this
    // project's minSdk = 26 - no need to handle the old pre-26 global
    // Settings.Secure.INSTALL_NON_MARKET_APPS toggle at all). Granted
    // once per app that requests installs, not per install - so this is
    // a one-time interruption the very first time someone updates.
    if (!context.packageManager.canRequestPackageInstalls()) {
        Logger.w(TAG, "Install permission not granted yet, sending user to Settings")
        val settingsIntent =
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(settingsIntent)
        // Bail here rather than downloading speculatively: the user
        // still needs to flip the permission and tap "update" again.
        // Chaining a retry off onResume would fire on ANY return to the
        // app (e.g. switching back from another app entirely), not just
        // a return from this specific Settings screen.
        return
    }

    downloadAndInstall(context, downloadUrl, versionName)
}

private fun downloadAndInstall(context: AppCompatActivity, downloadUrl: String, versionName: String) {
    val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
    val apkFile = File(downloadDir, APK_FILE_NAME)
    // Overwrite any half-finished/stale download from a previous attempt.
    if (apkFile.exists()) apkFile.delete()

    val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val request =
        DownloadManager.Request(downloadUrl.toUri())
            .setTitle("Wavora $versionName")
            .setDescription("Descargando actualización…")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationUri(Uri.fromFile(apkFile))
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

    val downloadId = downloadManager.enqueue(request)

    // Registered against the application context, not the Activity: a
    // large APK can take a while to download, and an Activity-scoped
    // receiver would either leak the Activity or go stale across a
    // configuration change (rotation) or the user backgrounding the app
    // mid-download. startActivity below still works fine from an
    // application context because of FLAG_ACTIVITY_NEW_TASK.
    val appContext = context.applicationContext
    val receiver =
        object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                if (completedId != downloadId) return
                appContext.unregisterReceiver(this)

                if (!apkFile.exists()) {
                    Logger.e(TAG, "Download reported complete but $apkFile is missing")
                    return
                }

                val apkUri =
                    try {
                        FileProvider.getUriForFile(appContext, "${appContext.packageName}.FileProvider", apkFile)
                    } catch (e: IllegalArgumentException) {
                        // Was silently killing this whole receiver before this
                        // catch existed - see provider_paths.xml's AUDIT NOTE for
                        // the root cause (missing <external-files-path>).
                        Logger.e(TAG, "FileProvider could not resolve $apkFile: ${e.message}")
                        return
                    }
                val installIntent =
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(apkUri, "application/vnd.android.package-archive")
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                try {
                    appContext.startActivity(installIntent)
                } catch (e: Exception) {
                    Logger.e(TAG, "Failed to launch package installer: ${e.message}")
                }
            }
        }

    ContextCompat.registerReceiver(
        appContext,
        receiver,
        IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
        ContextCompat.RECEIVER_NOT_EXPORTED,
    )
}
