package com.wavora.app

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import coil3.compose.LocalPlatformContext
import coil3.compose.setSingletonImageLoaderFactory
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.kdroid.composetray.tray.api.Tray
import com.kdroid.composetray.utils.SingleInstanceManager
import com.wavora.appdata.di.loader.loadAllModules
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.handler.ToastType
import com.wavora.app.di.viewModelModule
import com.wavora.app.ui.component.CustomTitleBar
import com.wavora.app.ui.mini_player.MiniPlayerManager
import com.wavora.app.ui.mini_player.MiniPlayerWindow
import com.wavora.app.utils.VersionManager
import com.wavora.app.viewModel.SharedViewModel
import com.wavora.app.viewModel.changeLanguageNative
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import multiplatform.network.cmptoast.ToastHost
import multiplatform.network.cmptoast.showToast
import okhttp3.OkHttpClient
import okio.FileSystem
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.inject
import org.koin.mp.KoinPlatform.getKoin
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.app_name
import wavora.composeapp.generated.resources.circle_app_icon
import wavora.composeapp.generated.resources.close_miniplayer
import wavora.composeapp.generated.resources.explicit_content_blocked
import wavora.composeapp.generated.resources.open_app
import wavora.composeapp.generated.resources.open_miniplayer
import wavora.composeapp.generated.resources.quit_app
import wavora.composeapp.generated.resources.time_out_check_internet_connection_or_change_piped_instance_in_settings

@OptIn(ExperimentalMaterial3Api::class)
fun runDesktopApp(args: Array<String> = emptyArray()) {
    // Install crash dialog handler first — catches all uncaught exceptions
    CrashDialog.install()

    System.setProperty("compose.swing.render.on.graphics", "true")
    System.setProperty("compose.interop.blending", "true")
    // WAVORA FIX: COMPONENT layers cause invisible/blank windows on Windows 10/11
    // with integrated Intel, older AMD, and some NVIDIA GPUs. SWING layers +
    // SOFTWARE rendering bypass all GPU driver issues entirely.
    val isWindows = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)
    if (isWindows) {
        System.setProperty("compose.layers.type", "SWING")
        System.setProperty("skiko.renderApi", "SOFTWARE")
    } else {
        System.setProperty("compose.layers.type", "COMPONENT")
    }

    // Handle deep link URIs
    // macOS: receives URI via Desktop open URI handler (app already running or launched via scheme)
    // Windows/Linux: receives URI as command-line argument
    val isMacOS = System.getProperty("os.name", "").contains("Mac", ignoreCase = true)
    if (isMacOS && java.awt.Desktop.isDesktopSupported()) {
        try {
            java.awt.Desktop.getDesktop().setOpenURIHandler { event ->
                DesktopDeepLinkHandler.onNewUri(event.uri.toString())
            }
        } catch (_: UnsupportedOperationException) {
            // Shouldn't happen on macOS, but handle gracefully
        }
    }
    // Handle URI passed as command-line argument (Windows/Linux, or explicit invocation)
    // Note: macOS does NOT pass URI as args — it uses Apple Events via setOpenURIHandler
    val deepLinkArg =
        args.firstOrNull()?.takeIf { arg ->
            arg.startsWith("wavora://") || arg.startsWith("http://") || arg.startsWith("https://")
        }
    // Single-instance guard — MUST run before startKoin. The DataStore Koin
    // singleton is `createdAtStart`, so a second Windows instance would touch
    // ~/.wavora/settings.preferences_pb and crash with an "Unable to rename
    // ...tmp" IOException (#2044) before it ever reached the old in-Compose check.
    // Bail out here, before Koin/DataStore initialize.
    val isSingleInstance =
        SingleInstanceManager.isSingleInstance(
            onRestoreRequest = { DesktopRestoreSignal.request() },
        )
    if (!isSingleInstance) {
        // Second instance: forward the deep link (if any) to the running instance,
        // then exit. Nothing has touched the DataStore file yet.
        deepLinkArg?.let { DesktopDeepLinkHandler.writePendingUri(it) }
        return
    }

    // First instance only: deliver our own deep link (non-macOS passes URI via args).
    if (!isMacOS) {
        deepLinkArg?.let { DesktopDeepLinkHandler.onNewUri(it) }
    }

    // Initialize Koin ONCE before application starts
    startKoin {
        loadAllModules()
        loadKoinModules(viewModelModule)
    }

    val language =
        runBlocking {
            getKoin()
                .get<DataStoreManager>()
                .language
                .first()
                .substring(0..1)
        }
    changeLanguageNative(language)

    VersionManager.initialize()
    if (BuildKonfig.sentryDsn.isNotEmpty()) {
        Sentry.init { options ->
            options.dsn = BuildKonfig.sentryDsn
            options.release = "wavora-desktop@${VersionManager.getVersionName()}"
            options.setDiagnosticLevel(SentryLevel.ERROR)
        }
    }

    val mediaPlayerHandler by inject<MediaPlayerHandler>(MediaPlayerHandler::class.java)
    mediaPlayerHandler.showToast = { type ->
        showToast(
            when (type) {
                ToastType.ExplicitContent -> {
                    runBlocking { getString(Res.string.explicit_content_blocked) }
                }

                is ToastType.PlayerError -> {
                    runBlocking { getString(Res.string.time_out_check_internet_connection_or_change_piped_instance_in_settings, type.error) }
                }
            },
        )
    }
    mediaPlayerHandler.pushPlayerError = { error ->
        Sentry.withScope { scope ->
            Sentry.captureMessage("Player Error: ${error.message}, code: ${error.errorCode}, code name: ${error.errorCodeName}")
        }
    }

    // Register wavora:// protocol handler on Windows (HKCU, no admin needed)
    WindowsProtocolRegistrar.register()

    val sharedViewModel = getKoin().get<SharedViewModel>()
    if (sharedViewModel.shouldCheckForUpdate()) {
        sharedViewModel.checkForUpdate()
    }

    // Connect deep link handler to SharedViewModel
    DesktopDeepLinkHandler.listener = { intent ->
        sharedViewModel.setIntent(intent)
    }

    application {
        // Main Window - restore saved size and position
        val windowPrefs = java.util.prefs.Preferences.userRoot().node("Wavora/MainWindow")
        val savedWidth = windowPrefs.getFloat("width", 1280f)
        val savedHeight = windowPrefs.getFloat("height", 780f)
        val savedX = windowPrefs.getInt("x", -1)
        val savedY = windowPrefs.getInt("y", -1)
        val wasMaximized = windowPrefs.getBoolean("maximized", false)

        val windowState =
            rememberWindowState(
                size = DpSize(savedWidth.dp, savedHeight.dp),
                placement = if (wasMaximized) androidx.compose.ui.window.WindowPlacement.Maximized
                            else androidx.compose.ui.window.WindowPlacement.Floating,
            )
        var isVisible by remember { mutableStateOf(true) }
        // The single-instance guard now runs before startKoin (top of
        // runDesktopApp). Here we only react to a restore request raised when a
        // second instance launches: bring the window back to the foreground and
        // consume any deep link the second instance forwarded.
        LaunchedEffect(Unit) {
            DesktopRestoreSignal.requests.collect {
                isVisible = true
                windowState.isMinimized = false
                DesktopDeepLinkHandler.consumePendingUri()
            }
        }
        val openAppString = stringResource(Res.string.open_app)
        val quitAppString = stringResource(Res.string.quit_app)
        val openMiniPlayer = stringResource(Res.string.open_miniplayer)
        val closeMiniPlayer = stringResource(Res.string.close_miniplayer)
        Tray(
            icon = painterResource(Res.drawable.circle_app_icon),
            tooltip = stringResource(Res.string.app_name),
            primaryAction = {
                isVisible = true
                windowState.isMinimized = false
            },
        ) {
            if (!isVisible) {
                Item(openAppString) {
                    isVisible = true
                    windowState.isMinimized = false
                }
            }
            if (MiniPlayerManager.isOpen) {
                Item(closeMiniPlayer) {
                    MiniPlayerManager.isOpen = false
                }
            } else {
                Item(openMiniPlayer) {
                    MiniPlayerManager.isOpen = true
                }
            }
            Divider()
            Item(quitAppString) {
                mediaPlayerHandler.release()
                exitApplication()
            }
        }
        // WAVORA: Use undecorated frameless window on ALL platforms.
        // The invisible window bug on Windows is fixed by SWING layers + SOFTWARE
        // rendering set at startup. This gives us the custom Wavora title bar
        // with buttons on the right instead of the ugly white Windows frame.
        val isUseDecorated = remember { false }
        Window(
            onCloseRequest = {
                // Save window size and position for next launch
                if (windowState.placement == androidx.compose.ui.window.WindowPlacement.Floating) {
                    windowPrefs.putFloat("width", windowState.size.width.value)
                    windowPrefs.putFloat("height", windowState.size.height.value)
                    windowPrefs.putBoolean("maximized", false)
                } else {
                    windowPrefs.putBoolean("maximized", true)
                }
                windowPrefs.flush()
                isVisible = false
            },
            title = stringResource(Res.string.app_name),
            icon = painterResource(Res.drawable.circle_app_icon),
            undecorated = true,
            transparent = true,
            state = windowState,
            visible = isVisible,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp)),
            ) {
                if (!isUseDecorated) {
                    CustomTitleBar(
                        title = stringResource(Res.string.app_name),
                        windowState = windowState,
                        window = window,
                        onCloseRequest = {
                            isVisible = false
                        },
                    )
                }

                val context = LocalPlatformContext.current
                setSingletonImageLoaderFactory {
                    ImageLoader
                        .Builder(context)
                        .components {
                            add(
                                OkHttpNetworkFetcherFactory(
                                    callFactory = {
                                        OkHttpClient()
                                    },
                                ),
                            )
                        }.diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .diskCache(
                            DiskCache
                                .Builder()
                                .directory(FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "image_cache")
                                .maxSizeBytes(512L * 1024 * 1024)
                                .build(),
                        ).crossfade(true)
                        .build()
                }
                App()
                ToastHost()
            }
        }

        // Mini Player Window (separate window)
        if (MiniPlayerManager.isOpen) {
            MiniPlayerWindow(
                sharedViewModel = sharedViewModel,
                onCloseRequest = {
                    MiniPlayerManager.isOpen = false
                },
            )
        }
    }
}

/**
 * Bridges a restore request from the single-instance guard (which runs outside
 * Compose, at the top of [runDesktopApp]) into the running window's composition.
 * The guard calls [request] when a second instance launches; the window collects
 * [requests] to bring itself back to the foreground and pick up any deep link.
 */
private object DesktopRestoreSignal {
    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests: SharedFlow<Unit> = _requests.asSharedFlow()

    fun request() {
        _requests.tryEmit(Unit)
    }
}