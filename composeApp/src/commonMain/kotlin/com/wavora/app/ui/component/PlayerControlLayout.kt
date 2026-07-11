package com.wavora.app.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.wavora.app.extension.wavoraIconGradient
import com.wavora.app.ui.theme.wavoraIconGradientBrush
import com.wavora.domain.mediaservice.handler.ControlState
import com.wavora.domain.mediaservice.handler.RepeatState
import com.wavora.app.ui.theme.seed
import com.wavora.app.ui.theme.transparent
import com.wavora.app.viewModel.UIEvent
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.shuffle
import wavora.composeapp.generated.resources.previous_track
import wavora.composeapp.generated.resources.play
import wavora.composeapp.generated.resources.pause
import wavora.composeapp.generated.resources.next_track
import wavora.composeapp.generated.resources.repeat_off
import wavora.composeapp.generated.resources.repeat_all
import wavora.composeapp.generated.resources.repeat_one

@Composable
fun PlayerControlLayout(
    controllerState: ControlState,
    isSmallSize: Boolean = false,
    onUIEvent: (UIEvent) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val height = if (isSmallSize) 48.dp else 96.dp
    val smallIcon = if (isSmallSize) 20.dp to 28.dp else 32.dp to 42.dp
    val mediumIcon = if (isSmallSize) 28.dp to 38.dp else 42.dp to 52.dp
    val bigIcon = if (isSmallSize) 38.dp to 48.dp else 72.dp to 96.dp
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(height)
                .padding(horizontal = 20.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .background(transparent)
                        .size(smallIcon.second)
                        .aspectRatio(1f)
                        .clip(
                            CircleShape,
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUIEvent(UIEvent.Shuffle)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = controllerState.isShuffle, label = "Shuffle Button") { isShuffle ->
                    if (!isShuffle) {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            tint = Color.White,
                            contentDescription = stringResource(Res.string.shuffle),
                            modifier = Modifier.size(smallIcon.first),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Shuffle,
                            tint = seed,
                            contentDescription = stringResource(Res.string.shuffle),
                            modifier = Modifier.size(smallIcon.first),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .background(transparent)
                        .size(mediumIcon.second)
                        .aspectRatio(1f)
                        .clip(
                            CircleShape,
                        )
                        .clickable {
                            if (controllerState.isPreviousAvailable) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUIEvent(UIEvent.Previous)
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipPrevious,
                    tint = Color.White,
                    contentDescription = stringResource(Res.string.previous_track),
                    modifier =
                        Modifier
                            .size(mediumIcon.first)
                            // Gradient painted on the glyph, no background shape. Disabled
                            // state swaps to a dim solid color instead of the gradient.
                            .wavoraIconGradient(
                                brush =
                                    if (controllerState.isPreviousAvailable) {
                                        wavoraIconGradientBrush
                                    } else {
                                        SolidColor(Color(0xFF3D3D5C))
                                    },
                            ),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .background(transparent)
                        .size(bigIcon.second)
                        .aspectRatio(1f)
                        .clip(
                            CircleShape,
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUIEvent(UIEvent.PlayPause)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = controllerState.isPlaying) { isPlaying ->
                    if (!isPlaying) {
                        Icon(
                            imageVector = Icons.Rounded.PlayCircle,
                            tint = Color.White,
                            contentDescription = stringResource(Res.string.play),
                            modifier = Modifier.size(bigIcon.first).wavoraIconGradient(),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.PauseCircle,
                            tint = Color.White,
                            contentDescription = stringResource(Res.string.pause),
                            modifier = Modifier.size(bigIcon.first).wavoraIconGradient(),
                        )
                    }
                }
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .background(transparent)
                        .size(mediumIcon.second)
                        .aspectRatio(1f)
                        .clip(
                            CircleShape,
                        )
                        .clickable {
                            if (controllerState.isNextAvailable) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onUIEvent(UIEvent.Next)
                            }
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.SkipNext,
                    tint = Color.White,
                    contentDescription = stringResource(Res.string.next_track),
                    modifier =
                        Modifier
                            .size(mediumIcon.first)
                            .wavoraIconGradient(
                                brush =
                                    if (controllerState.isNextAvailable) {
                                        wavoraIconGradientBrush
                                    } else {
                                        SolidColor(Color(0xFF3D3D5C))
                                    },
                            ),
                )
            }
        }
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Box(
                modifier =
                    Modifier
                        .size(smallIcon.second)
                        .aspectRatio(1f)
                        .clip(
                            CircleShape,
                        )
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onUIEvent(UIEvent.Repeat)
                        },
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(targetState = controllerState.repeatState) { rs ->
                    when (rs) {
                        is RepeatState.None -> {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                tint = Color.White,
                                contentDescription = stringResource(Res.string.repeat_off),
                                modifier = Modifier.size(smallIcon.first),
                            )
                        }

                        RepeatState.All -> {
                            Icon(
                                imageVector = Icons.Rounded.Repeat,
                                tint = seed,
                                contentDescription = stringResource(Res.string.repeat_all),
                                modifier = Modifier.size(smallIcon.first),
                            )
                        }

                        RepeatState.One -> {
                            Icon(
                                imageVector = Icons.Rounded.RepeatOne,
                                tint = seed,
                                contentDescription = stringResource(Res.string.repeat_one),
                                modifier = Modifier.size(smallIcon.first),
                            )
                        }
                    }
                }
            }
        }
    }
}