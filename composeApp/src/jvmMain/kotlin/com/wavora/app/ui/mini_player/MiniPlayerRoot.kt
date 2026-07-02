package com.wavora.app.ui.mini_player

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.wavora.app.viewModel.SharedViewModel
import java.awt.Cursor
import java.awt.MouseInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background


/**
 * Root composable for the mini player window content.
 * Automatically switches between layouts based on window width:
 * - < 260dp: Compact (controls only)
 * - 260-360dp: Medium (artwork + controls)
 * - > 360dp: Full (artwork + info + controls)
 *
 * Shows placeholder when no track is playing.
 * Includes close button and drag handle since window is frameless.
 */
@Composable
fun MiniPlayerRoot(
    sharedViewModel: SharedViewModel,
    onClose: () -> Unit,
    windowState: WindowState,
    window: java.awt.Window,
) {
    val nowPlayingData by sharedViewModel.nowPlayingScreenData.collectAsStateWithLifecycle()
    val controllerState by sharedViewModel.controllerState.collectAsStateWithLifecycle()
    val timeline by sharedViewModel.timeline.collectAsStateWithLifecycle()

    val lyricsData by remember {
        derivedStateOf {
            nowPlayingData.lyricsData
        }
    }

    // Check if there's any track playing
    val hasTrack = nowPlayingData.nowPlayingTitle.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
        color = Color(0xFF0B0B0F),
        shape = RoundedCornerShape(12.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().wrapContentHeight()) {
            if (!hasTrack) {
                // Show empty state
                EmptyMiniPlayerState()
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    // Calculate aspect ratio to detect square/tall layout
                    val aspectRatio = maxWidth.value / maxHeight.value
                    val isSquareOrTall = aspectRatio <= 1.3f && maxHeight >= 200.dp

                    when {
                        isSquareOrTall -> {
                            SquareMiniLayout(
                                nowPlayingData = nowPlayingData,
                                controllerState = controllerState,
                                timeline = timeline,
                                lyricsData = lyricsData,
                                onUIEvent = sharedViewModel::onUIEvent,
                            )
                        }

                        maxWidth < 260.dp -> {
                            CompactMiniLayout(
                                controllerState = controllerState,
                                timeline = timeline,
                                onUIEvent = sharedViewModel::onUIEvent,
                            )
                        }

                        maxWidth < 360.dp -> {
                            MediumMiniLayout(
                                nowPlayingData = nowPlayingData,
                                controllerState = controllerState,
                                timeline = timeline,
                                lyricsData = lyricsData,
                                onUIEvent = sharedViewModel::onUIEvent,
                            )
                        }

                        else -> {
                            ExpandedMiniLayout(
                                nowPlayingData = nowPlayingData,
                                controllerState = controllerState,
                                timeline = timeline,
                                lyricsData = lyricsData,
                                onUIEvent = sharedViewModel::onUIEvent,
                            )
                        }
                    }
                }
            }

            // Close button (top-right corner)
            IconButton(
                onClick = onClose,
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp),
                )
            }

            // Drag handle (top center area — 75% width avoids the resize corners)
            Box(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth(0.75f)
                        .height(28.dp)
                        .pointerInput(Unit) {
                            // Screen-space drag: same approach as CustomTitleBar.kt for the
                            // main window. We track the ABSOLUTE screen mouse position
                            // (MouseInfo.getPointerInfo) and move the real AWT window
                            // directly (window.setLocation), instead of using Compose's
                            // pointer coordinates local to the window (change.position) and
                            // writing to windowState.position.
                            //
                            // The previous approach broke because change.position is
                            // relative to the window's own origin -- and that origin moves
                            // every time we drag. Each frame's local reading already
                            // includes the window's own movement from the previous frame,
                            // so the calculation feeds back into itself: the window
                            // overshoots the mouse, corrects, overshoots again, which is
                            // exactly the jitter / stutter / "walks outside the rectangle"
                            // / snap-back behavior being reported. Absolute screen
                            // coordinates don't move when the window moves, so there's no
                            // feedback loop, and window.setLocation applies immediately
                            // instead of going through Compose state/recomposition.
                            var dragStartX = 0
                            var dragStartY = 0
                            detectDragGestures(
                                onDragStart = {
                                    val mouseLocation = MouseInfo.getPointerInfo().location
                                    dragStartX = mouseLocation.x - window.x
                                    dragStartY = mouseLocation.y - window.y
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val mouseLocation = MouseInfo.getPointerInfo().location
                                    window.setLocation(
                                        mouseLocation.x - dragStartX,
                                        mouseLocation.y - dragStartY,
                                    )
                                },
                            )
                        }.pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR))),
                contentAlignment = Alignment.Center,
            ) {
                // Visual drag indicator — three dots so the user knows this area is draggable
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.3f))
                        )
                    }
                }
            }
        }
    }
}