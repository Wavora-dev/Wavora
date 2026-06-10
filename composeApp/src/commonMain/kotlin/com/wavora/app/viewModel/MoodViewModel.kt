package com.wavora.app.viewModel

import androidx.lifecycle.viewModelScope
import com.wavora.common.SELECTED_LANGUAGE
import com.wavora.domain.model.model.mood.moodmoments.MoodsMomentObject
import com.wavora.domain.manager.DataStoreManager
import com.wavora.domain.repository.HomeRepository
import com.wavora.domain.utils.Resource
import com.wavora.logger.Logger
import com.wavora.app.viewModel.base.BaseViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

class MoodViewModel(
    dataStoreManager: DataStoreManager,
    private val homeRepository: HomeRepository,
) : BaseViewModel() {
    private val _moodsMomentObject: MutableStateFlow<MoodsMomentObject?> = MutableStateFlow(null)
    var moodsMomentObject: StateFlow<MoodsMomentObject?> = _moodsMomentObject
    var loading = MutableStateFlow<Boolean>(false)

    private var regionCode: String? = null
    private var language: String? = null

    init {
        regionCode = runBlocking { dataStoreManager.location.first() }
        language = runBlocking { dataStoreManager.getString(SELECTED_LANGUAGE).first() }
    }

    fun getMood(params: String) {
        loading.value = true
        viewModelScope.launch {
//            mainRepository.getMood(params, regionCode!!, SUPPORTED_LANGUAGE.serverCodes[SUPPORTED_LANGUAGE.codes.indexOf(language!!)]).collect{ values ->
//                _moodsMomentObject.value = values
//            }
            homeRepository.getMoodData(params).collect { values ->
                Logger.w("MoodViewModel", "getMood: $values")
                when (values) {
                    is Resource.Success -> {
                        _moodsMomentObject.value = values.data
                    }

                    is Resource.Error -> {
                        _moodsMomentObject.value = null
                    }
                }
            }
            withContext(Dispatchers.Main) {
                loading.value = false
            }
        }
    }
}