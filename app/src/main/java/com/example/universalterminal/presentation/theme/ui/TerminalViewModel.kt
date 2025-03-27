package com.example.universalterminal.presentation.theme.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.BleRepository
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ViewModel
@HiltViewModel
class TerminalViewModel @Inject constructor(
    private val bleRepository: BleRepository,
    private val deviceWorkingRepository: DeviceWorkingRepository
) : ViewModel() {

    private val _terminalState = MutableStateFlow(TerminalState())
    val terminalState: StateFlow<TerminalState> = _terminalState.asStateFlow()

    private val _deviceInfo = MutableStateFlow<BleDevice?>(null)
    val deviceInfo: StateFlow<BleDevice?> = _deviceInfo


    init {
        viewModelScope.launch {
            loadLastConnectedDevice()
        }
    }

    private fun loadLastConnectedDevice() {
        viewModelScope.launch {
            try {
                val device = deviceWorkingRepository.getLastConnectedDevice().first()
                device?.let {
                    val deviceWithInfo = deviceWorkingRepository.getDeviceInformation(it).first()
                    _deviceInfo.value = deviceWithInfo
                    Log.d("RawViewModel", "Loaded device info: $deviceWithInfo")
                } ?: run {
                    _terminalState.update { it.copy(responses = it.responses + "No last connected device found") }
                }
            } catch (e: Exception) {
                Log.e("RawViewModel", "Error loading device info", e)
                _terminalState.update { it.copy(responses = it.responses + "Failed to load last connected device: ${e.message}") }
            }
        }
    }

    suspend fun sendTerminalCommand(device: BleDevice, command: String) {
        val isConnected = bleRepository.isConnected().first()
        if (!isConnected) {
            _terminalState.update { it.copy(responses = it.responses + "Connecting to device...") }
            val connectSuccess = bleRepository.connectToDevice(device).first()
            if (!connectSuccess) {
                _terminalState.update { it.copy(responses = it.responses + "Connection failed") }
                return
            }

            val savedPin = deviceWorkingRepository.getDevicePassword(device.device.address).first()
            if (savedPin != null) {
                _terminalState.update { it.copy(responses = it.responses + "Sending PIN...") }
                val pinResponse = bleRepository.sendCommand("pin.$savedPin").first()
                val responseStr = String(pinResponse, Charsets.UTF_8)
                if (responseStr == "pin.error") {
                    _terminalState.update {
                        it.copy(responses = it.responses + "PIN error - command aborted")
                    }
                    bleRepository.disconnectFromDevice()
                    return
                } else if (responseStr == "pin.ok") {
                    _terminalState.update { it.copy(responses = it.responses + "PIN accepted") }
                }
            }
        }

        _terminalState.update { it.copy(responses = it.responses + "> $command") }
        val response = bleRepository.sendCommand(command).first() // Берем только первый ответ
        val responseStr = String(response, Charsets.UTF_8)
        _terminalState.update { it.copy(responses = it.responses + responseStr) }
    }
}

data class TerminalState(
    val responses: List<String> = emptyList()
)