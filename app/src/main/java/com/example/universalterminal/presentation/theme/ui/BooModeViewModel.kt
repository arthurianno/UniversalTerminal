package com.example.universalterminal.presentation.theme.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.universalterminal.di.toDfuAddress
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.ScanMode
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import com.example.universalterminal.domain.useCase.CheckDeviceConnectionUseCase
import com.example.universalterminal.domain.useCase.ConnectToDeviceUseCase
import com.example.universalterminal.domain.useCase.GetDevicePasswordUseCase
import com.example.universalterminal.domain.useCase.LoadDfuUseCase
import com.example.universalterminal.domain.useCase.LoadFirmwareUseCase
import com.example.universalterminal.domain.useCase.ProcessZipUseCase
import com.example.universalterminal.domain.useCase.ScanDevicesUseCase
import com.example.universalterminal.domain.useCase.SendCommandUseCase
import com.example.universalterminal.domain.useCase.StopScanDeviceUseCase
import com.example.universalterminal.domain.useCase.WriteConfigurationUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream
import java.util.regex.Pattern

private const val TAG = "BootModeViewModel"
private const val MASTER_PIN = "pin.master"

@HiltViewModel
class BootModeViewModel @Inject constructor(
    private val deviceRepository: DeviceWorkingRepository,
    private val sendCommandUseCase: SendCommandUseCase,
    private val loadFirmwareUseCase: LoadFirmwareUseCase,
    private val processZipUseCase: ProcessZipUseCase,
    private val writeConfigurationUseCase: WriteConfigurationUseCase,
    private val isDeviceConnectedUseCase: CheckDeviceConnectionUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val loadDfuUseCase: LoadDfuUseCase,
    private val scanDevicesUseCase: ScanDevicesUseCase,
    private val stopScanDevicesUseCase: StopScanDeviceUseCase,
    private val getDevicePasswordUseCase: GetDevicePasswordUseCase
) : ViewModel() {

    private val _firmwareUpdateState = MutableStateFlow<FirmwareUpdateState>(FirmwareUpdateState.Idle)
    val firmwareUpdateState: StateFlow<FirmwareUpdateState> = _firmwareUpdateState

    private val _isUpdateButtonEnabled = MutableStateFlow(true)
    val isUpdateButtonEnabled: StateFlow<Boolean> = _isUpdateButtonEnabled

    private val _deviceInfo = MutableStateFlow<BleDevice?>(null)
    val deviceInfo: StateFlow<BleDevice?> = _deviceInfo

    private val _selectedFirmware = MutableStateFlow<FirmwareFile?>(null)
    val selectedFirmware: StateFlow<FirmwareFile?> = _selectedFirmware

    private val _fileValidationError = MutableStateFlow<String?>(null)
    val fileValidationError: StateFlow<String?> = _fileValidationError

    init {
        loadLastConnectedDevice()
    }

    fun resetUpdateState() {
        _firmwareUpdateState.value = FirmwareUpdateState.Idle
    }

    private fun loadLastConnectedDevice() {
        viewModelScope.launch {
            try {
                val device = deviceRepository.getLastConnectedDevice().first()
                device?.let {
                    val deviceWithInfo = deviceRepository.getDeviceInformation(it).first()
                    _deviceInfo.value = deviceWithInfo
                    Log.d(TAG, "Loaded device info: $deviceWithInfo")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading device info", e)
            }
        }
    }

    fun processFirmwareFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                // Get file metadata
                val contentResolver = context.contentResolver
                val fileName = getFileNameFromUri(context, uri) ?: "unknown.zip"

                // Create a file copy
                val tempFile = File(context.cacheDir, fileName)
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                }

                // Determine firmware type and version
                val firmwareType: FirmwareType
                val version: String

                when {
                    fileName.startsWith("DfuAppOnline_") -> {
                        firmwareType = FirmwareType.NORDIC
                        version = extractVersionFromFileName(fileName)
                    }
                    fileName.startsWith("AppOnline_") -> {
                        firmwareType = FirmwareType.WCH
                        version = extractVersionFromFileName(fileName)
                    }
                    else -> {
                        _fileValidationError.value = "Unrecognized firmware file format: $fileName"
                        Log.e(TAG, "Unrecognized firmware file format: $fileName")
                        return@launch
                    }
                }

                // Validate firmware based on device model
                if (!validateFirmware(firmwareType, version)) {
                    return@launch
                }

                val firmwareFile = FirmwareFile(
                    type = firmwareType,
                    version = version,
                    fileName = fileName,
                    filePath = tempFile.absolutePath
                )

                _selectedFirmware.value = firmwareFile
                _fileValidationError.value = null
                Log.d(TAG, "Processed firmware file: $firmwareFile")
            } catch (e: Exception) {
                _fileValidationError.value = "Error processing firmware file: ${e.message}"
                Log.e(TAG, "Error processing firmware file", e)
            }
        }
    }

    private fun validateFirmware(firmwareType: FirmwareType, version: String): Boolean {
        val deviceModel = _deviceInfo.value?.deviceInfo?.model ?: return false

        // Extract version numbers for comparison
        val versionParts = version.split(".")
        if (versionParts.size < 3) {
            _fileValidationError.value = "Invalid version format: $version"
            return false
        }

        try {
            val major = versionParts[0].toInt()
            val minor = versionParts[1].toInt()
            val patch = versionParts[2].toInt()

            if (deviceModel.contains("WCH", ignoreCase = true)) {
                // For WCH devices, version should be AppOnline_4.5.0 - 4.9.9
                if (firmwareType != FirmwareType.WCH) {
                    _fileValidationError.value = "WCH device requires WCH firmware"
                    return false
                }

                if (major != 4 || minor < 5 || minor > 9) {
                    _fileValidationError.value = "WCH device requires firmware version 4.5.0 - 4.9.9"
                    return false
                }
            } else if (deviceModel.contains("NORDIC", ignoreCase = true)) {
                // For NORDIC devices, version should be AppOnline_4.0.0 - 4.1.5
                if (firmwareType != FirmwareType.NORDIC) {
                    _fileValidationError.value = "NORDIC device requires NORDIC firmware"
                    return false
                }

                if (major != 4 || (minor > 1) || (minor == 1 && patch > 9)) {
                    _fileValidationError.value = "NORDIC device requires firmware version 4.0.0 - 4.1.9"
                    return false
                }
            } else {
                _fileValidationError.value = "Unknown device model: $deviceModel"
                return false
            }

            return true
        } catch (e: NumberFormatException) {
            _fileValidationError.value = "Invalid version format: $version"
            return false
        }
    }

    fun clearSelectedFirmware() {
        _selectedFirmware.value = null
        _fileValidationError.value = null
    }

    fun startFirmwareUpdate() {
        viewModelScope.launch {
            val firmware = _selectedFirmware.value ?: return@launch
            _isUpdateButtonEnabled.value = false
            _firmwareUpdateState.value = FirmwareUpdateState.Updating

            try {
                updateFirmware(firmware)
                if (_firmwareUpdateState.value is FirmwareUpdateState.Updating) {
                    _firmwareUpdateState.value = FirmwareUpdateState.Success
                }
            } catch (e: Exception) {
                _firmwareUpdateState.value = FirmwareUpdateState.Error(e.message ?: "Unknown error")
            } finally {
                _isUpdateButtonEnabled.value = true
            }
        }
    }
    private suspend fun updateFirmware(firmware: FirmwareFile) {
        if (!isDeviceConnectedUseCase.invoke()) {
            Log.e("ViewModel", "Device is not connected")
            val currentDevice = _deviceInfo.value ?: run {
                _firmwareUpdateState.value = FirmwareUpdateState.Error("No selected device")
                _isUpdateButtonEnabled.value = true
                return
            }

            val connected = connectToDeviceUseCase.invoke(currentDevice)
            if (!connected) {
                _firmwareUpdateState.value = FirmwareUpdateState.Error("Connection failed")
                _isUpdateButtonEnabled.value = true
                return
            }

            val pinCommand = when (firmware.type) {
                FirmwareType.NORDIC -> {
                    val savedPin = getDevicePasswordUseCase.invoke(currentDevice.address).first()
                    if (savedPin.isNullOrBlank()) {
                        _firmwareUpdateState.value = FirmwareUpdateState.Error("Device PIN is missing")
                        _isUpdateButtonEnabled.value = true
                        return
                    }
                    "pin.$savedPin"
                }
                FirmwareType.WCH -> MASTER_PIN
            }

            val decodedResponse = String(sendCommandUseCase.invoke(pinCommand), Charsets.UTF_8)
            Log.e("ViewModel", "PIN Response: $decodedResponse")
            if (!decodedResponse.contains("pin.ok")) {
                _isUpdateButtonEnabled.value = true
                return
            }
        }

        performFirmwareUpdateWhenConnected(firmware)
    }

    private suspend fun performFirmwareUpdateWhenConnected(firmware: FirmwareFile) {
        if (_deviceInfo.value?.deviceInfo?.model.equals("WCH")) {
            Log.d(TAG, "Updating firmware: ${firmware.fileName}, type: ${firmware.type}")
            val (binData, datData) = processZipUseCase.invoke(firmware.filePath).getOrThrow()
            Log.d(
                "ViewModel",
                "BinData size: ${binData.size}, first few bytes: ${binData.take(10).map { it.toInt() }}"
            )
            Log.d(
                "ViewModel",
                "DatData size: ${datData.size}, first few bytes: ${datData.take(10).map { it.toInt() }}"
            )
            val decodedResponse = String(sendCommandUseCase.invoke("boot"), Charsets.UTF_8)
            Log.e("ViewModel", "Response: $decodedResponse")
            if (decodedResponse.contains("boot.ok")) {
                delay(1000L)
                loadFirmwareUseCase.invoke(binData, binData.size).collect {
                    Log.e("ViewModel", "Firmware: $it")
                    if (it) {
                        delay(1000L)
                        writeConfigurationUseCase.invoke(datData).collect { configResult ->
                            Log.e("ViewModel", "Configuration: $configResult")
                            if (configResult) {
                                _firmwareUpdateState.value = FirmwareUpdateState.Success
                                _isUpdateButtonEnabled.value = true
                                return@collect
                            } else {
                                _firmwareUpdateState.value = FirmwareUpdateState.Error("Firmware update failed")
                                _isUpdateButtonEnabled.value = true
                                return@collect
                            }
                        }
                    }
                }
            } else {
                _firmwareUpdateState.value = FirmwareUpdateState.BootError("Boot failed")
                _isUpdateButtonEnabled.value = true
            }
        } else {
            Log.d(TAG, "Updating firmware: ${firmware.fileName}, type: ${firmware.type}")
            val decodedResponse = String(sendCommandUseCase.invoke("boot"), Charsets.UTF_8)
            Log.e("ViewModel", "Response: $decodedResponse")
            if (decodedResponse.contains("boot.ok")) {
                val deviceDfuAddress = _deviceInfo.value?.address?.toDfuAddress()
                Log.e("ViewModel", "DFU Address: $deviceDfuAddress")

                val scannerDevice =
                    findDeviceForDFUUpdate(deviceDfuAddress.toString()) ?: return
                Log.e("ViewModel", "Scanner Device: $scannerDevice")

                stopScanDevicesUseCase.invoke()
                loadDfuUseCase.invoke(scannerDevice.address, firmware.filePath).collect {
                    Log.e("ViewModel", "DFU: $it")
                    if (it) {
                        _firmwareUpdateState.value = FirmwareUpdateState.Success
                        _isUpdateButtonEnabled.value = true
                        return@collect
                    } else {
                        _firmwareUpdateState.value = FirmwareUpdateState.Error("Firmware update failed")
                        _isUpdateButtonEnabled.value = true
                        return@collect
                    }
                }
            } else {
                _firmwareUpdateState.value = FirmwareUpdateState.BootError("Boot failed")
                _isUpdateButtonEnabled.value = true
            }
        }
    }

    private suspend fun findDeviceForDFUUpdate(deviceAddressDFU: String): BleDevice? =
        withContext(Dispatchers.IO) {
            try {
                withTimeout(30000) {
                    scanDevicesUseCase.invoke(ScanMode.BALANCED)
                        .collect { devicesSet ->
                            val foundDevice = devicesSet.firstOrNull { device ->
                                device.address == deviceAddressDFU
                            }
                            if (foundDevice != null) {
                                throw FoundDeviceException(foundDevice)
                            }
                        }
                }
                null
            } catch (e: FoundDeviceException) {
                e.device
            } catch (e: TimeoutCancellationException) {
                null
            } catch (e: Exception) {
                null
            }
        }

    // Custom exception to control flow when a device is found
    private class FoundDeviceException(val device: BleDevice) : Exception()



    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        result = it.getString(nameIndex)
                    }
                }
            }
        }
        return result
    }

    private fun extractVersionFromFileName(fileName: String): String {
        val pattern = Pattern.compile(".*_(\\d+\\.\\d+\\.\\d+)\\.zip")
        val matcher = pattern.matcher(fileName)
        return if (matcher.find()) {
            matcher.group(1)
        } else {
            "Unknown"
        }
    }
}

enum class FirmwareType {
    NORDIC,
    WCH
}

data class FirmwareFile(
    val type: FirmwareType,
    val version: String,
    val fileName: String,
    val filePath: String
)

sealed class FirmwareUpdateState {
    object Idle : FirmwareUpdateState()
    object Updating : FirmwareUpdateState()
    object Success : FirmwareUpdateState()
    data class Error(val message: String) : FirmwareUpdateState()
    data class BootError(val message: String) : FirmwareUpdateState() // Новое состояние
}
