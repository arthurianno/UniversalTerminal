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
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import java.io.FileOutputStream

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
                val parsedFirmware = FirmwareValidation.parseFileName(fileName).getOrElse { error ->
                    _fileValidationError.value = error.message ?: "Invalid firmware file: $fileName"
                    Log.e(TAG, "Invalid firmware file: $fileName")
                    return@launch
                }
                val firmwareType = parsedFirmware.type
                val version = parsedFirmware.version

                // Validate firmware based on device model
                val validationError = FirmwareValidation.validateForDeviceModel(
                    deviceModel = _deviceInfo.value?.deviceInfo?.model,
                    firmwareType = firmwareType,
                    version = version
                )
                if (validationError != null) {
                    _fileValidationError.value = validationError
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
                val success = updateFirmware(firmware)
                if (success) {
                    _firmwareUpdateState.value = FirmwareUpdateState.Success
                }
            } catch (e: Exception) {
                _firmwareUpdateState.value = FirmwareUpdateState.Error(e.message ?: "Unknown error")
            } finally {
                _isUpdateButtonEnabled.value = true
            }
        }
    }
    private suspend fun updateFirmware(firmware: FirmwareFile): Boolean {
        if (!ensureConnectedAndAuthorized(firmware)) {
            return false
        }

        return when (firmware.type) {
            FirmwareType.WCH -> updateWchFirmware(firmware)
            FirmwareType.NORDIC -> updateNordicFirmware(firmware)
        }
    }

    private suspend fun ensureConnectedAndAuthorized(firmware: FirmwareFile): Boolean {
        if (isDeviceConnectedUseCase.invoke()) {
            return true
        }

        Log.e("ViewModel", "Device is not connected")
        val currentDevice = _deviceInfo.value ?: run {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("No selected device")
            return false
        }

        val connected = connectToDeviceUseCase.invoke(currentDevice)
        if (!connected) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("Connection failed")
            return false
        }

        val pinCommand = when (firmware.type) {
            FirmwareType.NORDIC -> {
                val savedPin = getDevicePasswordUseCase.invoke(currentDevice.address).first()
                if (savedPin.isNullOrBlank()) {
                    _firmwareUpdateState.value = FirmwareUpdateState.Error("Device PIN is missing")
                    return false
                }
                "pin.$savedPin"
            }

            FirmwareType.WCH -> MASTER_PIN
        }

        val decodedResponse = String(sendCommandUseCase.invoke(pinCommand), Charsets.UTF_8)
        Log.e("ViewModel", "PIN Response: $decodedResponse")
        if (!decodedResponse.contains("pin.ok")) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("PIN authorization failed")
            return false
        }

        return true
    }

    private suspend fun updateWchFirmware(firmware: FirmwareFile): Boolean {
        Log.d(TAG, "Updating WCH firmware: ${firmware.fileName}")
        val firmwarePayload = processZipUseCase.invoke(firmware.filePath).getOrElse { error ->
            _firmwareUpdateState.value = FirmwareUpdateState.Error(error.message ?: "Invalid ZIP firmware file")
            return false
        }

        val (binData, datData) = firmwarePayload
        if (!enterBootMode()) {
            return false
        }

        delay(1000L)
        val firmwareLoadResults = loadFirmwareUseCase.invoke(binData, binData.size).toList()
        val firmwareLoaded = firmwareLoadResults.isNotEmpty() && firmwareLoadResults.all { it }
        if (!firmwareLoaded) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("Firmware transfer failed")
            return false
        }

        delay(1000L)
        val configWriteResults = writeConfigurationUseCase.invoke(datData).toList()
        val configWritten = configWriteResults.isNotEmpty() && configWriteResults.all { it }
        if (!configWritten) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("Configuration update failed")
            return false
        }

        return true
    }

    private suspend fun updateNordicFirmware(firmware: FirmwareFile): Boolean {
        Log.d(TAG, "Updating NORDIC firmware: ${firmware.fileName}")
        if (!enterBootMode()) {
            return false
        }

        val deviceAddress = _deviceInfo.value?.address
        if (deviceAddress.isNullOrBlank()) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("Device address is unavailable")
            return false
        }

        val scannerDevice = findDeviceForDFUUpdate(deviceAddress.toDfuAddress())
        if (scannerDevice == null) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("DFU device not found")
            return false
        }

        val dfuResults = loadDfuUseCase.invoke(scannerDevice.address, firmware.filePath).toList()
        val dfuSuccess = dfuResults.isNotEmpty() && dfuResults.all { it }
        if (!dfuSuccess) {
            _firmwareUpdateState.value = FirmwareUpdateState.Error("Firmware update failed")
            return false
        }

        return true
    }

    private suspend fun enterBootMode(): Boolean {
        val decodedResponse = String(sendCommandUseCase.invoke("boot"), Charsets.UTF_8)
        Log.e("ViewModel", "Boot response: $decodedResponse")
        if (!decodedResponse.contains("boot.ok")) {
            _firmwareUpdateState.value = FirmwareUpdateState.BootError("Boot failed")
            return false
        }
        return true
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
            } finally {
                stopScanDevicesUseCase.invoke()
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
