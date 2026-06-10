package com.wavora.appdata.mediaservice

actual fun createMediaServiceHandler(
    dataStoreManager: com.wavora.domain.manager.DataStoreManager,
    songRepository: com.wavora.domain.repository.SongRepository,
    streamRepository: com.wavora.domain.repository.StreamRepository,
    localPlaylistRepository: com.wavora.domain.repository.LocalPlaylistRepository,
    analyticsRepository: com.wavora.domain.repository.AnalyticsRepository,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
): com.wavora.domain.mediaservice.handler.MediaPlayerHandler {
    TODO("Not yet implemented")
}