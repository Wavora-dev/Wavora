package com.wavora.app.ui.screen.other

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.wavora.app.ui.component.RippleIconButton
import com.wavora.app.ui.theme.LocalAppTypography
import com.wavora.app.viewModel.EqualizerViewModel
import com.wavora.domain.mediaservice.player.MediaPlayerInterface
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.baseline_arrow_back_ios_new_24
import wavora.composeapp.generated.resources.equalizer
import wavora.composeapp.generated.resources.equalizer_custom
import wavora.composeapp.generated.resources.equalizer_enable
import wavora.composeapp.generated.resources.equalizer_not_available
import wavora.composeapp.generated.resources.equalizer_preamp
import wavora.composeapp.generated.resources.equalizer_presets
import wavora.composeapp.generated.resources.equalizer_reset
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(
    navController: NavController,
    viewModel: EqualizerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val custom1Label = MediaPlayerInterface.EQUALIZER_CUSTOM_1_NAME
    val custom2Label = MediaPlayerInterface.EQUALIZER_CUSTOM_2_NAME

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = stringResource(Res.string.equalizer),
                    style = LocalAppTypography.current.titleMedium,
                )
            },
            navigationIcon = {
                RippleIconButton(resId = Res.drawable.baseline_arrow_back_ios_new_24) {
                    navController.navigateUp()
                }
            },
        )

        if (!uiState.supported) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(Res.string.equalizer_not_available),
                    style = LocalAppTypography.current.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(Res.string.equalizer_enable),
                        style = LocalAppTypography.current.bodyLarge,
                    )
                    Switch(
                        checked = uiState.enabled,
                        onCheckedChange = viewModel::setEnabled,
                    )
                }
            }

            // Fila propia para las ranuras de usuario, arriba de los presets
            // curados, para que se destaquen como "las tuyas" - a diferencia
            // de los presets, arrancan planas (0 en todo) hasta que el
            // usuario las edita, y el botón Restablecer vive acá al lado
            // porque aplica a lo que sea que esté seleccionado en este
            // momento (una ranura Custom o un preset curado).
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.selectedPreset == custom1Label,
                            onClick = { viewModel.selectCustomSlot(1) },
                            label = { Text(custom1Label) },
                        )
                        FilterChip(
                            selected = uiState.selectedPreset == custom2Label,
                            onClick = { viewModel.selectCustomSlot(2) },
                            label = { Text(custom2Label) },
                        )
                    }
                    TextButton(onClick = viewModel::reset) {
                        Text(stringResource(Res.string.equalizer_reset))
                    }
                }
            }

            if (uiState.presets.isNotEmpty()) {
                item {
                    Column {
                        Text(
                            text = stringResource(Res.string.equalizer_presets),
                            style = LocalAppTypography.current.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(uiState.presets) { preset ->
                                FilterChip(
                                    selected = uiState.selectedPreset == preset,
                                    onClick = { viewModel.selectPreset(preset) },
                                    label = { Text(preset) },
                                )
                            }
                        }
                        if (uiState.selectedPreset == null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(Res.string.equalizer_custom),
                                style = LocalAppTypography.current.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item {
                EqualizerSliderRow(
                    label = stringResource(Res.string.equalizer_preamp),
                    value = uiState.preamp,
                    onValueChange = viewModel::setPreamp,
                )
            }

            item { HorizontalDivider() }

            itemsIndexed(uiState.bandFrequencies) { index, frequency ->
                EqualizerSliderRow(
                    label = formatFrequencyLabel(frequency),
                    value = uiState.bandAmps.getOrElse(index) { 0f },
                    onValueChange = { viewModel.setBandAmp(index, it) },
                )
            }
        }
    }
}

@Composable
private fun EqualizerSliderRow(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = LocalAppTypography.current.bodyMedium,
            modifier = Modifier.width(64.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = -20f..20f,
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
        )
        Text(
            text = "${if (value >= 0) "+" else ""}${(value * 10).roundToInt() / 10f} dB",
            style = LocalAppTypography.current.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp),
        )
    }
}

/** 125.0 -> "125 Hz", 8000.0 -> "8 kHz", 1220.0 -> "1.2 kHz" */
private fun formatFrequencyLabel(hz: Float): String =
    if (hz >= 1000f) {
        val khz = hz / 1000f
        val rounded = (khz * 10).roundToInt() / 10f
        if (rounded == rounded.roundToInt().toFloat()) "${rounded.roundToInt()} kHz" else "$rounded kHz"
    } else {
        "${hz.roundToInt()} Hz"
    }
