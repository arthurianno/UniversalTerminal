package com.example.universalterminal.data.managers

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.example.universalterminal.data.BLE.BleDeviceManager
import com.example.universalterminal.data.managers.BluetoothConstants.CONFIGURATION_SIZE
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.BleRepository
import com.example.universalterminal.presentation.theme.ui.ScanMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import no.nordicsemi.android.dfu.DfuServiceInitiator
import no.nordicsemi.android.dfu.DfuServiceListenerHelper
import java.io.File
import java.io.FileNotFoundException
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.math.min

class BleRepositoryImpl @Inject constructor(
    private val bleScanner: BleScanner,
    private val bleDeviceManager: BleDeviceManager,
    @ApplicationContext private val context: Context
): BleRepository {
    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override suspend fun scanDevices(scanMode: ScanMode): Flow<Set<BleDevice>> {
        stopScanDevices()
        bleScanner.startScan(scanMode)
        Log.i("BleRepository", "Scanning started")
        Log.i("BleRepository", "Device to rep: ${bleScanner.devicesFlow.value}")
        return bleScanner.devicesFlow

    }



    override suspend fun stopScanDevices(): Flow<Boolean> {
        bleScanner.stopScan()
        return bleScanner.isScanning
    }

    @RequiresApi(Build.VERSION_CODES.O)
    @SuppressLint("MissingPermission")
    override suspend fun connectToDevice(device: BleDevice): Flow<Boolean> = flow {
        // Проверяем, связано ли устройство
        val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val bondedDevice = bondedDevices.find { it.address == device.address }

        if (bondedDevice != null) {
            Log.i("BleRepository", "Connecting to bonded device ${device.address}")
            val success = bleDeviceManager.connectToDevice(device.copy(device = bondedDevice))
            emit(success)
        } else {
            Log.i("BleRepository", "Device ${device.address} not bonded, attempting direct connection")
            // Создаем BluetoothDevice из адреса
            val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
            if (bluetoothDevice == null) {
                Log.e("BleRepository", "Failed to create BluetoothDevice for ${device.address}")
                emit(false)
                return@flow
            }

            // Пытаемся инициировать связывание
            if (bluetoothDevice.bondState == BluetoothDevice.BOND_NONE) {
                Log.i("BleRepository", "Initiating bonding for ${device.address}")
                val bondResult = bluetoothDevice.createBond()
                if (!bondResult) {
                    Log.e("BleRepository", "Failed to initiate bonding for ${device.address}")
                }
            }

            // Пытаемся подключиться напрямую
            val success = bleDeviceManager.connectToDevice(device.copy(device = bluetoothDevice))
            if (success) {
                Log.i("BleRepository", "Direct connection successful for ${device.address}")
                emit(true)
            } else {
                Log.w("BleRepository", "Direct connection failed, falling back to scanning")
                // Если подключение не удалось, запускаем сканирование
                val scannedDevices = scanDevices(ScanMode.AGGRESSIVE).last()
                val targetDevice = scannedDevices.find { it.address == device.address }
                if (targetDevice != null) {
                    Log.i("BleRepository", "Device ${device.address} found, connecting and initiating bonding")
                    // Повторно инициируем bonding, если нужно
                    targetDevice.device?.let { btDevice ->
                        if (btDevice.bondState == BluetoothDevice.BOND_NONE) {
                            btDevice.createBond()
                        }
                    }
                    val success = bleDeviceManager.connectToDevice(targetDevice)
                    emit(success)
                } else {
                    Log.e("BleRepository", "Device ${device.address} not found during scan")
                    emit(false)
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sendCommand(command: String): Flow<ByteArray> {
        val response = bleDeviceManager.sendCommand(command)
        Log.i("BleRepository", "Response: ${response.toString()}")
        return response
    }

    override suspend fun disconnectFromDevice(): Flow<Boolean> {
        val disconnection = bleDeviceManager.disconnectToDevice()
        return if (disconnection){
            flowOf(true)
        }else {
            flowOf(false)
        }
    }

    override suspend fun isConnected(): Flow<Boolean> {
        return flowOf(bleDeviceManager.isConnected)
    }

    override suspend fun loadFirmware(command: ByteArray, fileSize: Int): Flow<Boolean> = flow {
        if (!bleDeviceManager.isConnected) {
            emit(false)
            return@flow
        }
        var position = 0
        var remainingSize = fileSize

        while (remainingSize > 0) {
            val chunkSize = min(BluetoothConstants.CHUNK_SIZE, remainingSize)
            val buffer = ByteArray(chunkSize)
            System.arraycopy(command, position, buffer, 0, chunkSize)

            // Ожидаем результат для каждого чанка
            val chunkResult = bleDeviceManager.writeFirmwareChunk(buffer, position).last()

            if (!chunkResult) {
                Log.e("BleRepository", "Firmware chunk write failed")
                emit(false)
                return@flow
            }

            position += chunkSize
            remainingSize -= chunkSize

            Log.i("BleRepository", "Firmware chunk written")
            Log.i("BleRepository", "Firmware remainingSize: $remainingSize")
            Log.i("BleRepository", "Firmware position: $position")
        }

        Log.i("BleRepository", "Firmware loading completed")
        emit(true)
    }.flowOn(Dispatchers.IO)

    override suspend fun loadDfuFirmware(address: String, filePath: String): Flow<Boolean> = flow {
        try {
            val result = suspendCancellableCoroutine<Boolean> { continuation ->
                val listener = object : DfuLogger() {
                    override fun onDeviceConnecting(deviceAddress: String) {
                        Log.d("DFU", "Connecting to device: $deviceAddress")
                    }
                    override fun onDeviceConnected(deviceAddress: String) {
                        Log.d("DFU", "Device connected: $deviceAddress")
                    }

                    override fun onDfuProcessStarting(deviceAddress: String) {
                        Log.d("DFU", "DFU process starting: $deviceAddress")
                    }

                    override fun onProgressChanged(
                        deviceAddress: String,
                        percent: Int,
                        speed: Float,
                        avgSpeed: Float,
                        currentPart: Int,
                        partsTotal: Int
                    ) {
                        Log.d("DFU", "Progress: $percent%, Speed: $speed, Part: $currentPart/$partsTotal")
                    }
                    override fun onDfuCompleted(deviceAddress: String) {
                        super.onDfuCompleted(deviceAddress)
                        Log.i("BleService", "DFU COMPLETED")
                        DfuServiceListenerHelper.unregisterProgressListener(context, this)
                        continuation.resume(true)
                    }

                    override fun onError(
                        deviceAddress: String,
                        error: Int,
                        errorType: Int,
                        message: String?
                    ) {
                        super.onError(deviceAddress, error, errorType, message)
                        DfuServiceListenerHelper.unregisterProgressListener(context, this)
                        continuation.resume(false)
                    }
                }

                try {
                    DfuServiceListenerHelper.registerProgressListener(context, listener)
                    val starter = DfuServiceInitiator(address).apply {
                        setDeviceName("Dfu")
                        setKeepBond(false)
                        setForceDfu(true)
                        setKeepBond(true)
                        setNumberOfRetries(1)
                        setForceScanningForNewAddressInLegacyDfu(false)
                        setPrepareDataObjectDelay(400L)
                        setRebootTime(0)
                        setScanTimeout(2000)
                        setZip(filePath)
                    }
                    starter.start(context, BooterDfuService::class.java)
                } catch (e: Exception) {
                    Log.e("BleRepo", e.message.toString())
                    DfuServiceListenerHelper.unregisterProgressListener(context, listener)
                    continuation.resume(false)
                }
            }

            emit(result)
        } catch (e: Exception) {
            Log.e("BleService", "DFU timeout or error: ${e.message}")
            emit(false)
        }
    }

    override suspend fun loadConfiguration(command: ByteArray): Flow<Boolean> = flow {
        val buffer = ByteArray(CONFIGURATION_SIZE)
        System.arraycopy(command, 0, buffer, 0, CONFIGURATION_SIZE)

        val result = bleDeviceManager.writeConfiguration(buffer)
        Log.i("BleRepository", "Configuration result: ${result.last()}")
        emitAll(result)

        //bleDeviceManager.disconnect().await()
    }.flowOn(Dispatchers.IO)


    override suspend fun processZipFiles(zipPath: String): Result<Pair<ByteArray, ByteArray>> =
            withContext(Dispatchers.IO) {
                runCatching {
                    val zipFile = File(zipPath)
                    Log.i("FileProcessing", zipFile.toString())
                    if (!zipFile.exists()) {
                       Log.e("FileProcessing","Required files not found in ZIP")
                        throw FileNotFoundException("ZIP file not found at $zipPath")
                    }

                    var binData: ByteArray? = null
                    var datData: ByteArray? = null

                    ZipFile(zipFile).use { zip ->
                        Log.i("Zip","Prepare for readBytes")
                        zip.entries().asSequence().forEach { entry ->
                            when {
                                entry.name.endsWith(".bin") -> {
                                    binData = zip.getInputStream(entry).use { it.readBytes() }
                                    Log.i("FileProcessing", binData.toString())
                                    Log.i("FileProcessing","readBytes $binData")
                                }
                                entry.name.endsWith(".dat") -> {
                                    datData = zip.getInputStream(entry).use { it.readBytes() }
                                    Log.i("FileProcessing","readBytes $datData")
                                }
                            }
                        }
                    }

                    if (binData == null || datData == null) {
                        Log.i("FileProcessing","NULL")
                        Log.i("FileProcessing","Required files not found in ZIP")
                        throw IllegalStateException("Required files not found in ZIP")


                    }

                    Pair(binData!!, datData!!)
                }
            }
    }




object BluetoothConstants {
    const val CHUNK_SIZE = 240
    const val CONFIGURATION_SIZE = 16
    const val BOOT_MODE_START: Byte = 0x24
    const val FIRMWARE_CHUNK_CMD: Byte = 0x01
    const val CONFIGURATION_CMD: Byte = 0x04
    const val WRITE_CMD: Byte = 0x01
    const val RAW_ASK: Byte = 0x00
    const val RAW_START_MARK: Byte = 0x21
    const val RAW_RD: Byte = 0x81.toByte()
    const val READ_STATE_CMD: Byte = 0x81.toByte()
}