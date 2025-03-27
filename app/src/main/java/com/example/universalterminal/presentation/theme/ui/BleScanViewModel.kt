package com.example.universalterminal.presentation.theme.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.universalterminal.data.BLE.BleDeviceManager.DeviceType
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.DeviceInfo
import com.example.universalterminal.domain.useCase.ConnectToDeviceUseCase
import com.example.universalterminal.domain.useCase.SaveDeviceInformationUseCase
import com.example.universalterminal.domain.useCase.SaveDevicePasswordUseCase
import com.example.universalterminal.domain.useCase.SaveLastConnectedDevice
import com.example.universalterminal.domain.useCase.ScanDevicesUseCase
import com.example.universalterminal.domain.useCase.SendCommandUseCase
import com.example.universalterminal.domain.useCase.StopScanDeviceUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class BleScanViewModel @Inject constructor(
    private val bleScanUseCase: ScanDevicesUseCase,
    private val bleStopScanUseCase: StopScanDeviceUseCase,
    private val bleConnectUseCase: ConnectToDeviceUseCase,
    private val bleSendCommandUseCase: SendCommandUseCase,
    private val saveDeviceInformationUseCase: SaveDeviceInformationUseCase,
    private val saveLastConnectedDevice: SaveLastConnectedDevice,
    private val saveDevicePasswordUseCase: SaveDevicePasswordUseCase

) : ViewModel() {
    private val _devices = MutableStateFlow<Set<BleDevice>>(emptySet())
    val devices: StateFlow<Set<BleDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _connectingDevice = MutableStateFlow<BleDevice?>(null)
    val connectingDevice: StateFlow<BleDevice?> = _connectingDevice.asStateFlow()

    private val _connectionInProgress = MutableStateFlow(false)
    val connectionInProgress: StateFlow<Boolean> = _connectionInProgress.asStateFlow()

    private val _navigateToConnected = MutableStateFlow(false)
    val navigateToConnected: StateFlow<Boolean> = _navigateToConnected.asStateFlow()

    private val _currentPin = MutableStateFlow("")
    val currentPin: StateFlow<String> = _currentPin.asStateFlow()

    fun onNavigatedToConnected() {
        _navigateToConnected.value = false
    }



    fun updatePin(pin: String) {
        viewModelScope.launch {
            _currentPin.value = pin
            _connectingDevice.value?.let { device ->
                saveDevicePasswordUseCase.invoke(device.address, pin)
                Log.d("BleScanViewModel", "Saving PIN: $pin for device: ${device.address}")
            } ?: Log.e("BleScanViewModel", "No device selected to save PIN")
        }
    }


    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            bleScanUseCase.invoke().collect { devices ->
                _devices.value = devices
            }
            _isScanning.value = false
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            bleStopScanUseCase.invoke().collect { result ->
                _isScanning.value = result
            }
        }
    }


    fun connectToDevice(device: BleDevice, pin: String) {
        stopScan()
        _connectingDevice.value = device
        _connectionInProgress.value = true

        viewModelScope.launch {
            try {
                bleConnectUseCase.invoke(device).collect { isConnected ->
                    if (isConnected) {
                        Log.i("ViewModel", "Connected")
                        try {
                            coroutineScope {
                                bleSendCommandUseCase.invoke("pin.$pin").collect { response ->
                                    val decodedResponse = String(response, Charsets.UTF_8)
                                    Log.i("ViewModel", "PIN Response: $decodedResponse")
                                    when {
                                        decodedResponse.contains("pin.error") -> {
                                            Log.i("ViewModel", "PIN ERROR")
                                        }
                                        decodedResponse.contains("pin.ok") -> {
                                            val deviceInfo = BleDevice(
                                                name = device.name,
                                                address = device.address,
                                                rssi = device.rssi,
                                                device = device.device,
                                                deviceInfo = DeviceInfo()
                                            )
                                            Log.i("ViewModel", "PIN OK")
                                            saveDevicePasswordUseCase.invoke(device.address, pin) // Сохраняем PIN после успешной проверки
                                            bleSendCommandUseCase.invoke("infoCommand").collect { response ->
                                                val decodedResponse = String(response, Charsets.UTF_8)
                                                Log.e("ViewModel", "Response: $decodedResponse")

                                                if (decodedResponse.startsWith("hw:")) {
                                                    val deviceType = determineDeviceType(decodedResponse)
                                                    Log.e("ViewModel", "Device type: $deviceType")
                                                    deviceInfo.deviceInfo?.model = deviceType.name
                                                    deviceInfo.deviceInfo?.version = decodedResponse
                                                }

                                                when {
                                                    decodedResponse.contains("ser.") -> {
                                                        deviceInfo.deviceInfo?.serialNumber = decodedResponse
                                                        Log.e("ViewModel", deviceInfo.toString())
                                                        saveDeviceInformationUseCase(deviceInfo)
                                                        saveLastConnectedDevice.invoke(device)
                                                        withContext(Dispatchers.Main) {
                                                            _navigateToConnected.value = true
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("ViewModel", "Error sending commands", e)
                        }
                    } else {
                        Log.i("ViewModel", "Not connected")
                    }
                }
            } catch (e: Exception) {
                Log.e("ViewModel", "Connection error", e)
            } finally {
                _connectionInProgress.value = false
                _connectingDevice.value = null
            }
        }
    }

    private fun determineDeviceType(swVersion: String): DeviceType {
        try {
            // Extract just the version number from the response
            val versionString = swVersion.substringAfter("sw:").trim()
            val versionParts = versionString.split(".").map { it.toInt() }

            if (versionParts.size >= 3) {
                val (major, minor, patch) = versionParts
                val version = major * 1000000 + minor * 1000 + patch

                Log.d("BleDeviceManager", "Parsed version: $major.$minor.$patch ($version)")

                return when (version) {
                    in 4000000..4001005 -> DeviceType.NORDIC
                    in 4001006..4005005 -> DeviceType.WCH
                    else -> DeviceType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.e("BleDeviceManager", "Error parsing version: $swVersion", e)
        }
        return DeviceType.UNKNOWN
    }
}
