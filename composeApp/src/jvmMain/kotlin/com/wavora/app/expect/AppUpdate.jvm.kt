package com.wavora.app.expect

import com.sun.jna.platform.win32.Shell32
import com.sun.jna.platform.win32.WinUser
import com.wavora.logger.Logger
import dev.hydraulic.conveyor.control.SoftwareUpdateController
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.system.exitProcess

private const val TAG = "AppUpdate"

// Bump this whenever wavoraUpdater's own code changes in a way that needs
// redeploying to already-installed machines' %LOCALAPPDATA% copy. Compared
// against the marker file written by ensureUpdaterInstalled() - NOT the
// main app's own version, since the updater can (and should) go multiple
// Wavora releases without changing at all.
private const val BUNDLED_UPDATER_VERSION = 1

private fun isWindows(): Boolean = System.getProperty("os.name")?.contains("Windows", ignoreCase = true) == true

// Desktop always reports true: Conveyor IS the primary path (see
// installUpdate's doc), and on Windows the fallback native updater covers
// the case where Conveyor's own mechanism isn't usable. Mac/Linux keep
// relying purely on Conveyor (background/Sparkle) - installUpdate() below
// only ever runs the fallback branch on Windows.
actual fun supportsInAppUpdate(): Boolean = true

actual fun currentDeviceAbis(): List<String> = emptyList()

/**
 * Desktop update entry point.
 *
 * 1. Conveyor is, and remains, the primary/preferred update mechanism (see
 *    tryConveyorUpdate() - unchanged from the previous round). If it can
 *    handle the update, we stop here and let it do everything.
 *
 * 2. Only if Conveyor can't (see tryConveyorUpdate()'s doc for exactly
 *    when) do we fall back to WavoraUpdater.exe - a small, standalone
 *    Kotlin+Compose Desktop app (module :wavoraUpdater) that owns the
 *    ENTIRE rest of the update (download, SHA-256 verification,
 *    extraction, install, cleanup, relaunch). This function's only job
 *    in the fallback case is:
 *      a) make sure a current copy of WavoraUpdater exists at
 *         %LOCALAPPDATA%\Wavora\Updater\ (installed once, reused for
 *         every future update - see ensureUpdaterInstalled()),
 *      b) launch it elevated (single UAC prompt for the whole flow) with
 *         the info it needs, and
 *      c) exit immediately - this process does not download anything
 *         itself and does not wait for the updater to finish.
 */
actual fun installUpdate(
    downloadUrl: String,
    versionName: String,
    sha256: String?,
    onError: (String) -> Unit,
) {
    if (tryConveyorUpdate(versionName)) {
        return
    }

    if (!isWindows()) {
        Logger.w(TAG, "Conveyor update unavailable and no fallback exists on this OS - nothing left to try")
        return
    }

    try {
        val updaterExe = ensureUpdaterInstalled()
        launchUpdaterElevated(updaterExe, downloadUrl, versionName, sha256)
        exitProcess(0)
    } catch (e: Exception) {
        Logger.e(TAG, "Failed to launch WavoraUpdater: ${e.message}")
        onError(e.message ?: "No se pudo iniciar el actualizador de Wavora")
    }
}

/**
 * Tries Conveyor's own update mechanism via its official Control API.
 * Unchanged from the previous round - see the chat report for the full
 * rationale. Returns true if Conveyor successfully took over, false if
 * the caller should fall back.
 */
private fun tryConveyorUpdate(versionName: String): Boolean {
    val controller =
        try {
            SoftwareUpdateController.getInstance()
        } catch (e: Throwable) {
            Logger.w(TAG, "SoftwareUpdateController.getInstance() threw: ${e.message}")
            null
        }

    if (controller == null) {
        Logger.d(TAG, "Not packaged by Conveyor (or control API unavailable) - falling back")
        return false
    }

    val availability =
        try {
            controller.canTriggerUpdateCheckUI()
        } catch (e: Throwable) {
            Logger.w(TAG, "canTriggerUpdateCheckUI() threw: ${e.message}")
            return false
        }

    if (availability != SoftwareUpdateController.Availability.AVAILABLE) {
        Logger.d(TAG, "Conveyor update checks not available on this install ($availability) - falling back")
        return false
    }

    return try {
        val currentVersion = controller.currentVersion
        val latestVersion = controller.currentVersionFromRepository
        if (latestVersion == null) {
            Logger.w(TAG, "Conveyor repository returned no version info - falling back")
            return false
        }
        if (latestVersion.compareTo(currentVersion) <= 0) {
            Logger.w(TAG, "Conveyor's site doesn't see a newer version than $currentVersion (expected $versionName) - falling back")
            return false
        }
        Logger.d(TAG, "Conveyor confirms update to $latestVersion is available - handing off to Conveyor")
        controller.triggerUpdateCheckUI()
        true
    } catch (e: SoftwareUpdateController.UpdateCheckException) {
        Logger.w(TAG, "Conveyor update check failed (site unreachable?): ${e.message} - falling back")
        false
    } catch (e: Exception) {
        Logger.w(TAG, "Unexpected error trying Conveyor update: ${e.message} - falling back")
        false
    }
}

/**
 * Ensures %LOCALAPPDATA%\Wavora\Updater\ has a current copy of
 * WavoraUpdater, installing/refreshing it from the zip bundled inside
 * this app's own jar (composeApp's build embeds :wavoraUpdater's
 * jpackage app-image output as a classpath resource - see
 * composeApp/build.gradle.kts's bundleWavoraUpdater task) if it's
 * missing or older than what THIS build of Wavora ships.
 *
 * Deliberately does the version check/copy from the MAIN app (not
 * having WavoraUpdater update itself) - this avoids the exact
 * files-in-use problem the whole design is built around: at the moment
 * this runs, WavoraUpdater.exe is NOT running yet, so replacing its
 * files is always safe, no waiting/locking concerns apply here at all.
 */
private fun ensureUpdaterInstalled(): File {
    val updaterDir = File(System.getenv("LOCALAPPDATA") ?: error("LOCALAPPDATA not set"), "Wavora/Updater")
    val versionMarker = File(updaterDir, "updater-version.txt")
    val installedVersion = versionMarker.takeIf { it.exists() }?.readText()?.trim()?.toIntOrNull() ?: -1

    if (installedVersion >= BUNDLED_UPDATER_VERSION && File(updaterDir, "WavoraUpdater.exe").exists()) {
        Logger.d(TAG, "WavoraUpdater already up to date at $updaterDir (v$installedVersion)")
        return File(updaterDir, "WavoraUpdater.exe")
    }

    Logger.d(TAG, "Installing/refreshing WavoraUpdater at $updaterDir (had v$installedVersion, bundling v$BUNDLED_UPDATER_VERSION)")
    if (updaterDir.exists()) updaterDir.deleteRecursively()
    updaterDir.mkdirs()

    val resourceStream =
        object {}.javaClass.getResourceAsStream("/wavora-updater.zip")
            ?: throw IllegalStateException("Bundled resource /wavora-updater.zip not found - was it built by :wavoraUpdater?")

    ZipInputStream(resourceStream).use { zis ->
        var entry = zis.nextEntry
        val canonicalDest = updaterDir.canonicalFile
        while (entry != null) {
            val outFile = File(updaterDir, entry.name)
            if (!outFile.canonicalFile.path.startsWith(canonicalDest.path + File.separator) && outFile.canonicalFile != canonicalDest) {
                throw IllegalStateException("Invalid zip entry outside destination: ${entry.name}")
            }
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                outFile.outputStream().use { output -> zis.copyTo(output) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }

    versionMarker.writeText(BUNDLED_UPDATER_VERSION.toString())

    val exe = File(updaterDir, "WavoraUpdater.exe")
    if (!exe.exists()) {
        throw IllegalStateException("WavoraUpdater.exe missing after extraction - check :wavoraUpdater's jpackage output layout")
    }
    return exe
}

/**
 * Launches WavoraUpdater.exe elevated (single UAC prompt for the whole
 * fallback flow) using JNA's Shell32.ShellExecute with verb "runas".
 *
 * Why JNA instead of ProcessBuilder: Windows requires ShellExecute(Ex)
 * specifically to trigger a UAC elevation prompt - a plain CreateProcess
 * call (which is what ProcessBuilder uses under the hood) fails with
 * "elevation required" instead of prompting, even if the target exe's own
 * manifest requests administrator. No PowerShell involved anywhere in
 * this step.
 *
 * Plain ShellExecute (not ShellExecuteEx/SHELLEXECUTEINFO): the "Ex"
 * variant's only reason to exist here would be to get a process handle
 * back via SEE_MASK_NOCLOSEPROCESS - but this function's caller
 * (installUpdate()) exits immediately after this returns and never
 * waits on or reads that handle. Plain ShellExecute triggers the exact
 * same "runas" elevation with a single primitive call - no struct to
 * build, no mask flags, smaller surface for exactly the kind of
 * wrong-interface mistake that broke the first version of this file
 * (see the chat's build-log fix).
 */
private fun launchUpdaterElevated(
    updaterExe: File,
    zipUrl: String,
    versionName: String,
    sha256: String?,
) {
    val currentPid = ProcessHandle.current().pid()
    val args =
        listOf(
            "--zip-url", zipUrl,
            "--version", versionName,
            "--sha256", sha256 ?: "none",
            "--wavora-pid", currentPid.toString(),
        ).joinToString(" ") { arg -> if (arg.startsWith("--")) arg else "\"$arg\"" }

    Logger.d(TAG, "Launching WavoraUpdater elevated for $versionName (Wavora pid=$currentPid)")
    val result =
        Shell32.INSTANCE.ShellExecute(
            null,
            "runas",
            updaterExe.absolutePath,
            args,
            updaterExe.parentFile?.absolutePath,
            WinUser.SW_SHOWNORMAL,
        )
    // ShellExecute returns WinDef.INT_PTR, not a true HINSTANCE/Pointer -
    // its value is only ever meant to be compared as a plain int (see
    // Shell32's own doc: "can be cast only to an int and compared to
    // either 32 or the SE_ERR_* codes"). A value > 32 means success;
    // anything <= 32 is one of those SE_ERR_* codes - most commonly the
    // user declining the UAC prompt.
    val resultCode = result.toInt()
    if (resultCode <= 32) {
        throw IllegalStateException("ShellExecute failed to launch WavoraUpdater (code=$resultCode - user may have declined the UAC prompt)")
    }
}
