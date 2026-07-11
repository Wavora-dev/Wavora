package com.wavora.app.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material3.ElevatedFilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.wavora.app.ui.theme.wavoraBorder
import com.wavora.app.ui.theme.wavoraTextSecondary
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.done

@Composable
fun Chip(
    isAnimated: Boolean = false,
    isSelected: Boolean = false,
    text: String,
    onClick: () -> Unit,
) {
    InfiniteBorderAnimationView(
        isAnimated = isAnimated && isSelected,
        backgroundColor = Color.Transparent,
        contentPadding = 0.dp,
        borderWidth = 1.dp,
        shape = CircleShape,
        oneCircleDurationMillis = 2500,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            ElevatedFilterChip(
                shape = CircleShape,
                colors =
                    FilterChipDefaults.elevatedFilterChipColors(
                        containerColor = Color.Transparent,
                        iconColor = Color.White,
                        selectedContainerColor = wavoraBorder.copy(alpha = 0.8f),
                        labelColor = wavoraTextSecondary,
                        selectedLabelColor = Color.White,
                    ),
                onClick = { onClick.invoke() },
                label = {
                    Text(text, maxLines = 1)
                },
                border =
                    FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        selectedBorderColor = Color.Transparent,
                        borderColor = wavoraBorder,
                    ),
                selected = isSelected,
                leadingIcon = {
                    AnimatedContent(isSelected) {
                        if (it) {
                            Icon(
                                imageVector = Icons.Filled.Done,
                                contentDescription = stringResource(Res.string.done),
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    }
                },
            )
        }
    }
}