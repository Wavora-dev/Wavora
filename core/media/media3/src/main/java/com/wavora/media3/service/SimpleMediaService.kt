package com.wavora.media3.service

import android.app.Activity
import android.app.ActivityManager
import android.app.ActivityManager.RunningAppProcessInfo
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.getSystemService
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import androidx.media3.ui.DefaultMediaDescriptionAdapter
import androidx.media3.ui.PlayerNotificationManager
import com.google.common.util.concurrent.MoreExecutors
import com.wavora.common.MEDIA_NOTIFICATION
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.domain.mediaservice.player.MediaPlayerInterface
import com.wavora.logger.Logger
import com.wavora.media3.exoplayer.CrossfadeExoPlayerAdapter
import com.wavora.media3.R
import com.wavora.media3.extension.toCommandButton
import com.wavora.media3.utils.CoilBitmapLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.koin.core.qualifier.named
import kotlin.system.exitProcess
import kotlin.time.Duration.Companion.seconds

@UnstableApi
internal class SimpleMediaService :
    MediaLibraryService(),
    KoinComponent {
    private val coroutineScope by inject<CoroutineScope>(named(com.wavora.common.Config.SERVICE_SCOPE))
    private val mediaPlayerAdapter: MediaPlayerInterface by inject<MediaPlayerInterface>()
    private val player: Player by lazy {
        (mediaPlayerAdapter as CrossfadeExoPlayerAdapter).forwardingPlayer
    }
    private val coilBitmapLoader: CoilBitmapLoader by inject<CoilBitmapLoader>()

    private var mediaSession: MediaLibrarySession? = null

    private val simpleMediaSessionCallback: MediaLibrarySession.Callback by inject<MediaLibrarySession.Callback>()

    private val simpleMediaServiceHandler: MediaPlayerHandler by inject<MediaPlayerHandler>()
    private val dataStoreManager: DataStoreManager by inject<DataStoreManager>()

    private val binder = MusicBinder()

    private lateinit var playerNotificationManager: PlayerNotificationManager

    inner class MusicBinder : Binder() {
        val service: SimpleMediaService
            get() = this@SimpleMediaService

        fun setActivitySession(
            context: Context,
            activity: Class<out Activity>,
        ) {
            mediaSession?.setSessionActivity(
                PendingIntent.getActivity(
                    context,
                    0,
                    Intent(context, activity),
                    PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.w("Service", "Simple Media Service Bound")
        return super.onBind(intent) ?: binder
    }

    @UnstableApi
    override fun onCreate() {
        super.onCreate()
        Logger.w("Service", "Simple Media Service Created")

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { MEDIA_NOTIFICATION.NOTIFICATION_ID },
                MEDIA_NOTIFICATION.NOTIFICATION_CHANNEL_ID,
                R.string.notification_channel_name,
            ).apply {
                setSmallIcon(R.drawable.mono)
            },
        )

        if (mediaSession == null) {
            mediaSession =
                provideMediaLibrarySession(
                    this,
                    player,
                    simpleMediaSessionCallback,
                )
        }

        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }

        val sessionToken = SessionToken(this, ComponentName(this, SimpleMediaService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())

        // FIX (posible causa del ANR reportado en Sentry, Redmi Note 9 Pro): antes esto
        // usaba runBlocking{} para leer el DataStore, bloqueando el hilo principal
        // (Service.onCreate corre en main thread) hasta que termine la lectura de disco.
        // En un dispositivo con almacenamiento lento o bajo presión de memoria (típico en
        // hardware de gama media/baja), esa lectura puede demorar lo suficiente como para
        // disparar un ANR "en segundo plano". Ahora se lanza en el scope de corutinas del
        // servicio en vez de bloquear - todo lo que depende de este valor (que ya era
        // exclusivamente para la feature opcional "keepServiceAlive") queda adentro del
        // launch, sin afectar el resto de onCreate().
        coroutineScope.launch {
            if (dataStoreManager.keepServiceAlive.first() == DataStoreManager.TRUE) {
            val notificationManager = getSystemService<NotificationManager>()
            notificationManager?.run {
                createNotificationChannel(
                    NotificationChannel(
                        "media_playback_channel",
                        "Now playing",
                        NotificationManager.IMPORTANCE_LOW,
                    ).apply {
                        setSound(null, null)
                        enableLights(false)
                        enableVibration(false)
                    },
                )
            }
            playerNotificationManager =
                PlayerNotificationManager
                    .Builder(this@SimpleMediaService, 2026, "media_playback_channel")
                    .setNotificationListener(
                        object : PlayerNotificationManager.NotificationListener {
                            override fun onNotificationPosted(
                                notificationId: Int,
                                notification: Notification,
                                ongoing: Boolean,
                            ) {
                                fun startFg() {
                                    try {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                            startForeground(
                                                notificationId,
                                                notification,
                                                FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
                                            )
                                        } else {
                                            startForeground(notificationId, notification)
                                        }
                                    } catch (e: Exception) {
                                        // FIX (crash real reportado en Sentry, Redmi Note 9 Pro/MIUI):
                                        // ForegroundServiceStartNotAllowedException (API 31+) puede
                                        // ocurrir en cualquier momento entre que el servicio arranca y
                                        // que esta notificación asíncrona llega a postearse - sobre
                                        // todo en fabricantes con gestión de batería agresiva (MIUI,
                                        // ColorOS, etc.) que pueden revocar el permiso de foreground
                                        // en el medio. Google documenta esto como algo esperable e
                                        // indica capturarlo en vez de intentar prevenirlo del todo:
                                        // https://developer.android.com/develop/background-work/services/fgs/exception
                                        // Sin este catch, esto tiraba abajo toda la app en vez de
                                        // simplemente perder esta actualización puntual de notificación.
                                        Logger.e(
                                            "Service",
                                            "startForeground falló (probable restricción del " +
                                                "fabricante), continuando sin crashear: ${e.message}",
                                        )
                                    }
                                }
                                // Call startForeground once when the notification is first posted.
                                // Repeating it in a loop is unnecessary and wastes battery.
                                startFg()
                            }
                        },
                    ).setMediaDescriptionAdapter(DefaultMediaDescriptionAdapter(mediaSession?.sessionActivity))
                    .build()
            playerNotificationManager.setPlayer(player)
            playerNotificationManager.setSmallIcon(R.drawable.mono)
            mediaSession?.platformToken?.let { playerNotificationManager.setMediaSessionToken(it) }
            }
        }

        simpleMediaServiceHandler.onUpdateNotification = { list ->
            val commandButtonList = list.map { it.toCommandButton(this) }
            mediaSession?.setMediaButtonPreferences(
                commandButtonList,
            )
        }
    }

    @UnstableApi
    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        Logger.w("Service", "Simple Media Service Received Action: ${intent?.action}")
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? = mediaSession

    @UnstableApi
    override fun onUpdateNotification(
        session: MediaSession,
        startInForegroundRequired: Boolean,
    ) {
        super.onUpdateNotification(session, startInForegroundRequired)
    }

    @UnstableApi
    fun release() {
        Logger.w("Service", "Starting release process")
        runBlocking {
            try {
                // Release MediaSession (don't release player - CrossfadeExoPlayerAdapter manages it)
                mediaSession?.run {
                    this.player.pause()
                    this.player.playWhenReady = false
                    // Don't call this.player.release() - CrossfadeExoPlayerAdapter manages player lifecycle
                    this.release()
                }
                // Release handler (contains coroutines and jobs, which also releases the adapter)
                simpleMediaServiceHandler.release()
                mediaSession = null
                Logger.w("Service", "Simple Media Service Released")
            } catch (e: Exception) {
                Logger.e("Service", "Error during release")
            }
        }
    }

    @UnstableApi
    override fun onDestroy() {
        super.onDestroy()
        Logger.w("Service", "Simple Media Service Destroyed")
        if (simpleMediaServiceHandler.shouldReleaseOnTaskRemoved()) {
            release()
        }
    }

    override fun onTrimMemory(level: Int) {
        Logger.w("Service", "Simple Media Service Trim Memory Level: $level")
        simpleMediaServiceHandler.mayBeSaveRecentSong()
    }

    @UnstableApi
    override fun onTaskRemoved(rootIntent: Intent?) {
        Logger.w("Service", "Simple Media Service Task Removed")
        if (simpleMediaServiceHandler.shouldReleaseOnTaskRemoved()) {
            release()
            super.onTaskRemoved(rootIntent)
            exitProcess(0)
        }
    }

    // Can't inject by Koin because it depend on service
    @UnstableApi
    private fun provideMediaLibrarySession(
        service: MediaLibraryService,
        player: Player,
        callback: MediaLibrarySession.Callback,
    ): MediaLibrarySession =
        MediaLibrarySession
            .Builder(
                service,
                player,
                callback,
            ).setId(this.javaClass.name)
            .setBitmapLoader(coilBitmapLoader)
            .build()

    private fun isAppInForeground(): Boolean {
        val appProcessInfo = RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(appProcessInfo)
        return appProcessInfo.importance == RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }
}