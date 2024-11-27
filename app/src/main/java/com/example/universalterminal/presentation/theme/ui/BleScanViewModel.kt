package com.example.universalterminal.presentation.theme.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.useCase.ScanDevicesUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BleScanViewModel @Inject constructor(
    private val bleScanUseCase: ScanDevicesUseCase
) : ViewModel() {
    private val _devices = MutableStateFlow<List<BleDevice>>(emptyList())
    val devices: StateFlow<List<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()



    fun startScan(){
        viewModelScope.launch {
            _isScanning.value = true
            bleScanUseCase.invoke().collect { devices ->
                _devices.value = devices
            }
            _isScanning.value = false
        }
    }

    fun stopScan() {
        // Implement stop scan logic if needed
        _isScanning.value = false
    }
}