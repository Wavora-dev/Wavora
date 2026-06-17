package com.wavora.media_jvm_ui.ui

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.wavora.domain.model.model.metadata.Lyrics
import com.wavora.domain.model.model.streams.TimeLine
import com.wavora.domain.mediaservice.handler.MediaPlayerHandler
import com.wavora.media_jvm.VlcPlayerAdapter
import com.wavora.media_jvm.VlcVideoSurfacePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.swing.Swing
import org.koin.compose.koinInject
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import java.awt.Dimension
import javax.swing.JPanel

@Composable
fun MediaPlayerViewWithUrl(
    url: String,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    var videoPanel by remember { mutableStateOf<VlcVideoSurfacePanel?>(null) }
    var vlcFactory by remember { mutableStateOf<MediaPlayerFactory?>(null) }
    var vlcPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(url) {
        scope.launch(Dispatchers.Swing) {
            val factory = MediaPlayerFactory()
            vlcFactory = factory
            val panel = VlcVideoSurfacePanel()

            val player = factory.mediaPlayers().newEmbeddedMediaPlayer()
            val surface = panel.createVideoSurface(factory)
            player.videoSurface().set(surface)
            vlcPlayer = player

            player.events().addMediaPlayerEventListener(
                object : MediaPlayerEventAdapter() {
                    override fun finished(mediaPlayer: MediaPlayer) {
                        // VLCJ deadlocks if you call player methods from the event callback thread.
                        // Dispatch replay onto the Swing EDT to avoid the deadlock.
                        scope.launch(Dispatchers.Swing) {
                            mediaPlayer.media().play(url)
                        }
                    }
                },
            )

            videoPanel = panel
            player.media().play(url)
        }
        onDispose {
            vlcPlayer?.release()
            vlcFactory?.release()
            videoPanel = null
            vlcPlayer = null
            vlcFactory = null
        }
    }

    Box(
        modifier
            .then(
                Modifier
                    .graphicsLayer { clip = true },
            ),
    ) {
        val panel = videoPanel
        if (panel != null) {
            key(panel) {
                SwingPanel(
                    factory = {
                        JPanel(java.awt.BorderLayout()).apply {
                            background = java.awt.Color.BLACK
                            isOpaque = true
                            add(panel, java.awt.BorderLayout.CENTER)
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                )
            }
        }
    }
}

private val RICH_SYNC_TIMESTAMP_REGEX = Regex("""<\d{2}:\d{2}\.\d{2,3}>\s*""")

@Composable
fun MediaPlayerViewWithSubtitleJvm(
    modifier: Modifier,
    playerName: String,
    shouldShowSubtitle: Boolean,
    shouldScaleDownSubtitle: Boolean,
    timelineState: TimeLine,
    lyricsData: Lyrics?,
    translatedLyricsData: Lyrics?,
    mainTextStyle: TextStyle,
    translatedTextStyle: TextStyle,
    mediaPlayerHandler: MediaPlayerHandler = koinInject(),
) {
    val player: VlcPlayerAdapter = koinInject<VlcPlayerAdapter>()

    val state by mediaPlayerHandler.nowPlayingState.collectAsState()
    val videoCanvas by player.currentVideoSurface.collectAsState()

    var sizePx by remember { mutableStateOf(0 to 0) }

    val showArtwork = videoCanvas == null

    val artworkUri = state.songEntity?.thumbnails

    var currentLineIndex by rememberSaveable { mutableIntStateOf(-1) }
    var currentTranslatedLineIndex by rememberSaveable { mutableIntStateOf(-1) }

    // Key on timelineState.current (Long) instead of the full TimeLine object.
    // TimeLine is a data class — a new instance is emitted every 100ms even when current
    // hasn't changed, causing unnecessary re-launches. Keying on the Long skips those.
    // Binary search replaces forEach so lookup is O(log n) instead of O(n).
    LaunchedEffect(key1 = timelineState.current) {
        val currentMs = timelineState.current
        val lines = lyricsData?.lines
        val translatedLines = translatedLyricsData?.lines

        if (currentMs <= 0L || lines.isNullOrEmpty()) {
            currentLineIndex = -1
            currentTranslatedLineIndex = -1
            return@LaunchedEffect
        }

        // Binary search: find last line whose startTimeMs <= currentMs
        fun findLineIndex(lyricLines: List<com.wavora.domain.model.model.metadata.Line>): Int {
            var lo = 0
            var hi = lyricLines.size - 1
            var result = -1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val startMs = lyricLines[mid].startTimeMs.toLong()
                if (startMs <= currentMs) {
                    result = mid
                    lo = mid + 1
                } else {
                    hi = mid - 1
                }
            }
            // Check the found line hasn't ended yet
            if (result != -1) {
                val endMs = if (result < lyricLines.size - 1)
                    lyricLines[result + 1].startTimeMs.toLong()
                else
                    lyricLines[result].startTimeMs.toLong() + 60_000L
                if (currentMs > endMs) result = -1
            }
            return result
        }

        currentLineIndex = findLineIndex(lines)
        if (translatedLines != null) {
            currentTranslatedLineIndex = findLineIndex(translatedLines)
        }
    }

    Box(
        modifier =
            modifier
                .graphicsLayer { clip = true }
                .onGloballyPositioned {
                    val width = it.size.width
                    val height = it.size.height
                    sizePx = width to height
                },
        contentAlignment = Alignment.Center,
    ) {
        // SwingPanel (native Swing component) does not support Compose animation layers
        // (alpha, z-ordering). Using Crossfade here causes the old and new SwingPanel to
        // coexist during the animation, leading to the video not visually switching.
        // Use a simple conditional instead so the old panel is removed immediately.
        if (showArtwork) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(LocalPlatformContext.current)
                        .data(artworkUri)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .diskCacheKey(artworkUri)
                        .crossfade(550)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.FillHeight,
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center),
            )
        } else {
            val canvas = videoCanvas
            if (canvas != null && sizePx.first > 0 && sizePx.second > 0) {
                key(canvas) {
                    SwingPanel(
                        factory = {
                            JPanel(java.awt.BorderLayout()).apply {
                                background = java.awt.Color.BLACK
                                isOpaque = true
                                preferredSize = Dimension(sizePx.first, sizePx.second)
                                add(canvas, java.awt.BorderLayout.CENTER)
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxSize(),
                    )
                }
            }
        }
        if (lyricsData != null && shouldShowSubtitle) {
            Crossfade(
                currentLineIndex != -1,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxSize(),
            ) {
                val lines = lyricsData.lines ?: return@Crossfade
                if (it) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                            .padding(bottom = if (shouldScaleDownSubtitle) 10.dp else 40.dp)
                            .align(Alignment.BottomCenter),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(Modifier.fillMaxWidth(0.7f)) {
                            Column(
                                Modifier.align(Alignment.BottomCenter),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text =
                                        lines
                                            .getOrNull(currentLineIndex)
                                            ?.words
                                            ?.replace(RICH_SYNC_TIMESTAMP_REGEX, "")
                                            ?.trim() ?: return@Crossfade,
                                    style =
                                        mainTextStyle.let { style ->
                                            if (shouldScaleDownSubtitle) {
                                                style.copy(fontSize = style.fontSize * 0.8f)
                                            } else {
                                                style
                                            }
                                        },
                                    color = Color.White,
                                    textAlign = TextAlign.Center,
                                    modifier =
                                        Modifier
                                            .padding(4.dp)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .wrapContentWidth(),
                                )
                                Crossfade(translatedLyricsData?.lines != null, label = "") { translate ->
                                    val translateLines = translatedLyricsData?.lines ?: return@Crossfade
                                    if (translate) {
                                        Text(
                                            text = translateLines.getOrNull(currentTranslatedLineIndex)?.words ?: return@Crossfade,
                                            style =
                                                translatedTextStyle.let { style ->
                                                    if (shouldScaleDownSubtitle) {
                                                        style.copy(fontSize = style.fontSize * 0.8f)
                                                    } else {
                                                        style
                                                    }
                                                },
                                            color = Color.Yellow,
                                            textAlign = TextAlign.Center,
                                            modifier =
                                                Modifier
                                                    .background(Color.Black.copy(alpha = 0.5f))
                                                    .wrapContentWidth(),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}