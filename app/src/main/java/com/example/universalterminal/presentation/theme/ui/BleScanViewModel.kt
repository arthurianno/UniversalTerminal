package com.example.universalterminal.presentation.theme.ui

import android.Manifest
import android.util.Log
import androidx.annotation.RequiresPermission
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.Job
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
    private val saveDevicePasswordUseCase: SaveDevicePasswordUseCase,
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

    private val _scanMode = MutableStateFlow(ScanMode.BALANCED)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()
    private var scanJob: Job? = null

    fun onNavigatedToConnected() {
        _navigateToConnected.value = false
    }

    fun resetPinError() {
        _pinError.value = null
    }

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        if (_isScanning.value) {
            stopScan()
            startScan()
        }
    }

    fun resetConnectionState() {
        _connectionInProgress.value = false
        _connectingDevice.value = null
        _navigateToConnected.value = false
        _currentPin.value = ""
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
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            _isScanning.value = true
            val devicesFlow = bleScanUseCase.invoke(_scanMode.value)
            devicesFlow.collect { devices ->
                _devices.value = devices
            }
        }
    }

    fun stopScan() {
        viewModelScope.launch {
            scanJob?.cancel()
            scanJob = null
            bleStopScanUseCase.invoke()
            _isScanning.value = false
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BleDevice, pin: String) {
        if (_connectionInProgress.value) return
        stopScan()

        // Сбрасываем старое состояние
        resetConnectionState()

        _connectingDevice.value = device
        _connectionInProgress.value = true
        resetPinError()

        // Записываем время начала подключения
        val startTime = System.currentTimeMillis()

        viewModelScope.launch {
            Log.e("DebugCheck", "Перед сохранением последнего устройства: $device")
            try {
                // Сохраняем устройство как последнее подключенное
                Log.e("Check", "Entering connectToDevice for device: $device")
                saveLastConnectedDevice.invoke(device)
                Log.e("Check", "Check last dev $device")

                val isConnected = bleConnectUseCase.invoke(device)
                if (isConnected) {
                    Log.i("ViewModel", "Connected to ${device.address}")
                    try {
                        val pinResponse = bleSendCommandUseCase.invoke("pin.$pin")
                        val decodedResponse = String(pinResponse, Charsets.UTF_8)
                        Log.i("ViewModel", "PIN Response: $decodedResponse")
                        when {
                            decodedResponse.contains("pin.error") -> {
                                _pinError.value = "Invalid PIN code. Please try again."
                                _connectionInProgress.value = false
                                _connectingDevice.value = null
                            }
                            decodedResponse.contains("pin.ok") -> {
                                val endTime = System.currentTimeMillis()
                                val connectionTime = endTime - startTime
                                Log.i(
                                    "ConnectionTime",
                                    "Device ${device.address} connected in $connectionTime ms"
                                )

                                updatePin(pin)

                                val serialResponse = String(
                                    bleSendCommandUseCase.invoke("serial"),
                                    Charsets.UTF_8
                                )
                                val versionResponse = String(
                                    bleSendCommandUseCase.invoke("version"),
                                    Charsets.UTF_8
                                )

                                Log.d("ViewModel", "Serial Response: $serialResponse")
                                Log.d("ViewModel", "Version Response: $versionResponse")

                                val deviceInfo = DeviceInfo(
                                    serialNumber = serialResponse.removePrefix("ser.").trim(),
                                    version = versionResponse.substringAfter("sw:")
                                        .substringBefore(" ").trim(),
                                    model = determineDeviceType(versionResponse).toString(),
                                    firmwareVersion = versionResponse,
                                )

                                Log.d("ViewModel", "Created DeviceInfo: $deviceInfo")

                                val updatedDevice = device.copy(
                                    name = "SatelliteOnline${serialResponse.removePrefix("ser.").takeLast(4)}",
                                    deviceInfo = deviceInfo
                                )

                                saveDeviceInformationUseCase.invoke(updatedDevice)
                                Log.d("ViewModel", "Saved updated device: $updatedDevice")

                                withContext(Dispatchers.Main) {
                                    _navigateToConnected.value = true
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("ViewModel", "Error sending commands", e)
                    }
                } else {
                    Log.i("ViewModel", "Not connected")
                }
            } catch (e: Exception) {
                Log.i("DebugCheck", e.message.toString())
                // Логируем время при ошибке подключения
                val endTime = System.currentTimeMillis()
                val connectionTime = endTime - startTime
                Log.e(
                    "ConnectionTime",
                    "Device ${device.address} failed to connect in $connectionTime ms, error: ${e.message}"
                )
            } finally {
                if (_pinError.value == null) {
                    _connectionInProgress.value = false
                    _connectingDevice.value = null
                }
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
                    in 4000000..4001009 -> DeviceType.NORDIC
                    in 4005000..4005009 -> DeviceType.WCH
                    else -> DeviceType.UNKNOWN
                }
            }
        } catch (e: Exception) {
            Log.e("BleDeviceManager", "Error parsing version: $swVersion", e)
        }
        return DeviceType.UNKNOWN
    }
}

enum class ScanMode {
    LOW_POWER,
    BALANCED,
    AGGRESSIVE
}
