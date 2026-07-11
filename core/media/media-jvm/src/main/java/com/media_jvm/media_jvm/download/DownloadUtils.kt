package com.wavora.media_jvm.download

import com.wavora.common.MERGING_DATA_TYPE
import com.wavora.domain.model.entities.DownloadState
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.mediaservice.handler.DownloadHandler
import com.wavora.domain.repository.SongRepository
import com.wavora.domain.repository.StreamRepository
import com.wavora.domain.utils.toTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.launch
import java.io.File

internal class DownloadUtils(
    private val dataStoreManager: DataStoreManager,
    private val streamRepository: StreamRepository,
    private val songRepository: SongRepository,
) : DownloadHandler {
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var _downloads = MutableStateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>(emptyMap())

    // Audio / Video
    override val downloads: StateFlow<Map<String, Pair<DownloadHandler.Download?, DownloadHandler.Download?>>>
        get() = _downloads
    private val _downloadTask = MutableStateFlow<Map<String, Int>>(emptyMap())
    override val downloadTask: StateFlow<Map<String, Int>> get() = _downloadTask

    val downloadingVideoIds = MutableStateFlow<MutableSet<String>>(mutableSetOf())

    init {
    }

    override suspend fun downloadTrack(
        videoId: String,
        title: String,
        thumbnail: String,
    ) {
        val song = songRepository.getSongById(videoId).lastOrNull()
        if (song != null) {
            songRepository.updateDownloadState(
                videoId,
                DownloadState.STATE_DOWNLOADING,
            )
            if (!File(getDownloadPath()).exists()) {
                File(getDownloadPath()).mkdirs()
            }
            // Same thumbnail heuristic as the Android DownloadUtils (see comment there): a
            // song's thumbnail is rewritten to the square w544/h544 format, real videos keep
            // their original 16:9-style URL. Previously this was hardcoded to `isVideo = false`,
            // so bulk playlist downloads on Desktop never actually fetched the video part even
            // for video tracks — only the single-track download from NowPlayingScreen did.
            val isSongThumbnail = thumbnail.contains("w544") && thumbnail.contains("h544")
            val looksLikeVideoThumbnail =
                thumbnail.contains("maxresdefault") ||
                    thumbnail.contains("sddefault") ||
                    thumbnail.contains("hqdefault") ||
                    thumbnail.contains("hq720")
            val isVideo =
                !isSongThumbnail &&
                    looksLikeVideoThumbnail &&
                    dataStoreManager.downloadVideoEnabled.firstOrNull() != DataStoreManager.Values.FALSE
            songRepository
                .downloadToFile(
                    song.toTrack(),
                    path = getDownloadPath() + File.separator + videoId,
                    videoId = videoId,
                    isVideo = isVideo,
                ).collect { state ->
                    if (state.isError) {
                        songRepository.updateDownloadState(
                            videoId,
                            DownloadState.STATE_NOT_DOWNLOADED,
                        )
                    } else if (state.isDone) {
                        songRepository.updateDownloadState(
                            videoId,
                            DownloadState.STATE_DOWNLOADED,
                        )
                    }
                }
        }
    }

    override fun removeDownload(videoId: String) {
        File(getDownloadPath())
            .listFiles()
            .filter {
                it.name.contains(videoId)
            }.forEach {
                it.delete()
                coroutineScope.launch {
                    songRepository.updateDownloadState(
                        videoId,
                        DownloadState.STATE_NOT_DOWNLOADED,
                    )
                }
            }
    }

    override fun removeAllDownloads() {
        File(getDownloadPath()).listFiles().forEach {
            it.delete()
            coroutineScope.launch {
                songRepository.updateDownloadState(
                    it.name.split(".").first().removePrefix(
                        MERGING_DATA_TYPE.VIDEO,
                    ),
                    DownloadState.STATE_NOT_DOWNLOADED,
                )
            }
        }
    }
}

fun getDownloadPath(): String = System.getProperty("user.home") + File.separator + ".wavora" + File.separator + "downloads"