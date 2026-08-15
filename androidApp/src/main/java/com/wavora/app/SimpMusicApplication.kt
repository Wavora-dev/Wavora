package com.wavora.app

import android.annotation.SuppressLint
import android.app.Application
import android.database.CursorWindow
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.work.Configuration
import androidx.work.WorkManager
import cat.ereza.customactivityoncrash.config.CaocConfig
import coil3.memory.MemoryCache
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
        // AUDIT FIX: movido acá, ANTES de cualquier otra cosa - literalmente
        // lo primero que corre en onCreate(). CustomActivityOnCrash necesita
        // estar instalado ANTES de que pase el crash para poder mostrarlo -
        // si algo más abajo (Kermit, el file log writer recién movido de
        // módulo, Koin, etc.) crashea antes de que esto se configure, el
        // usuario ve el diálogo genérico de Android ("Wavora sigue
        // fallando") en vez de la pantalla de detalles de error de esta
        // librería - que es justo lo que necesitamos para diagnosticar sin
        // ADB. Antes estaba varias líneas más abajo, después de Kermit/Koin/
        // WorkManager, dejando todo eso sin cobertura si fallaba antes de
        // llegar acá.
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

        // Kermit defaults to logging everything (Severity.Verbose) regardless of build type.
        // The ProGuard rule for android.util.Log only strips calls made directly through that
        // class — it has no effect on Kermit, which formats and dispatches every Logger.d()
        // call across the whole codebase (network responses, lyrics sync, playback progress)
        // unless told otherwise. Debug keeps full logs for development; release keeps only
        // warnings/errors so Sentry breadcrumbs still work.
        // AUDIT FIX: Kermit filtra por severidad ANTES de llegar a los
        // writers - no hay forma de que Logcat se quede en Warn/Error (para
        // no ensuciar producción) mientras el archivo de diagnóstico recibe
        // Debug, con un único minSeverity global. Confirmado con un log real
        // de un build release: 0 líneas Debug/Verbose, solo 173 Warn y 3
        // Error - justo donde vive el 95% de los eventos que sirven para
        // diagnosticar (CROSSFADE_*, cambios de estado de Cast, etc.), que
        // se loguean con Logger.d(). Bajamos el piso a Debug también en
        // release: es exactamente lo que hace falta para que el botón
        // "Compartir logs de diagnóstico" (AuditFileLogWriter) sirva para
        // algo. Severity.Verbose se deja solo para debug local (sería
        // demasiado ruido para pedirle un archivo a un usuario real).
        KermitLogger.setMinSeverity(if (BuildConfig.DEBUG) Severity.Verbose else Severity.Debug)
        // AUDIT FIX (crash al abrir la app, sin logcat/adb disponible para
        // diagnosticarlo): aislado en su propia función privada en vez de
        // quedar inline acá arriba. Un try/catch inline dentro de onCreate()
        // no siempre atrapa un fallo de VERIFICACIÓN de bytecode (a
        // diferencia de una excepción en tiempo de ejecución) - Android
        // verifica el método ENTERO antes de correr una sola instrucción, así
        // que si algo dentro del bloque no verifica bien, puede tirar abajo
        // todo onCreate() antes de que el catch llegue a intervenir. Al
        // aislarlo en installFileLogWriter(), el try/catch de acá abajo
        // envuelve el PUNTO DE LLAMADA a esa función completa, que sí es
        // atrapable de forma mucho más confiable si algo adentro falla al
        // cargar/verificar (referencia a AuditFileLogWriter, que se movió de
        // módulo hace poco, o a platformLogWriter() de kermit).
        try {
            installFileLogWriter()
        } catch (e: Throwable) {
            android.util.Log.e("WavoraApplication", "No se pudo instalar el file log writer: ${e.message}")
        }
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

    // AUDIT FIX: separado de onCreate() a propósito - ver comentario en el
    // punto de llamada. Pedirle un log a un usuario random de la app (no
    // vos) para diagnosticar algo que solo pasa en su celular - ej. el bug
    // de Chromecast, que necesita un dispositivo Cast real para
    // reproducirse - era imposible antes de esto: Kermit solo mandaba a
    // Logcat, que requiere ADB + PC. Este writer deja el mismo log
    // persistente que ya existe en Desktop (AuditFileLogWriter), y
    // SettingScreen.kt agrega un botón para compartirlo por WhatsApp/mail
    // sin salir de la app. Se suma platformLogWriter() explícitamente para
    // no perder Logcat en desarrollo (setLogWriters reemplaza los writers
    // activos, no los suma).
    private fun installFileLogWriter() {
        KermitLogger.setLogWriters(co.touchlab.kermit.platformLogWriter(), com.wavora.app.diagnostics.AuditFileLogWriter(this))
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
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.10)
                    .build()
            }
            .build()
}