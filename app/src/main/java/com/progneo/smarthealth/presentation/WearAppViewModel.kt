package com.progneo.smarthealth.presentation

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.progneo.smarthealth.data.HealthServicesRepository
import com.progneo.smarthealth.data.PassiveDataRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class WearAppViewModel(
    private val healthServicesRepository: HealthServicesRepository,
    private val passiveDataRepository: PassiveDataRepository
) : ViewModel() {

    val hrValue = passiveDataRepository.latestHeartRate
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), Double.NaN)

    val hrEnabled = passiveDataRepository.passiveDataEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

    val uiState: MutableState<MainUiState> = mutableStateOf(MainUiState.Startup)

    init {
        viewModelScope.launch {
            val supported = healthServicesRepository.hasHeartRateCapability()
            uiState.value = if (supported) {
                MainUiState.Supported
            } else {
                MainUiState.NotSupported
            }
        }

        viewModelScope.launch {
            passiveDataRepository.passiveDataEnabled.distinctUntilChanged().collect { enabled ->
                if (enabled) {
                    healthServicesRepository.registerForHeartRateData()
                } else {
                    healthServicesRepository.unregisterForHeartRateData()
                }
            }
        }
    }

    fun toggleEnabled() {
        viewModelScope.launch {
            val newEnabledStatus = !hrEnabled.value
            passiveDataRepository.setPassiveDataEnabled(newEnabledStatus)
            if (!newEnabledStatus) {
                passiveDataRepository.storeLatestHeartRate(Double.NaN)
            }
        }
    }
}

class PassiveDataViewModelFactory(
    private val healthServicesRepository: HealthServicesRepository,
    private val passiveDataRepository: PassiveDataRepository
) : ViewModelProvider.Factory {

    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(WearAppViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return WearAppViewModel(
                healthServicesRepository = healthServicesRepository,
                passiveDataRepository = passiveDataRepository
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

sealed class MainUiState {
    data object Startup : MainUiState()
    data object NotSupported : MainUiState()
    data object Supported : MainUiState()
}
