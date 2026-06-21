package com.wavora.app

import android.annotation.SuppressLint
import android.app.Application
import android.database.CursorWindow
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.crossfade
import com.wavora.appdata.di.loader.loadAllModules
import com.wavora.domain.manager.DataStoreManager
import com.wavora.logger.Logger
import co.touchlab.kermit.Logger as KermitLogger
import co.touchlab.kermit.Severity
import com.wavora.app.di.viewModelModule
import com.wavora.app.service.backup.AutoBackupScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import multiplatform.network.cmptoast.AppContext
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import okio.FileSystem
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import com.wavora.crashlytics.configCrashlytics
import java.lang.reflect.Field

class WavoraApplication :
    Application(),
    KoinComponent,
    SingletonImageLoader.Factory {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dataStoreManager: DataStoreManager by inject()
    private lateinit var autoBackupScheduler: AutoBackupScheduler

    override fun onCreate() {
        super.onCreate()
        // Kermit defaults to logging everything (Severity.Verbose) regardless of build type.
        // The ProGuard rule for android.util.Log only strips calls made directly through that
        // class — it has no effect on Kermit, which formats and dispatches every Logger.d()
        // call across the whole codebase (network responses, lyrics sync, playback progress)
        // unless told otherwise. Debug keeps full logs for development; release keeps only
        // warnings/errors so Sentry breadcrumbs still work.
        KermitLogger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Verbose else Severity.Warn)
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        configCrashlytics(this, BuildKonfig.sentryDsn)
        startKoin {
            androidLogger(level = Level.DEBUG)
            androidContext(this@WavoraApplication)
            loadAllModules()
            loadKoinModules(viewModelModule)
        }
        // provide custom configuration
        val workConfig =
            Configuration
                .Builder()
                .setMinimumLoggingLevel(Log.INFO)
                .build()

        // initialize WorkManager
        WorkManager.initialize(this, workConfig)

        // Initialize and start AutoBackupScheduler
        autoBackupScheduler = AutoBackupScheduler(this, dataStoreManager)
        applicationScope.launch {
            autoBackupScheduler.observeAndSchedule()
        }

        CaocConfig.Builder
            .create()
            .backgroundMode(CaocConfig.BACKGROUND_MODE_SILENT) // default: CaocConfig.BACKGROUND_MODE_SHOW_CUSTOM
            .enabled(true) // default: true
            .showErrorDetails(true) // default: true
            .showRestartButton(true) // default: true
            .errorDrawable(R.mipmap.ic_launcher_round)
            .logErrorOnRestart(false) // default: true
            .trackActivities(true) // default: false
            .minTimeBetweenCrashesMs(2000) // default: 3000 //default: bug image
            .restartActivity(MainActivity::class.java) // default: null (your app's launch activity)
            .apply()

        @SuppressLint("DiscouragedPrivateApi")
        val field: Field = CursorWindow::class.java.getDeclaredField("sCursorWindowSize")
        field.isAccessible = true
        val expectSize = 100 * 1024 * 1024
        field.set(null, expectSize)

        AppContext.apply {
            set(applicationContext)
        }
    }

    override fun onTerminate() {
        super.onTerminate()

        Logger.w("Terminate", "Checking")
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader
            .Builder(context)
            .components {
                add(
                    OkHttpNetworkFetcherFactory(
                        // A single shared OkHttpClient with an explicit connection pool,
                        // instead of OkHttp's bare defaults. Coil caches this factory's
                        // result, so this client lives for the lifetime of the ImageLoader.
                        callFactory = {
                            OkHttpClient.Builder()
                                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                                .build()
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