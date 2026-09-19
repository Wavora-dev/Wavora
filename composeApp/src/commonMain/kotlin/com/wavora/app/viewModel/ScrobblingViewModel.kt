package com.wavora.app.viewModel

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.domain.manager.DataStoreManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import org.koin.core.component.inject

@Immutable
data class ScrobblingUiState(
    val supported: Boolean = false,
    val lastFmConfigured: Boolean = false,
    val lastFmEnabled: Boolean = false,
    val lastFmLoggedInAs: String? = null,
    val lastFmLoggingIn: Boolean = false,
    val lastFmError: String? = null,
    val listenBrainzEnabled: Boolean = false,
    val listenBrainzToken: String = "",
)

private data class ScrobbleSettingsSnapshot(
    val lastFmEnabled: String,
    val lastFmUsername: String,
    val listenBrainzEnabled: String,
    val listenBrainzToken: String,
)

class ScrobblingViewModel : BaseViewModel() {
    private val dataStoreManager: DataStoreManager by inject()

    private val _uiState = MutableStateFlow(ScrobblingUiState())
    val uiState: StateFlow<ScrobblingUiState> = _uiState.asStateFlow()

    init {
        val player = mediaPlayerHandler.player
        val supported = player.supportsScrobbling()
        val configured = if (supported) player.isLastFmConfigured() else false
        _uiState.value = _uiState.value.copy(supported = supported, lastFmConfigured = configured)

        if (supported) {
            viewModelScope.launch {
                combine(
                    dataStoreManager.scrobbleLastFmEnabled,
                    dataStoreManager.scrobbleLastFmUsername,
                    dataStoreManager.scrobbleListenBrainzEnabled,
                    dataStoreManager.scrobbleListenBrainzToken,
                ) { lastFmEnabled, lastFmUsername, lbEnabled, lbToken ->
                    ScrobbleSettingsSnapshot(lastFmEnabled, lastFmUsername, lbEnabled, lbToken)
                }.collect { snapshot ->
                    _uiState.value =
                        _uiState.value.copy(
                            lastFmEnabled = snapshot.lastFmEnabled == DataStoreManager.Values.TRUE,
                            lastFmLoggedInAs = snapshot.lastFmUsername.takeIf { it.isNotBlank() },
                            listenBrainzEnabled = snapshot.listenBrainzEnabled == DataStoreManager.Values.TRUE,
                            listenBrainzToken = snapshot.listenBrainzToken,
                        )
                }
            }
        }
    }

    fun loginToLastFm(
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(lastFmLoggingIn = true, lastFmError = null)
            mediaPlayerHandler.player
                .loginToLastFm(username, password)
                .onSuccess { sessionKey ->
                    dataStoreManager.setScrobbleLastFmSession(username, sessionKey)
                    dataStoreManager.setScrobbleLastFmEnabled(true)
                    _uiState.value = _uiState.value.copy(lastFmLoggingIn = false, lastFmError = null)
                }.onFailure { error ->
                    _uiState.value = _uiState.value.copy(lastFmLoggingIn = false, lastFmError = error.message)
                }
        }
    }

    fun logoutFromLastFm() {
        viewModelScope.launch { dataStoreManager.clearScrobbleLastFmSession() }
    }

    fun setLastFmEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setScrobbleLastFmEnabled(enabled) }
    }

    fun setListenBrainzEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStoreManager.setScrobbleListenBrainzEnabled(enabled) }
    }

    fun setListenBrainzToken(token: String) {
        _uiState.value = _uiState.value.copy(listenBrainzToken = token)
        viewModelScope.launch { dataStoreManager.setScrobbleListenBrainzToken(token) }
    }
}
