package com.wavora.app.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.wavora.app.viewModel.base.BaseViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Immutable
data class EqualizerUiState(
    /** false en cualquier plataforma que no sea Desktop/VLC (ver [com.wavora.domain.mediaservice.player.MediaPlayerInterface]). */
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val bandFrequencies: List<Float> = emptyList(),
    val bandAmps: List<Float> = emptyList(),
    val preamp: Float = 0f,
    val presets: List<String> = emptyList(),
    val selectedPreset: String? = null,
)

class EqualizerViewModel : BaseViewModel() {
    private val _uiState = MutableStateFlow(EqualizerUiState())
    val uiState: StateFlow<EqualizerUiState> = _uiState.asStateFlow()

    init {
        refreshFromPlayer()
    }

    private fun refreshFromPlayer() {
        val player = mediaPlayerHandler.player
        if (!player.supportsEqualizer()) {
            _uiState.value = EqualizerUiState(supported = false)
            return
        }
        _uiState.value =
            EqualizerUiState(
                supported = true,
                enabled = player.isEqualizerEnabled(),
                bandFrequencies = player.getEqualizerBandFrequencies(),
                bandAmps = player.getEqualizerBandAmps(),
                preamp = player.getEqualizerPreamp(),
                presets = player.getEqualizerPresetNames(),
                selectedPreset = player.getEqualizerPresetName(),
            )
    }

    fun setEnabled(enabled: Boolean) {
        mediaPlayerHandler.player.setEqualizerEnabled(enabled)
        _uiState.value = _uiState.value.copy(enabled = enabled)
    }

    fun setPreamp(value: Float) {
        val player = mediaPlayerHandler.player
        player.setEqualizerPreamp(value)
        // No asumimos que el preset queda deseleccionado: si había una ranura
        // Custom 1/2 activa, el player la deja seleccionada a propósito (ver
        // VlcPlayerAdapter.persistCurrentBandsOrSlot) - leemos el valor real.
        _uiState.value = _uiState.value.copy(preamp = value, selectedPreset = player.getEqualizerPresetName())
    }

    fun setBandAmp(
        index: Int,
        value: Float,
    ) {
        val player = mediaPlayerHandler.player
        player.setEqualizerBandAmp(index, value)
        val newAmps = _uiState.value.bandAmps.toMutableList()
        if (index in newAmps.indices) newAmps[index] = value
        _uiState.value = _uiState.value.copy(bandAmps = newAmps, selectedPreset = player.getEqualizerPresetName())
    }

    fun selectPreset(name: String) {
        mediaPlayerHandler.player.loadEqualizerPreset(name)
        // Releemos en vez de asumir los valores: el preset es quien decide los
        // dB reales de cada banda, no algo que conozcamos de este lado.
        refreshFromPlayer()
    }

    /** slot 1 o 2 - carga la ranura de usuario Custom 1/Custom 2. */
    fun selectCustomSlot(slot: Int) {
        viewModelScope.launch {
            mediaPlayerHandler.player.loadEqualizerCustomSlot(slot)
            refreshFromPlayer()
        }
    }

    /** Restablece lo seleccionado actualmente (preset o ranura Custom) a su estado por defecto. */
    fun reset() {
        mediaPlayerHandler.player.resetEqualizerToDefault()
        refreshFromPlayer()
    }
}
