package com.wavora.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URI
import java.nio.channels.FileLock
import java.security.MessageDigest
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
            waitForWavoraToExit(args.wavoraPid, onStage)

            val zipFile = File(workDir, "AppwavoraWindows.zip")
            downloadWithProgress(args.zipUrl, zipFile, onStage)

            if (args.expectedSha256 != null) {
                verifySha256(zipFile, args.expectedSha256, onStage)
            }

            val extractDir = File(workDir, "extracted")
            extractZip(zipFile, extractDir, onStage)

            val installScript =
                extractDir.walkTopDown().firstOrNull { it.isFile && it.name.equals("install.ps1", ignoreCase = true) }
                    ?: throw UpdaterException("No se encontró install.ps1 dentro del ZIP descargado")

            runInstallScript(installScript, onStage)

            onStage(Stage.CLEANING, null)
            // Nothing else to do here beyond the finally block below -
            // kept as an explicit stage so the UI shows it, since the
            // actual removal happens for BOTH the success and failure
            // paths (see finally).

            onStage(Stage.LAUNCHING, null)
            // install.ps1 already launches Wavora itself as its last step
            // (resolving the installed package's AppUserModelID via
            // Get-StartApps and `Start-Process shell:AppsFolder\<id>`) -
            // that specific lookup has no simple pure-Kotlin equivalent
            // without WinRT/COM interop (see the chat report), so rather
            // than duplicate a fragile reimplementation here, this stage
            // is shown for UI continuity around the moment it already
            // happened inside the one install.ps1 invocation above.
        } finally {
            workDir.deleteRecursively()
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
                val outFile = File(destinationDir, entry.name)
                // Zip-slip guard: refuse to extract any entry whose resolved
                // path escapes destinationDir.
                if (!outFile.canonicalFile.path.startsWith(canonicalDest.path + File.separator) &&
                    outFile.canonicalFile != canonicalDest
                ) {
                    throw UpdaterException("Entrada de ZIP inválida (fuera del directorio de extracción): ${entry.name}")
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
    }

    private fun runInstallScript(
        installScript: File,
        onStage: StageCallback,
    ) {
        onStage(Stage.INSTALLING, null)
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
            ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-ExecutionPolicy", "Bypass",
                "-WindowStyle", "Hidden",
                "-File", installScript.absolutePath,
                "-Silent",
            ).directory(installScript.parentFile)
                .start()

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
