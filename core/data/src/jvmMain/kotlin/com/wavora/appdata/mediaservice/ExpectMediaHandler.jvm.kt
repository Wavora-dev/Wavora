package com.wavora.appdata.mediaservice

import com.wavora.domain.repository.AnalyticsRepository

actual fun createMediaServiceHandler(
    dataStoreManager: com.wavora.domain.manager.DataStoreManager,
    songRepository: com.wavora.domain.repository.SongRepository,
    streamRepository: com.wavora.domain.repository.StreamRepository,
    localPlaylistRepository: com.wavora.domain.repository.LocalPlaylistRepository,
    analyticsRepository: AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.wavora.domain.mediaservice.handler.MediaPlayerHandler =
    JvmMediaPlayerHandlerImpl(
        dataStoreManager = dataStoreManager,
        songRepository = songRepository,
        streamRepository = streamRepository,
        localPlaylistRepository = localPlaylistRepository,
        analyticsRepository = analyticsRepository,
        coroutineScope = coroutineScope,
    )