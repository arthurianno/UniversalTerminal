package com.example.universalterminal.data.BLE

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.content.Context
import android.util.Log
import androidx.annotation.RequiresPermission
import com.example.universalterminal.data.managers.BluetoothConstants
import com.example.universalterminal.data.managers.BluetoothConstants.BOOT_MODE_START
import com.example.universalterminal.data.managers.BluetoothConstants.CONFIGURATION_CMD
import com.example.universalterminal.data.managers.BluetoothConstants.RAW_START_MARK
import com.example.universalterminal.data.managers.BluetoothConstants.WRITE_CMD
import com.example.universalterminal.domain.entities.BleDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import no.nordicsemi.android.ble.BleManager
import no.nordicsemi.android.ble.ConnectionPriorityRequest
import no.nordicsemi.android.ble.PhyRequest
import no.nordicsemi.android.ble.annotation.BondState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID
import javax.inject.Inject

class BleDeviceManager @Inject constructor(@ApplicationContext private val context: Context) : BleManager(context) {

    private var controlRequest: BluetoothGattCharacteristic? = null
    private var controlResponse: BluetoothGattCharacteristic? = null
    private var successfulOperationsCount: Int = 0

    private val _bondState = MutableStateFlow<Int>(BluetoothDevice.BOND_NONE)
    val bondState: Flow<Int> = _bondState.asStateFlow()

    override fun isRequiredServiceSupported(gatt: BluetoothGatt): Boolean {
        gatt.getService(UART_SERVICE_UUID)?.let { service ->
            controlRequest = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID)
            controlResponse = service.getCharacteristic(UART_TX_CHARACTERISTIC_UUID)
        }
        return controlRequest != null && controlResponse != null
    }

    @RequiresPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
    override fun initialize() {
        super.initialize()
        requestConnectionPriority(ConnectionPriorityRequest.CONNECTION_PRIORITY_HIGH).enqueue()
        requestMtu(247).fail { device, status ->
            Log.e("Ble", "Failed to request MTU: $status")

        }.enqueue()
        // Проверяем состояние бондинга
        if (bluetoothDevice?.bondState != BluetoothDevice.BOND_BONDED) {
            Log.d("Ble", "Initiating bonding for ${bluetoothDevice?.address}")
            createBond()
                .done {
                    _bondState.value = BluetoothDevice.BOND_BONDED
                    Log.d("Ble", "Bonding successful for ${bluetoothDevice?.address}")
                }
                .fail { device, status ->
                    _bondState.value = BluetoothDevice.BOND_NONE
                    Log.e("Ble", "Bonding failed for ${device.address}, status: $status")
                }
                .enqueue()
        } else {
            Log.d("Ble", "Device ${bluetoothDevice?.address} already bonded")
            _bondState.value = BluetoothDevice.BOND_BONDED
        }
    }

    override fun onServicesInvalidated() {
        super.onServicesInvalidated()
        controlRequest = null
        controlResponse = null
        _bondState.value = BluetoothDevice.BOND_NONE
        // Не отменяем регистрацию BroadcastReceiver, так как он нужен на протяжении жизни объекта
        close()
    }




    suspend fun connectToDevice(device: BleDevice): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(10_000) {
                connect(device.device)
                    .usePreferredPhy(PhyRequest.PHY_LE_1M_MASK)
                    .retry(3, 100)
                    .useAutoConnect(false)
                    .await()
            }
            true
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: Exception) {
            Log.e("BleManagerConnect", "Error connecting to device ${device.address}: ${e.message}")
            false
        }
    }
    suspend fun disconnectToDevice(): Boolean = withContext(Dispatchers.IO) {
        try {
            withTimeout(10_000) {
                disconnect().await()
            }
            true
        } catch (e: CancellationException) {
            close()
            throw e
        } catch (e: Exception) {
            Log.e("BleManagerDisconnect", "Error disconnecting from device: ${e.message}")
            false
        }
    }

    fun writeDataRaw(command: ByteArray): Flow<WriteResult> = flow {
        Log.d("BleControlManager", "Connection state: ${isConnected()}")

        if (!isConnected() || controlRequest == null || controlResponse == null) {
            emit(WriteResult.DeviceNotConnected)
            return@flow
        }

        val characteristic = controlRequest!!

        Log.d("BleControlManager", "Sending command: ${command.joinToString(" ") { it.toString(16).padStart(2, '0') }}")

        // Канал для получения ответа
        val responseChannel = Channel<ByteArray>(Channel.BUFFERED)

        // Устанавливаем callback для уведомлений
        setNotificationCallback(controlResponse).with { _, data ->
            val response = data.value ?: ByteArray(0)
            Log.d("BleControlManager", "Response received: ${response.joinToString(" ") { it.toString(16).padStart(2, '0') }}")
            responseChannel.trySend(response)
        }

        // Включаем уведомления
        enableNotifications(controlResponse!!).enqueue()

        try {
            // Отправляем команду
            val writeResult = writeCharacteristic(
                characteristic,
                command,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            ).await()

            Log.d("BleControlManager", "Command sent successfully")

            // Ожидаем ответа с таймаутом
            val response = withTimeoutOrNull(5000) {
                responseChannel.receive()
            }

            if (response != null) {
                emit(WriteResult.Success(response))
            } else {
                Log.e("BleControlManager", "Response timeout")
                emit(WriteResult.Error(Exception("Response timeout")))
            }
        } catch (e: Exception) {
            Log.e("BleControlManager", "Failed to send command: ${e.message}")
            emit(WriteResult.Error(e))
        } finally {
            disableNotifications(controlResponse!!).enqueue()
            responseChannel.close()
        }
    }.flowOn(Dispatchers.IO)

    fun sendApplyCommand(): Flow<Boolean> = flow {
        if (!isConnected || controlRequest == null) {
            Log.e("BleDeviceManager", "Device is not connected or controlRequest is null")
            emit(false)
            return@flow
        }

        val commandData = byteArrayOf(RAW_START_MARK, 0xEE.toByte()) // <0x21> <0xEE>
        val responseChannel = Channel<Boolean>(Channel.CONFLATED)

        setNotificationCallback(controlResponse).with { _, data ->
            val response = data.value ?: ByteArray(0)
            val success = response.size >= 2 && response[0] == 0x00.toByte() && response[1] == 0xEE.toByte()
            Log.d("BleDeviceManager", "Apply response: ${response.joinToString(" ") { it.toString(16) }}")
            responseChannel.trySend(success)
        }

        enableNotifications(controlResponse!!).enqueue()

        writeCharacteristic(
            controlRequest!!,
            commandData,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ).done {
            Log.d("BleDeviceManager", "Apply command sent")
        }.fail { _, status ->
            Log.e("BleDeviceManager", "Failed to send Apply command: $status")
            responseChannel.trySend(false)
        }.enqueue()

        val success = withTimeoutOrNull(5000) {
            responseChannel.receive()
        } ?: false

        disableNotifications(controlResponse!!).enqueue()
        responseChannel.close()

        emit(success)
    }.flowOn(Dispatchers.IO)



    suspend fun sendCommand(command: String): Flow<ByteArray> = flow {
        if (!isConnected || controlRequest == null) {
            Log.d("BleDeviceManager", "Connection state: $isConnected, controlRequest: $controlRequest")
            throw IllegalStateException("Device is not connected")
        }

        val responseChannel = Channel<ByteArray>(Channel.BUFFERED)

        setNotificationCallback(controlResponse).with { device, data ->
            val response = data.value ?: ByteArray(0)
            val decodedResponse = String(response, Charsets.UTF_8)
            Log.d("BleDeviceManager", "Notification received decoded: $decodedResponse")

            responseChannel.trySend(response)
        }

        Log.d("BleDeviceManager", "Enabling notifications...")
        enableNotifications(controlResponse!!).enqueue()

        try {
            if (command == "infoCommand") {
                // Send version command first
                Log.d("BleDeviceManager", "Writing command: version")
                writeCharacteristic(
                    controlRequest,
                    "version".toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ).enqueue()

                // Wait for the version response
                val versionResponse = responseChannel.receive()
                emit(versionResponse)

                // Send serial command next
                Log.d("BleDeviceManager", "Writing command: serial")
                writeCharacteristic(
                    controlRequest,
                    "serial".toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ).enqueue()

                // Continue collecting responses as normal
                for (response in responseChannel) {
                    val decodedResponse = String(response, Charsets.UTF_8)
                    Log.d("BleDeviceManager", "Emitting decoded response: $decodedResponse")
                    emit(response)
                }
            } else {
                // Original behavior for non-infoCommand
                Log.d("BleDeviceManager", "Writing characteristic: $command")
                writeCharacteristic(
                    controlRequest,
                    command.toByteArray(),
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                ).enqueue()

                Log.d("BleDeviceManager", "Starting to collect responses...")
                for (response in responseChannel) {
                    val decodedResponse = String(response, Charsets.UTF_8)
                    Log.d("BleDeviceManager", "Emitting decoded response: $decodedResponse")
                    emit(response)
                }
            }
        } finally {
            Log.d("BleDeviceManager", "Disabling notifications...")
            disableNotifications(controlResponse!!).enqueue()
            responseChannel.close()
        }
    }

    fun buildCommandPacket(
        startByte: Byte,
        command: Byte,
        address: Int,
        data: ByteArray
    ): ByteArray {
        return ByteArray(data.size + 7).apply {
            this[0] = startByte
            this[1] = command

            val addressBytes = ByteBuffer.allocate(4)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putInt(address)
                .array()

            System.arraycopy(addressBytes, 0, this, 2, 4)
            this[6] = data.size.toByte()
            System.arraycopy(data, 0, this, 7, data.size)
        }
    }


    fun writeFirmwareChunk(data: ByteArray, address: Int): Flow<Boolean> = flow {
        if (!isConnected) {
            Log.e("BleManager", "Device is not connected. Cannot write firmware chunk.")
            emit(false)
            return@flow
        }

        Log.d("BleManager", "Writing firmware chunk: Size: ${data.size}, Address: $address")

        var operationCompleted = false
        var operationSuccess = false

        val commandData = buildCommandPacket(
            startByte = BOOT_MODE_START,
            command = BluetoothConstants.FIRMWARE_CHUNK_CMD,
            address = address,
            data = data
        )

        Log.d("BleManager", "Command packet: ${commandData.joinToString(" ") { String.format("%02X", it) }}")

        // Создаем канал для синхронизации
        val responseChannel = Channel<Boolean>(Channel.CONFLATED)

        setNotificationCallback(controlResponse)
            .with { _, responseData ->
                val response = responseData.value ?: ByteArray(0)
                Log.d("BleManager", "Notification received: ${response.joinToString(" ") { String.format("%02X", it) }}")

                // Здесь можно добавить проверку успешности операции
                responseChannel.trySend(response.isNotEmpty())
            }

        enableNotifications(controlResponse).enqueue()

        // Отправляем команду
        writeCharacteristic(
            controlRequest,
            commandData,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ).enqueue()

        // Ожидаем ответ с таймаутом
        withTimeoutOrNull(5000) {
            operationSuccess = responseChannel.receive()
            operationCompleted = true
        }

        // Выключаем уведомления
        disableNotifications(controlResponse).enqueue()

        // Проверяем результат
        if (!operationCompleted) {
            Log.e("BleManager", "Firmware chunk write timeout")
            emit(false)
        } else {
            Log.d("BleManager", "Firmware chunk write ${if (operationSuccess) "successful" else "failed"}")
            emit(operationSuccess)
        }
    }.flowOn(Dispatchers.IO)

    fun writeConfiguration(buffer: ByteArray): Flow<Boolean> = flow {
        if (!isConnected) {
            Log.e("BleManager", "Device is not connected. Cannot write configuration.")
            emit(false)
            return@flow
        }

        var operationCompleted = false
        var operationSuccess = false

        val commandData = buildCommandPacket(
            startByte = BOOT_MODE_START,
            command = CONFIGURATION_CMD,
            address = 0,
            data = buffer
        )

        // Создаем канал для синхронизации
        val responseChannel = Channel<Boolean>(Channel.CONFLATED)

        setNotificationCallback(controlResponse)
            .with { _, responseData ->
                val response = responseData.value ?: ByteArray(0)
                Log.d("BleManager", "Configuration notification received: ${response.joinToString(" ") { String.format("%02X", it) }}")

                // Здесь можно добавить более точную проверку успешности ответа
                responseChannel.trySend(response.isNotEmpty())
            }

        enableNotifications(controlResponse).enqueue()

        // Отправляем команду конфигурации
        writeCharacteristic(
            controlRequest,
            commandData,
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        ).enqueue()

        // Ожидаем ответ с таймаутом
        withTimeoutOrNull(5000) {
            operationSuccess = responseChannel.receive()
            operationCompleted = true
        }

        // Выключаем уведомления
        disableNotifications(controlResponse).enqueue()

        // Проверяем результат
        if (!operationCompleted) {
            Log.e("BleManager", "Configuration write timeout")
            emit(false)
        } else {
            Log.d("BleManager", "Configuration write ${if (operationSuccess) "successful" else "failed"}")
            emit(operationSuccess)
        }
    }.flowOn(Dispatchers.IO)





    sealed class WriteResult {
        data class Success(val response: ByteArray) : WriteResult() // Теперь возвращаем ответ
        data class Error(val exception: Throwable) : WriteResult()
        object DeviceNotConnected : WriteResult()
        object CharacteristicNull : WriteResult()
    }

    companion object {
        private val UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_RX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E")
        private val UART_TX_CHARACTERISTIC_UUID =
            UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E")

    }


    enum class DeviceType {
        NORDIC,
        WCH,
        UNKNOWN
    }

}