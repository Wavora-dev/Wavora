package com.wavora.app.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.wavora.app.ui.theme.wavoraIconGradientBrush

/**
 * Toggle switch with the wavora violet -> cyan gradient on its track when checked,
 * instead of Material3's Switch (whose SwitchColors only accepts a solid Color for
 * checkedTrackColor, and has no track/thumb customization slot to work around that).
 *
 * Drop-in replacement for `Switch(checked, onCheckedChange, enabled)` at the two call
 * sites that needed a gradient track (SettingItem.kt, ModalBottomSheet.kt).
 */
@Composable
fun GradientSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val trackWidth = 52.dp
    val trackHeight = 32.dp
    val thumbSize = 24.dp
    val thumbPadding = 4.dp

    val thumbOffset by animateDpAsState(
        targetValue = if (checked) trackWidth - thumbSize - thumbPadding else thumbPadding,
        animationSpec = tween(durationMillis = 150),
        label = "GradientSwitchThumbOffset",
    )
    val trackColorWhenUnchecked by animateColorAsState(
        targetValue = if (checked) Color.Transparent else Color(0xFF3D3D5C),
        animationSpec = tween(durationMillis = 150),
        label = "GradientSwitchTrackColor",
    )

    Box(
        modifier =
            modifier
                .size(width = trackWidth, height = trackHeight)
                .alpha(if (enabled) 1f else 0.4f)
                .clip(CircleShape)
                // Unchecked: flat dim color (was Material3's default outline/gray track).
                // Checked: the brand gradient, painted as a real background fill here (this
                // is a track/shape, not an icon glyph, so the earlier "no background
                // circles" concern doesn't apply -- a colored track IS the switch).
                .background(trackColorWhenUnchecked)
                .then(
                    if (checked) {
                        Modifier.background(wavoraIconGradientBrush)
                    } else {
                        Modifier
                    },
                )
                .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .padding(start = thumbOffset)
                    .size(thumbSize)
                    .clip(CircleShape)
                    .background(Color.White),
        )
    }
}
