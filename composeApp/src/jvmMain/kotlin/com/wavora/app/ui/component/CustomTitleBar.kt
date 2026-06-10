package com.wavora.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowState
import com.wavora.app.ui.theme.md_theme_dark_background
import com.wavora.app.ui.theme.typo
import java.awt.MouseInfo
import java.awt.Window

// Wavora brand gradient
private val wavoraGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFFA259FF), Color(0xFF6A5CFF), Color(0xFF00D4FF))
)

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CustomTitleBar(
    title: String,
    windowState: WindowState,
    window: Window,
    onCloseRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isMaximized by remember { mutableStateOf(windowState.placement == WindowPlacement.Maximized) }
    var dragStartX by remember { mutableStateOf(0) }
    var dragStartY by remember { mutableStateOf(0) }

    LaunchedEffect(windowState.placement) {
        isMaximized = windowState.placement == WindowPlacement.Maximized
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(Color(0xFF0B0B0F))
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
                            WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                )
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        val mouseLocation = MouseInfo.getPointerInfo().location
                        dragStartX = mouseLocation.x - window.x
                        dragStartY = mouseLocation.y - window.y
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val mouseLocation = MouseInfo.getPointerInfo().location
                        if (windowState.placement == WindowPlacement.Maximized) {
                            windowState.placement = WindowPlacement.Floating
                            dragStartX = (windowState.size.width.value / 2).toInt()
                            dragStartY = 20
                        }
                        window.setLocation(
                            mouseLocation.x - dragStartX,
                            mouseLocation.y - dragStartY,
                        )
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // App title with Wavora gradient accent dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(wavoraGradient)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                style = typo().labelSmall.copy(
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp,
                ),
                color = Color(0xFFB3B3C6),
            )

            Spacer(modifier = Modifier.weight(1f))

            // Windows-style buttons on the RIGHT
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Minimize
                WindowControlButton(
                    onClick = { windowState.isMinimized = true },
                    icon = WindowControlIcon.Minimize,
                    type = ButtonType.Neutral,
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Maximize / Restore
                WindowControlButton(
                    onClick = {
                        windowState.placement = if (windowState.placement == WindowPlacement.Maximized)
                            WindowPlacement.Floating else WindowPlacement.Maximized
                    },
                    icon = if (isMaximized) WindowControlIcon.Restore else WindowControlIcon.Maximize,
                    type = ButtonType.Neutral,
                )
                Spacer(modifier = Modifier.width(4.dp))

                // Close
                WindowControlButton(
                    onClick = onCloseRequest,
                    icon = WindowControlIcon.Close,
                    type = ButtonType.Danger,
                )
            }
        }
    }
}

private enum class WindowControlIcon { Minimize, Maximize, Restore, Close }
private enum class ButtonType { Neutral, Danger }

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun WindowControlButton(
    onClick: () -> Unit,
    icon: WindowControlIcon,
    type: ButtonType,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isHovered && type == ButtonType.Danger -> Color(0xFFFF4444)
        isHovered && type == ButtonType.Neutral -> Color(0xFF1F1F2E)
        else -> Color(0xFF12121A)
    }

    Box(
        modifier = Modifier
            .size(width = 36.dp, height = 28.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onClick() })
            },
        contentAlignment = Alignment.Center,
    ) {
        val iconTint = if (isHovered) Color.White else Color(0xFF6B6B7A)
        when (icon) {
            WindowControlIcon.Minimize ->
                Icon(Icons.Rounded.Remove, tint = iconTint, contentDescription = "Minimize",
                    modifier = Modifier.size(14.dp))
            WindowControlIcon.Maximize ->
                Icon(Icons.Rounded.UnfoldMore, tint = iconTint, contentDescription = "Maximize",
                    modifier = Modifier.size(14.dp).rotate(45f))
            WindowControlIcon.Restore ->
                Icon(Icons.Rounded.UnfoldLess, tint = iconTint, contentDescription = "Restore",
                    modifier = Modifier.size(14.dp).rotate(45f))
            WindowControlIcon.Close ->
                Icon(Icons.Rounded.Close, tint = iconTint, contentDescription = "Close",
                    modifier = Modifier.size(14.dp))
        }
    }
}
