package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.common.STATUS_DONE
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.manager.DataStoreManager.Values.TRUE
import com.wavora.domain.model.model.intent.GenericIntent
import com.wavora.domain.model.model.update.UpdateData
import com.wavora.domain.repository.UpdateRepository
import com.wavora.domain.utils.Resource
import com.wavora.app.utils.VersionManager
import com.wavora.app.viewModel.base.BaseViewModel
import com.wavora.logger.LogLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.error

class AppViewModel(
    private val dataStoreManager: DataStoreManager,
    private val updateRepository: UpdateRepository,
) : BaseViewModel() {

    // ── Simple key-value persistence (e.g. one-shot promo flags) ───────────
    suspend fun getString(key: String): String? = dataStoreManager.getString(key).first()

    suspend fun putString(key: String, value: String) = dataStoreManager.putString(key, value)

    // ── Flags ─────────────────────────────────────────────────────────────
    var isFirstLiked: Boolean = false
    var isFirstMiniplayer: Boolean = false
    var isFirstSuggestions: Boolean = false
    var showedUpdateDialog: Boolean = false

    // ── Update ────────────────────────────────────────────────────────────
    private val _isCheckingUpdate = MutableStateFlow(false)
    val isCheckingUpdate: StateFlow<Boolean> = _isCheckingUpdate

    private val _updateResponse = MutableStateFlow<UpdateData?>(null)
    val updateResponse: StateFlow<UpdateData?> = _updateResponse

    // ── Intent ────────────────────────────────────────────────────────────
    private val _intent = MutableStateFlow<GenericIntent?>(null)
    val intent: StateFlow<GenericIntent?> = _intent

    // ── Notification permission ───────────────────────────────────────────
    private val _showNotificationPermissionDialog = MutableStateFlow(false)
    val showNotificationPermissionDialog: StateFlow<Boolean> = _showNotificationPermissionDialog

    // ── Reload destination ────────────────────────────────────────────────
    private val _reloadDestination = MutableStateFlow<KClass<*>?>(null)
    val reloadDestination: StateFlow<KClass<*>?> = _reloadDestination.asStateFlow()

    // ── Recreate activity ─────────────────────────────────────────────────
    private val _recreateActivity = MutableStateFlow(false)
    val recreateActivity: StateFlow<Boolean> = _recreateActivity

    // ── Open app time ─────────────────────────────────────────────────────
    val openAppTime: StateFlow<Int> = dataStoreManager.openAppTime.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000L), 0,
    )

    // ── Location (country/region code, e.g. YT Music content region) ───────
    // Backed by the same DataStore key MainActivity seeds during first-time
    // locale migration ("location"). Primed once at app startup so it's
    // already warm by the time anything downstream needs it.
    private val _location = MutableStateFlow("")
    val location: StateFlow<String> = _location

    fun getLocation() {
        viewModelScope.launch {
            dataStoreManager.location.collectLatest { _location.value = it }
        }
    }

    init {
        viewModelScope.launch {
            if (dataStoreManager.appVersion.first() != VersionManager.getVersionName()) {
                dataStoreManager.resetOpenAppTime()
                dataStoreManager.setAppVersion(VersionManager.getVersionName())
            }
            dataStoreManager.openApp()
        }
        // Was `runBlocking` here, executed synchronously during ViewModel construction (i.e. on
        // the thread that first resolves this Koin singleton, typically the main thread at cold
        // start). Moved into its own coroutine so DataStore's disk I/O never blocks the caller;
        // these flags aren't read anywhere until after construction completes, so resolving them
        // a frame later has no behavioral effect.
        viewModelScope.launch {
            dataStoreManager.getString("miniplayer_guide").first().let { isFirstMiniplayer = it != STATUS_DONE }
            dataStoreManager.getString("suggest_guide").first().let { isFirstSuggestions = it != STATUS_DONE }
            dataStoreManager.getString("liked_guide").first().let { isFirstLiked = it != STATUS_DONE }
        }
    }

    // ── Intent ────────────────────────────────────────────────────────────
    fun setIntent(intent: GenericIntent?) { _intent.value = intent }

    // ── Notification permission ───────────────────────────────────────────
    fun showNotificationPermissionDialog() { _showNotificationPermissionDialog.value = true }
    fun dismissNotificationPermissionDialog(doNotShowAgain: Boolean) {
        _showNotificationPermissionDialog.value = false
        if (doNotShowAgain) {
            viewModelScope.launch {
                dataStoreManager.putString("notification_permission_do_not_ask", "true")
            }
        }
    }

    // ── Reload / recreate ─────────────────────────────────────────────────
    fun reloadDestination(destination: KClass<*>) { _reloadDestination.value = destination }
    fun reloadDestinationDone() { _reloadDestination.value = null }
    fun activityRecreate() { _recreateActivity.value = true }
    fun activityRecreateDone() { _recreateActivity.value = false }

    // ── Review / lyrics share ─────────────────────────────────────────────
    fun onDoneReview(isDismissOnly: Boolean = true) {
        viewModelScope.launch {
            if (!isDismissOnly) dataStoreManager.doneOpenAppTime() else dataStoreManager.openApp()
        }
    }

    fun onDoneRequestingShareLyrics(contributor: Pair<String, String>? = null) {
        viewModelScope.launch {
            dataStoreManager.setHelpBuildLyricsDatabase(true)
            dataStoreManager.setContributorLyricsDatabase(contributor)
        }
    }

    // ── Update ────────────────────────────────────────────────────────────
    // Used for the *automatic* startup check only. Reads the autoCheckForUpdates preference
    // inside the coroutine instead of via a blocking runBlocking() call on the caller's thread
    // (previously executed synchronously in MainActivity.onCreate()/runDesktopApp() before the
    // first frame). checkForUpdate() itself stays unconditional below, since it's also called
    // directly by the manual "check for update" button in Settings, which must always run
    // regardless of this preference.
    fun checkForUpdateIfEnabled() {
        viewModelScope.launch {
            if (dataStoreManager.autoCheckForUpdates.first() != TRUE) return@launch
            checkForUpdate()
        }
    }

    // [isManual] distinguishes the automatic startup check (silent on
    // failure, as before - no toast on every launch just because there's
    // no internet) from the explicit "buscar actualizaciones" button in
    // Settings, which should tell the user when the check itself failed
    // (GitHub down, rate-limited, no connection) instead of leaving them
    // guessing why nothing happened.
    fun checkForUpdate(isManual: Boolean = false) {
        viewModelScope.launch {
            _isCheckingUpdate.value = true
            val updateChannel = dataStoreManager.updateChannel.first()
            dataStoreManager.putString("CheckForUpdateAt", System.currentTimeMillis().toString())
            val flow = when (updateChannel) {
                DataStoreManager.GITHUB -> updateRepository.checkForGithubReleaseUpdate()
                else -> updateRepository.checkForFdroidUpdate()
            }
            flow.collectLatest { response ->
                val data = response.data
                when (response) {
                    is Resource.Success if (data != null) -> { _updateResponse.value = data; showedUpdateDialog = true }
                    else -> {
                        log("Check for update error: ${response.message}", LogLevel.WARN)
                        if (isManual) {
                            makeToast(getString(Res.string.error) + ": ${response.message}")
                        }
                    }
                }
                _isCheckingUpdate.value = false
            }
        }
    }
}
