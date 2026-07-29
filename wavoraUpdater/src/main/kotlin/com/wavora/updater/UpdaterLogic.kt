package com.wavora.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.PrintWriter
import java.io.RandomAccessFile
import java.io.StringWriter
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileLock
import java.security.MessageDigest
import java.time.Instant
import java.util.zip.ZipInputStream

/** Everything the caller (Main.kt / the UI) needs to run one update pass. */
data class UpdaterArgs(
    val zipUrl: String,
    val targetVersion: String,
    /** Lowercase hex, no "sha256:" prefix - already stripped by the caller
     * in AppUpdate.jvm.kt. Null if GitHub didn't publish a digest for
     * this asset (older release) - verification is then skipped, not
     * failed, since its absence isn't itself a sign of tampering. */
    val expectedSha256: String?,
    /** PID of the already-exiting Wavora process we must wait out before
     * touching any of its files. */
    val wavoraPid: Long,
)

enum class Stage {
    WAITING_FOR_WAVORA,
    DOWNLOADING,
    VERIFYING,
    EXTRACTING,
    INSTALLING,
    CLEANING,
    LAUNCHING,
}

class UpdaterException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Minimal file-based logger for WavoraUpdater. This module deliberately has
 * no dependency on the main app's Logger (kermit) - see the doc on
 * :wavoraUpdater staying standalone - so this is a tiny from-scratch
 * equivalent, just enough to leave a real trail on disk for whatever run
 * just happened.
 *
 * Why this exists: before this, the ONLY failure signal a user could give
 * us was a phone photo of the error screen showing a raw OS exception
 * message (see e.message in UpdaterApp's catch block) - no stage, no file
 * path, no stack trace. That's nearly undiagnosable. Every run now writes
 * one log file with a timestamped name (so retries don't overwrite each
 * other), one line per stage transition, and on failure the FULL exception
 * (class name + message + stack trace), not just e.message.
 */
object UpdateLog {
    private val logFile: File by lazy {
        val dir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Wavora/Updater/logs")
        dir.mkdirs()
        // Best-effort prune: keep only the 10 most recent log files so this
        // folder doesn't grow forever across many update attempts.
        dir.listFiles { f -> f.isFile && f.name.startsWith("update-") }
            ?.sortedByDescending { it.lastModified() }
            ?.drop(9)
            ?.forEach { it.delete() }
        File(dir, "update-${System.currentTimeMillis()}.log")
    }

    fun line(message: String) {
        runCatching {
            logFile.appendText("[${Instant.now()}] $message\n")
        }
    }

    fun error(
        context: String,
        e: Throwable,
    ) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        runCatching {
            logFile.appendText("[${Instant.now()}] ERROR at $context: ${e::class.qualifiedName}: ${e.message}\n$sw\n")
        }
    }

    /** Path of this run's log file, for surfacing in the error UI. */
    val path: String get() = logFile.absolutePath
}

/**
 * Single-instance protection: if the user mashes "Actualizar" more than
 * once, or the main app somehow ends up launching WavoraUpdater twice
 * (e.g. a retry from Wavora's own side racing an already-running
 * updater), only the first instance should actually do anything - every
 * later one should recognize that and exit immediately, before creating
 * any window.
 *
 * Deliberately a plain JDK [FileLock] on a lock file under the same
 * %LOCALAPPDATA%\Wavora\Updater\ folder the updater already lives in -
 * not a native Win32 mutex (which would need a JNA dependency this
 * module intentionally doesn't have, see build.gradle.kts's doc on
 * staying standalone). A FileLock is held only as long as this process
 * is alive; Windows releases it automatically the moment the process
 * exits or crashes, so a prior crashed instance can never permanently
 * block future launches - no stale-lock cleanup logic needed.
 *
 * Returns the (still open) [FileLock] to keep alive for the process's
 * whole lifetime if this is the first instance, or null if another
 * instance already holds it (caller should exit immediately in that
 * case, without acquiring anything further or opening any UI).
 */
fun acquireSingleInstanceLock(): FileLock? {
    val lockDir = File(System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir"), "Wavora/Updater")
    lockDir.mkdirs()
    val lockFile = File(lockDir, ".instance.lock")
    val channel = RandomAccessFile(lockFile, "rw").channel
    // tryLock (not lock()) - non-blocking: if another instance already
    // holds it, we want to know immediately and exit, not wait around.
    return try {
        channel.tryLock()
    } catch (e: Exception) {
        // Any failure to even attempt the lock (e.g. antivirus holding the
        // file transiently) - fail safe by treating it as "someone else has
        // it" rather than risk two instances running concurrently.
        null
    }
}

/** progress is 0f..1f when known, null for indeterminate stages
 * (verifying/extracting/installing/cleaning/launching don't have a
 * meaningful percentage, only downloading does). */
typealias StageCallback = (Stage, progress: Float?) -> Unit

object UpdaterLogic {
    /**
     * Runs one full update attempt. Throws [UpdaterException] (with a
     * human-readable message) on any failure - the caller is responsible
     * for offering retry, which simply calls this again from scratch
     * (every step here is idempotent: same target file names, always
     * deleted/recreated up front).
     */
    suspend fun run(
        args: UpdaterArgs,
        onStage: StageCallback,
    ) = withContext(Dispatchers.IO) {
        UpdateLog.line("=== Starting update run: version=${args.targetVersion} zipUrl=${args.zipUrl} sha256=${args.expectedSha256 ?: "none"} wavoraPid=${args.wavoraPid} ===")
        // Logging every single onStage() call was a real bug, not just
        // noise: downloadWithProgress() invokes this once per network
        // read - for a 326MB file that's several thousand calls, each one
        // opening, appending to, and closing the log file on disk. That
        // I/O overhead was almost certainly the actual cause of the
        // updater appearing to hang/never finish (confirmed by Sebastian:
        // the log file itself was tens of thousands of lines of pure
        // download progress). Only log DOWNLOADING at 5% steps; every
        // other stage is infrequent enough to log unconditionally.
        var lastLoggedDownloadBucket = -1
        val loggedOnStage: StageCallback = { stage, progress ->
            if (stage == Stage.DOWNLOADING && progress != null) {
                val bucket = (progress * 20).toInt() // 20 buckets = 5% each
                if (bucket != lastLoggedDownloadBucket) {
                    lastLoggedDownloadBucket = bucket
                    UpdateLog.line("Stage=$stage progress=$progress")
                }
            } else {
                UpdateLog.line("Stage=$stage progress=$progress")
            }
            onStage(stage, progress)
        }

        // Best-effort cleanup of workDirs left behind by a previous run that
        // never reached its own finally block below (process killed, Windows
        // restarted mid-update, power loss, etc.). Safe to do unconditionally
        // here because the caller only reaches run() after acquiring the
        // single-instance lock, so no other WavoraUpdater instance can be
        // using one of these directories right now.
        File(System.getProperty("java.io.tmpdir"))
            .listFiles { file -> file.isDirectory && file.name.startsWith("wavora-update-") }
            ?.forEach { it.deleteRecursively() }

        val workDir = File(System.getProperty("java.io.tmpdir"), "wavora-update-${System.currentTimeMillis()}")
        workDir.mkdirs()
        try {
            waitForWavoraToExit(args.wavoraPid, loggedOnStage)

            val zipFile = File(workDir, "AppwavoraWindows.zip")
            downloadWithProgress(args.zipUrl, zipFile, loggedOnStage)

            if (args.expectedSha256 != null) {
                verifySha256(zipFile, args.expectedSha256, loggedOnStage)
            }

            val extractDir = File(workDir, "extracted")
            extractZip(zipFile, extractDir, loggedOnStage)

            val installScript =
                extractDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("install.ps1", ignoreCase = true) }
                    ?: throw UpdaterException("No se encontró install.ps1 dentro del ZIP descargado (log: ${UpdateLog.path})")

            runInstallScript(installScript, loggedOnStage)

            loggedOnStage(Stage.CLEANING, null)
            // Nothing else to do here beyond the finally block below -
            // kept as an explicit stage so the UI shows it, since the
            // actual removal happens for BOTH the success and failure
            // paths (see finally).

            loggedOnStage(Stage.LAUNCHING, null)
            // install.ps1 already launches Wavora itself as its last step
            // (resolving the installed package's AppUserModelID via
            // Get-StartApps and `Start-Process shell:AppsFolder\<id>`) -
            // that specific lookup has no simple pure-Kotlin equivalent
            // without WinRT/COM interop (see the chat report), so rather
            // than duplicate a fragile reimplementation here, this stage
            // is shown for UI continuity around the moment it already
            // happened inside the one install.ps1 invocation above.
            UpdateLog.line("=== Update run finished successfully ===")
        } catch (e: UpdaterException) {
            // Already a clear, specific message from one of the checks
            // above (own log line already written where it was thrown) -
            // just log the file path used this run and let it propagate
            // unchanged rather than wrapping a wrapper.
            UpdateLog.error("run() - update failed", e)
            throw e
        } catch (e: Exception) {
            UpdateLog.error("run() - update failed", e)
            // Any OTHER exception type reaching here means some native
            // call wasn't wrapped with context above - still better than
            // showing the bare OS message with nothing else, so at least
            // point at the log file for the full stack trace.
            throw UpdaterException("${e.message ?: e::class.simpleName} (ver log: ${UpdateLog.path})", e)
        } finally {
            // NOTE: deliberately NOT deleting workDir here anymore. A
            // single deleteRecursively() call on the freshly-written
            // .msix can block for a long time - not fail fast, genuinely
            // block the calling thread - because Windows Defender's
            // on-access scan intercepts the delete/close and holds it up
            // until the scan finishes (confirmed: this is what was
            // keeping the app open at the LAUNCHING stage, consuming CPU
            // for the halo animation, long after the real work was done).
            // Closing the app promptly matters far more than removing
            // this folder right now - the orphan cleanup at the top of
            // the NEXT run already deletes any leftover
            // wavora-update-* folder, by which point Defender's scan has
            // long finished and the delete is instant.
        }
    }

    private suspend fun waitForWavoraToExit(
        pid: Long,
        onStage: StageCallback,
    ) {
        onStage(Stage.WAITING_FOR_WAVORA, null)
        val handle = ProcessHandle.of(pid).orElse(null) ?: return // already gone
        if (!handle.isAlive) return
        // Real OS-level exit event (Java 9+ ProcessHandle API), not a
        // sleep/poll loop - resolves exactly when Windows reports the
        // process has actually terminated, which is also exactly when
        // its file locks are released.
        withContext(Dispatchers.IO) {
            handle.onExit().join()
        }
    }

    private fun downloadWithProgress(
        url: String,
        destination: File,
        onStage: StageCallback,
    ) {
        onStage(Stage.DOWNLOADING, 0f)
        val connection = URI(url).toURL().openConnection() as HttpURLConnection
        connection.instanceFollowRedirects = true
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw UpdaterException("La descarga falló (HTTP ${connection.responseCode})")
        }

        val totalBytes = connection.contentLengthLong
        var readBytes = 0L
        connection.inputStream.use { input ->
            destination.outputStream().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break
                    output.write(buffer, 0, read)
                    readBytes += read
                    if (totalBytes > 0) {
                        onStage(Stage.DOWNLOADING, readBytes.toFloat() / totalBytes.toFloat())
                    }
                }
            }
        }

        if (!destination.exists() || destination.length() == 0L) {
            throw UpdaterException("El archivo descargado está vacío o no existe")
        }
        if (totalBytes > 0 && readBytes != totalBytes) {
            throw UpdaterException("La descarga quedó incompleta ($readBytes de $totalBytes bytes)")
        }

        // Magic-byte sanity check: a ZIP always starts with "PK". This
        // catches the case where the HTTP response was 200 with a
        // Content-Length that happened to match (e.g. a proxy/AV
        // interstitial page, a GitHub error page, a truncated CDN
        // response) - readBytes==totalBytes would pass above even though
        // what we downloaded isn't a real installer at all. Without this,
        // that case surfaces later as an opaque native error deep inside
        // extraction with no indication the download itself was the
        // actual problem.
        val header = ByteArray(2)
        RandomAccessFile(destination, "r").use { raf -> raf.readFully(header) }
        if (header[0] != 'P'.code.toByte() || header[1] != 'K'.code.toByte()) {
            UpdateLog.line("Downloaded file does not start with ZIP magic bytes (got ${header.joinToString { "%02x".format(it) }}) - size=${destination.length()}")
            throw UpdaterException(
                "El archivo descargado no es un ZIP válido (¿la URL cambió o algo interceptó la descarga? " +
                    "tamaño: ${destination.length()} bytes)",
            )
        }
    }

    private fun verifySha256(
        file: File,
        expectedHex: String,
        onStage: StageCallback,
    ) {
        onStage(Stage.VERIFYING, null)
        val digest = MessageDigest.getInstance("SHA-256")
        RandomAccessFile(file, "r").use { raf ->
            val buffer = ByteArray(64 * 1024)
            var read: Int
            val channel = raf.channel
            val byteBuffer = java.nio.ByteBuffer.wrap(buffer)
            while (true) {
                byteBuffer.clear()
                read = channel.read(byteBuffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        val actualHex = digest.digest().joinToString("") { "%02x".format(it) }
        if (!actualHex.equals(expectedHex, ignoreCase = true)) {
            throw UpdaterException(
                "La verificación de integridad falló: el archivo descargado no coincide con el checksum publicado " +
                    "(esperado $expectedHex, obtenido $actualHex). No se instala un archivo que no se puede verificar.",
            )
        }
    }

    private fun extractZip(
        zipFile: File,
        destinationDir: File,
        onStage: StageCallback,
    ) {
        onStage(Stage.EXTRACTING, null)
        destinationDir.mkdirs()
        val canonicalDest = destinationDir.canonicalFile
        ZipInputStream(zipFile.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val entryName = entry.name
                val outFile = File(destinationDir, entryName)
                try {
                    // Zip-slip guard: refuse to extract any entry whose
                    // resolved path escapes destinationDir.
                    if (!outFile.canonicalFile.path.startsWith(canonicalDest.path + File.separator) &&
                        outFile.canonicalFile != canonicalDest
                    ) {
                        throw UpdaterException("Entrada de ZIP inválida (fuera del directorio de extracción): $entryName")
                    }
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { output -> zis.copyTo(output) }
                    }
                } catch (e: UpdaterException) {
                    throw e
                } catch (e: Exception) {
                    // Wrap ANY native I/O failure (invalid filename chars,
                    // reserved Windows device names like con/aux/nul, path
                    // too long, etc.) with the exact entry name and target
                    // path - this is what used to surface as a bare,
                    // contextless Windows error string with no way to know
                    // which of the hundreds of files in the zip caused it.
                    UpdateLog.line("Failed extracting entry '$entryName' -> '${outFile.absolutePath}': ${e::class.simpleName}: ${e.message}")
                    throw UpdaterException(
                        "No se pudo extraer '$entryName' (destino: ${outFile.absolutePath}): ${e.message} (log: ${UpdateLog.path})",
                        e,
                    )
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
    }

    private fun runInstallScript(
        installScript: File,
        onStage: StageCallback,
    ) {
        onStage(Stage.INSTALLING, null)

        // Pre-flight checks - if either of these is false, ProcessBuilder
        // would fail deep inside a native CreateProcess call with an
        // opaque OS error message (e.g. ERROR_INVALID_NAME) and no
        // indication of WHICH path was the problem. Checking here first
        // turns that into a clear, actionable message.
        if (!installScript.exists()) {
            throw UpdaterException("install.ps1 no existe en la ruta esperada: ${installScript.absolutePath}")
        }
        val workingDir = installScript.parentFile
        if (workingDir == null || !workingDir.exists()) {
            throw UpdaterException("El directorio de trabajo para install.ps1 no existe: ${workingDir?.absolutePath}")
        }
        UpdateLog.line("Launching install.ps1 at ${installScript.absolutePath} (cwd=${workingDir.absolutePath})")

        // The one deliberate exception to "everything in Kotlin": MSIX
        // package installation (Add-AppxPackage) and trusting the
        // self-signed cert (Import-Certificate to LocalMachine) have no
        // simple public API callable directly from a JVM process without
        // substantial COM/WinRT interop - PowerShell's own cmdlets are
        // the thin, official wrapper around exactly that. This whole
        // updater process is already running elevated (see
        // AppUpdate.jvm.kt's launch via JNA Shell32.ShellExecute "runas"),
        // so this doesn't trigger a second UAC prompt.
        val process =
            try {
                ProcessBuilder(
                    "powershell.exe",
                    "-NoProfile",
                    "-ExecutionPolicy", "Bypass",
                    "-WindowStyle", "Hidden",
                    "-File", installScript.absolutePath,
                    "-Silent",
                ).directory(workingDir)
                    .start()
            } catch (e: Exception) {
                UpdateLog.error("runInstallScript() - ProcessBuilder.start() failed", e)
                throw UpdaterException(
                    "No se pudo iniciar install.ps1 (path=${installScript.absolutePath}, cwd=${workingDir.absolutePath}): ${e.message}",
                    e,
                )
            }

        // Real process-exit wait (Process.waitFor()), not a sleep. The
        // bounded overload here is a safety net against a genuinely wedged
        // child (e.g. Add-AppxPackage hung on something with no console to
        // report to), not a substitute for the real completion signal -
        // the normal, expected path always returns well before this.
        val finished = process.waitFor(5, java.util.concurrent.TimeUnit.MINUTES)
        if (!finished) {
            process.destroyForcibly()
            throw UpdaterException("La instalación no terminó dentro del tiempo de espera de seguridad (5 min)")
        }
        if (process.exitValue() != 0) {
            throw UpdaterException("La instalación terminó con código de error ${process.exitValue()}")
        }
    }
}
