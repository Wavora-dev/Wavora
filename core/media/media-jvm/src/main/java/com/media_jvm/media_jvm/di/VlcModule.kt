package com.wavora.media_jvm.di

import com.wavora.common.Config.SERVICE_SCOPE
import com.wavora.domain.mediaservice.handler.DownloadHandler
import com.wavora.domain.mediaservice.player.MediaPlayerInterface
import com.wavora.domain.repository.CacheRepository
import com.wavora.media_jvm.VlcPlayerAdapter
import com.wavora.media_jvm.download.DownloadUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import org.koin.core.context.loadKoinModules
import org.koin.core.qualifier.named
import org.koin.dsl.module

private val vlcModule =
    module {
        single<CoroutineScope>(qualifier = named(SERVICE_SCOPE)) {
            // Single-thread dispatcher: VLC native calls are NOT thread-safe,
            // so all player operations must be serialized on one thread.
            // UI listener notifications are dispatched to Dispatchers.Main separately.
            val vlcDispatcher = Executors.newSingleThreadExecutor { r ->
                Thread(r, "VLC-Player-Thread").apply { isDaemon = true }
            }.asCoroutineDispatcher()
            CoroutineScope(vlcDispatcher + SupervisorJob())
        }

        single<VlcPlayerAdapter> {
            VlcPlayerAdapter(
                coroutineScope = get(named(SERVICE_SCOPE)),
                dataStoreManager = get(),
                streamRepository = get(),
            )
        }

        single<MediaPlayerInterface> {
            get<VlcPlayerAdapter>()
        }
        single<CacheRepository> {
            object : CacheRepository {
                override suspend fun getCacheSize(cacheName: String): Long = 0L

                override fun clearCache(cacheName: String) {}

                override suspend fun getAllCacheKeys(cacheName: String): List<String> = emptyList()
            }
        }
        single<DownloadHandler> {
            DownloadUtils(
                dataStoreManager = get(),
                streamRepository = get(),
                songRepository = get(),
            )
        }
    }

fun loadVlcModule() = loadKoinModules(vlcModule)