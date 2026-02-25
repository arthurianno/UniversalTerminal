package com.example.universalterminal.presentation.theme.ui

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.universalterminal.data.BLE.BleDeviceManager
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.useCase.CheckDeviceConnectionUseCase
import com.example.universalterminal.domain.useCase.ConnectToDeviceUseCase
import com.example.universalterminal.domain.useCase.GetDeviceInformationUseCase
import com.example.universalterminal.domain.useCase.GetDevicePasswordUseCase
import com.example.universalterminal.domain.useCase.GetLastConnectedDeviceUseCase
import com.example.universalterminal.domain.useCase.SendCommandUseCase
import com.example.universalterminal.presentation.theme.ui.screens.ConfigData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject

@HiltViewModel
class RawModeViewModel @Inject constructor(
    private val bleDeviceManager: BleDeviceManager,
    private val sendCommandUseCase: SendCommandUseCase,
    private val connectToDeviceUseCase: ConnectToDeviceUseCase,
    private val getDevicePasswordUseCase: GetDevicePasswordUseCase,
    private val isDeviceConnectedUseCase: CheckDeviceConnectionUseCase,
    private val getLastConnectedDeviceUseCase: GetLastConnectedDeviceUseCase,
    private val getDeviceInformationUseCase: GetDeviceInformationUseCase
) : ViewModel() {

    private val _readResponseFlow = MutableSharedFlow<List<ConfigData>>()
    val readResponseFlow: SharedFlow<List<ConfigData>> = _readResponseFlow

    private val _deviceInfo = MutableStateFlow<BleDevice?>(null)
    val deviceInfo: StateFlow<BleDevice?> = _deviceInfo

    private val _errorState = MutableStateFlow<String?>(null)
    val errorState: StateFlow<String?> = _errorState

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isRawModeActive = MutableStateFlow(false)
    val isRawModeActive: StateFlow<Boolean> = _isRawModeActive

    init {
        viewModelScope.launch {
            loadLastConnectedDevice()
        }
    }

    private fun loadLastConnectedDevice() {
        viewModelScope.launch {
            try {
                val device = getLastConnectedDeviceUseCase().first()
                device?.let {
                    val deviceWithInfo = getDeviceInformationUseCase(it).first()
                    _deviceInfo.value = deviceWithInfo
                    Log.d("RawViewModel", "Loaded device info: $deviceWithInfo")
                } ?: run {
                    _errorState.value = "No last connected device found"
                }
            } catch (e: Exception) {
                Log.e("RawViewModel", "Error loading device info", e)
                _errorState.value = "Failed to load last connected device: ${e.message}"
            }
        }
    }

    private suspend fun connectToDevice(): Boolean {
        if (_deviceInfo.value == null) {
            _errorState.value = "No device selected to connect"
            return false
        }

        return try {
            val connectionResult = connectToDeviceUseCase.invoke(_deviceInfo.value!!)
            if (connectionResult) {
                val password = getDevicePasswordUseCase.invoke(_deviceInfo.value!!.address).first()
                if (password == null) {
                    _errorState.value = "No password found for device"
                    return false
                }

                val response = sendCommandUseCase.invoke("pin.$password")
                val decodedResponse = String(response, Charsets.UTF_8)
                Log.d("RawModeViewModel", "PIN Response: $decodedResponse")

                if (decodedResponse.contains("pin.ok")) {
                    Log.d("RawModeViewModel", "PIN accepted")
                    // Reset raw mode status on new connection
                    _isRawModeActive.value = false
                    true
                } else {
                    _errorState.value = "Invalid PIN response: $decodedResponse"
                    false
                }
            } else {
                _errorState.value = "Connection failed"
                false
            }
        } catch (e: Exception) {
            Log.e("RawModeViewModel", "Connection error", e)
            _errorState.value = "Connection error: ${e.message}"
            false
        }
    }

    fun tryingToSendCommands(forModify: Boolean, modifiedData: List<ConfigData>? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (!isDeviceConnectedUseCase.invoke()) {
                    val connectionSuccess = connectToDevice()
                    if (!connectionSuccess) {
                        Log.e("RawModeViewModel", "Connection failed, stopping execution")
                        return@launch
                    }
                }
                Log.d("RawModeViewModel", "Proceeding to send multiple read commands")
                if (forModify) {
                    sendModifiedData(modifiedData!!)
                } else {
                    sendMultipleReadCommands()
                }

            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun sendMultipleReadCommands() {
        try {
            // Only send "setraw" if not already in raw mode
            if (!_isRawModeActive.value) {
                val setRawResponse = sendCommandUseCase.invoke("setraw")
                val decodedResponse = String(setRawResponse, Charsets.UTF_8)
                Log.d("RawModeViewModel", "Setraw response: $decodedResponse")
                if (decodedResponse.contains("setraw.ok")) {
                    _isRawModeActive.value = true // Mark as in raw mode
                } else {
                    _errorState.value = "Failed to set raw mode: $decodedResponse"
                    return
                }
            } else {
                Log.d("RawModeViewModel", "Device already in raw mode, skipping 'setraw'")
            }

            val commands = listOf(
                byteArrayOf(0x21, 0x81.toByte(), 0x00, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x10, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x20, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x30, 0x08),
                byteArrayOf(0x21, 0x81.toByte(), 0x38, 0x04),
                byteArrayOf(0x21, 0x81.toByte(), 0x3C, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x4C, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x5C, 0x10),
                byteArrayOf(0x21, 0x81.toByte(), 0x6C, 0x04),
                byteArrayOf(0x21, 0x81.toByte(), 0x70, 0x0C)
            )

            val allConfigData = mutableListOf<ConfigData>()
            for (command in commands) {
                bleDeviceManager.writeDataRaw(command).collect { result ->
                    when (result) {
                        is BleDeviceManager.WriteResult.Success -> {
                            val rawResponse = result.response.joinToString(" ") {
                                it.toString(16).padStart(2, '0')
                            }
                            Log.d("RawModeViewModel", "Raw response: $rawResponse")
                            val parsedData = parseResponse(result.response)
                            allConfigData.addAll(parsedData)
                            _readResponseFlow.emit(allConfigData.toList())
                        }

                        is BleDeviceManager.WriteResult.Error -> {
                            _errorState.value = "Write error: ${result.exception.message}"
                        }

                        BleDeviceManager.WriteResult.DeviceNotConnected -> {
                            _errorState.value = "Device disconnected during operation"
                            _isRawModeActive.value = false // Reset raw mode on disconnect
                        }

                        BleDeviceManager.WriteResult.CharacteristicNull -> {
                            _errorState.value = "BLE characteristic not found"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("RawModeViewModel", "Error in sendMultipleReadCommands", e)
            _errorState.value = "Error sending commands: ${e.message}"
        }
    }

    fun clearError() {
        _errorState.value = null
    }


    fun parseResponse(response: ByteArray): List<ConfigData> {
        val result = mutableListOf<ConfigData>()
        if (response.isEmpty() || response[0] != 0x00.toByte()) return emptyList()
        val cmd = response[1].toInt() and 0xFF
        if (cmd != 0x81) return emptyList()

        val address = response[2].toInt() and 0xFF
        val num = response[3].toInt() and 0xFF
        val data = response.drop(4).take(num)

        val configMap = mapOf(
            0x00 to Pair("Калибровка тока (0 мкА)", "FLOAT"),
            0x04 to Pair("Калибровка тока (2 мкА)", "FLOAT"),
            0x08 to Pair("Калибровка тока (10 мкА)", "FLOAT"),
            0x0C to Pair("Калибровка тока (20 мкА)", "FLOAT"),
            0x10 to Pair("Калибровка тока (30 мкА)", "FLOAT"),
            0x14 to Pair("Калибровка тока (40 мкА)", "FLOAT"),
            0x18 to Pair("Калибровка тока (60 мкА)", "FLOAT"),
            0x1C to Pair("Калибровка температуры (мВ)", "FLOAT"),
            0x20 to Pair("Значение R1 (Ом)", "UINT32"),
            0x24 to Pair("Калибровка Uref (UIC1101)", "UINT32_HEX"),
            0x28 to Pair("Калибровка Uw (UIC1101)", "UINT32_HEX"),
            0x2C to Pair("Калибровка температуры (°C x10)", "UINT32"),
            0x30 to Pair("Дата выпуска устройства (UNIX)", "TIMESTAMP"),
            0x34 to Pair("Калибровка напряжения питания", "FLOAT"),
            0x38 to Pair("Слово конфигурации устройства", "BITWISE"),
            0x3C to Pair("Имя выпускавшего оператора", "Char[]"),
            0x4C to Pair("Аппаратная версия устройства", "Char[]"),
            0x5C to Pair("Серийный номер устройства", "Char[]"),
            0x6C to Pair("Локальное смещение времени (UNIX)", "INT32"),
            0x70 to Pair("Зарезервированная область", "BYTE[]")
        )

        var offset = 0
        while (offset < data.size && offset < num) {
            val currentAddress = address + offset
            configMap[currentAddress]?.let { (name, type) ->
                val isEditable = currentAddress != 0x70
                val value = when (type) {
                    "FLOAT" -> {
                        if (data.size - offset >= 4) {
                            ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .float.toString()
                        } else "Invalid data"
                    }

                    "UINT32" -> {
                        if (data.size - offset >= 4) {
                            ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .int.toUInt().toString()
                        } else "Invalid data"
                    }

                    "UINT32_HEX" -> {
                        if (data.size - offset >= 4) {
                            val intValue = ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .int.toUInt()
                            "0x${intValue.toString(16).uppercase().padStart(8, '0')}"
                        } else "Invalid data"
                    }

                    "TIMESTAMP" -> {
                        if (data.size - offset >= 4) {
                            val timestamp = ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .int.toLong()
                            java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                                .format(java.util.Date(timestamp * 1000))
                        } else "Invalid data"
                    }

                    "BITWISE" -> {
                        if (data.size - offset >= 4) {
                            val intValue = ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .int.toUInt()
                            intValue.toString(2).padStart(32, '0')
                        } else "Invalid data"
                    }

                    "INT32" -> {
                        if (data.size - offset >= 4) {
                            ByteBuffer.wrap(data.drop(offset).take(4).toByteArray())
                                .order(ByteOrder.LITTLE_ENDIAN)
                                .int.toString()
                        } else "Invalid data"
                    }

                    "Char[]" -> {
                        if (data.size - offset >= 16) {
                            val bytes = data.drop(offset).take(16).toByteArray()
                            val str = String(bytes, charset("CP1251"))
                            val nullIndex = str.indexOf('\u0000') // Ищем \0
                            if (nullIndex >= 0) str.substring(
                                0,
                                nullIndex
                            ) else str // Обрезаем до \0
                        } else "Invalid data"
                    }

                    "BYTE[]" -> {
                        data.drop(offset).take(num - offset)
                            .joinToString(" ") { it.toString(16).padStart(2, '0') }
                    }

                    else -> "Unsupported type"
                }
                result.add(
                    ConfigData(
                        name = name,
                        address = "0x${currentAddress.toString(16).padStart(2, '0')}",
                        value = value,
                        type = type,
                        isEditable = isEditable
                    )
                )
                offset += when (type) {
                    "FLOAT", "UINT32", "UINT32_HEX", "TIMESTAMP", "BITWISE", "INT32" -> 4
                    "Char[]" -> 16
                    "BYTE[]" -> num - offset
                    else -> 0
                }
            } ?: run { offset++ }
        }
        return result
    }

    @SuppressLint("SimpleDateFormat")
    private fun sendModifiedData(modifiedData: List<ConfigData>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (!isDeviceConnectedUseCase.invoke()) {
                    val connectionSuccess = connectToDevice()
                    if (!connectionSuccess) {
                        Log.e("RawModeViewModel", "Connection failed, stopping execution")
                        return@launch
                    }
                }

                if (!_isRawModeActive.value) {
                    val setRawResponse = sendCommandUseCase.invoke("setraw")
                    val decodedResponse = String(setRawResponse, Charsets.UTF_8)
                    Log.d("RawModeViewModel", "Setraw response: $decodedResponse")
                    if (decodedResponse.contains("setraw.ok")) {
                        _isRawModeActive.value = true
                    } else {
                        _errorState.value = "Failed to set raw mode: $decodedResponse"
                        return@launch
                    }
                } else {
                    Log.d("RawModeViewModel", "Device already in raw mode, skipping 'setraw'")
                }

                for (data in modifiedData.filter { it.isModified }) {
                    val address = data.address.removePrefix("0x").toInt(16)
                    Log.d("RawModeViewModel", "Sending modified data for ${data.name} and $address")
                    val newValueBytes = when (data.type) {
                        "FLOAT" -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putFloat(data.newValue!!.toFloat()).array()

                        "UINT32" -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(data.newValue!!.toUInt().toInt()).array()

                        "UINT32_HEX" -> {
                            val decimalValue = data.newValue!!.toUInt()
                            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(decimalValue.toInt()).array()
                        }

                        "TIMESTAMP" -> {
                            val dateFormat = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
                            val date = dateFormat.parse(data.newValue!!)
                                ?: throw IllegalArgumentException("Invalid timestamp: ${data.newValue}")
                            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt((date.time / 1000).toInt()).array()
                        }

                        "BITWISE" -> {
                            val binaryString = data.newValue!!.replace(" ", "")
                            val intValue = binaryString.toUInt(2)
                            ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                                .putInt(intValue.toInt()).array()
                        }

                        "INT32" -> ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                            .putInt(data.newValue!!.toInt()).array()

                        "Char[]" -> {
                            val str = data.newValue!! + '\u0000' // Добавляем \0
                            val padded = str.padEnd(16, '\u0000') // Падим \0
                            padded.toByteArray(charset("CP1251")).copyOf(16)
                        }

                        else -> throw IllegalArgumentException("Unsupported type: ${data.type}")
                    }

                    val command = byteArrayOf(
                        0x21, // start
                        0x01, // cmd (Write)
                        address.toByte(),
                        newValueBytes.size.toByte()
                    ) + newValueBytes

                    bleDeviceManager.writeDataRaw(command).collect { result ->
                        when (result) {
                            is BleDeviceManager.WriteResult.Success -> {
                                Log.d("RawModeViewModel", "Write success for ${data.name}")
                            }

                            is BleDeviceManager.WriteResult.Error -> {
                                _errorState.value =
                                    "Write error for ${data.name}: ${result.exception.message}"
                                return@collect
                            }

                            else -> {
                                _errorState.value = "Unexpected error during write for ${data.name}"
                                return@collect
                            }
                        }
                    }
                }

                bleDeviceManager.sendApplyCommand().collect { success ->
                    if (success) {
                        Log.d("RawModeViewModel", "Apply command sent successfully")
                        _readResponseFlow.emit(emptyList())
                    } else {
                        _errorState.value = "Failed to apply changes"
                    }
                }
            } catch (e: Exception) {
                Log.e("RawModeViewModel", "Error sending modified data", e)
                _errorState.value = "Error: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }
}
