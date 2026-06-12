package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.domain.manager.DataStoreManager
import com.wavora.app.viewModel.base.BaseViewModel
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.isSuccess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LogInViewModel(
    private val dataStoreManager: DataStoreManager,
) : BaseViewModel() {
    private val _spotifyStatus: MutableStateFlow<Boolean> = MutableStateFlow(false)
    val spotifyStatus: StateFlow<Boolean> get() = _spotifyStatus

    private val _fullSpotifyCookies: MutableStateFlow<List<Pair<String, String?>>> = MutableStateFlow(emptyList())
    val fullSpotifyCookies: StateFlow<List<Pair<String, String?>>> get() = _fullSpotifyCookies.asStateFlow()

    private val _fullYouTubeCookies: MutableStateFlow<List<Pair<String, String?>>> = MutableStateFlow(emptyList())
    val fullYouTubeCookies: StateFlow<List<Pair<String, String?>>> get() = _fullYouTubeCookies.asStateFlow()

    fun saveSpotifySpdc(cookie: String) {
        viewModelScope.launch {
            cookie
                .split("; ")
                .filter { it.isNotEmpty() }
                .associate {
                    val (key, value) = it.split("=")
                    key to value
                }.let {
                    dataStoreManager.setSpdc(it["sp_dc"] ?: "")
                    _spotifyStatus.value = true
                }
        }
    }

    fun setVisitorData(visitorData: String) {
        viewModelScope.launch {
            dataStoreManager.setVisitorData(visitorData)
        }
    }

    fun setDataSyncId(dataSyncId: String) {
        viewModelScope.launch {
            dataStoreManager.setDataSyncId(dataSyncId)
        }
    }

    fun setFullSpotifyCookies(cookies: List<Pair<String, String?>>) {
        viewModelScope.launch {
            _fullSpotifyCookies.value = cookies
        }
    }

    fun setFullYouTubeCookies(cookies: List<Pair<String, String?>>) {
        viewModelScope.launch {
            _fullYouTubeCookies.value = cookies
        }
    }

    fun saveDiscordToken(token: String) {
        viewModelScope.launch {
            dataStoreManager.setDiscordToken(token)
        }
    }

    sealed class TokenValidationState {
        object Idle : TokenValidationState()
        object Loading : TokenValidationState()
        data class Success(val userInfo: UserInfo) : TokenValidationState()
        object Error : TokenValidationState()
    }

    private val _tokenValidation = MutableStateFlow<TokenValidationState>(TokenValidationState.Idle)
    val tokenValidation: StateFlow<TokenValidationState> = _tokenValidation.asStateFlow()

    fun validateAndSaveDiscordToken(token: String) {
        viewModelScope.launch {
            _tokenValidation.value = TokenValidationState.Loading
            KizzyRPC.getUserInfo(token.trim())
                .onSuccess { userInfo ->
                    dataStoreManager.setDiscordToken(token.trim())
                    _tokenValidation.value = TokenValidationState.Success(userInfo)
                }
                .onFailure {
                    _tokenValidation.value = TokenValidationState.Error
                }
        }
    }

    fun resetTokenValidation() {
        _tokenValidation.value = TokenValidationState.Idle
    }

    // ── Spotify sp_dc validation ──────────────────────────────────────────
    sealed class SpdcValidationState {
        object Idle : SpdcValidationState()
        object Loading : SpdcValidationState()
        object Success : SpdcValidationState()
        object Error : SpdcValidationState()
    }

    private val _spdcValidation = MutableStateFlow<SpdcValidationState>(SpdcValidationState.Idle)
    val spdcValidation: StateFlow<SpdcValidationState> = _spdcValidation.asStateFlow()

    fun validateAndSaveSpotifySpdc(spdc: String) {
        viewModelScope.launch {
            _spdcValidation.value = SpdcValidationState.Loading
            runCatching {
                val client = HttpClient()
                val response = client.get("https://open.spotify.com/get_access_token") {
                    header("Cookie", "sp_dc=${spdc.trim()}")
                }
                client.close()
                response.status.isSuccess()
            }.onSuccess { valid ->
                if (valid) {
                    val spdcCookie = "sp_dc=${spdc.trim()}"
                    dataStoreManager.setSpdc(spdc.trim())
                    _spotifyStatus.value = true
                    _spdcValidation.value = SpdcValidationState.Success
                } else {
                    _spdcValidation.value = SpdcValidationState.Error
                }
            }.onFailure {
                _spdcValidation.value = SpdcValidationState.Error
            }
        }
    }

    fun resetSpdcValidation() {
        _spdcValidation.value = SpdcValidationState.Idle
    }
}