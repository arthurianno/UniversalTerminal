package com.example.universalterminal.data.managers

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.universalterminal.data.BLE.BleDeviceManager
import com.example.universalterminal.data.managers.BluetoothConstants.CONFIGURATION_SIZE
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.ScanMode
import com.example.universalterminal.domain.repository.BleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    private val bluetoothAdapter: BluetoothAdapter?
        get() = context.getSystemService(BluetoothManager::class.java)?.adapter

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun scanDevices(scanMode: ScanMode): StateFlow<Set<BleDevice>> {
        stopScanDevices()
        bleScanner.startScan(scanMode)
        Log.i("BleRepository", "Scanning started")
        Log.i("BleRepository", "Devices: ${bleScanner.devicesFlow.value}")
        return bleScanner.devicesFlow
    }

    override suspend fun stopScanDevices() {
        bleScanner.stopScan()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun connectToDevice(device: BleDevice): Boolean {
        if (!hasBluetoothConnectPermission()) {
            Log.e("BleRepository", "Missing BLUETOOTH_CONNECT permission for connect operation")
            return false
        }

        val bondedDevices = bluetoothAdapter?.bondedDevices ?: emptySet()
        val bondedDevice = bondedDevices.find { it.address == device.address }

        if (bondedDevice != null) {
            Log.i("BleRepository", "Connecting to bonded device ${device.address}")
            return bleDeviceManager.connectToDevice(bondedDevice)
        }

        Log.i("BleRepository", "Device ${device.address} not bonded, attempting direct connection")
        val bluetoothDevice = bluetoothAdapter?.getRemoteDevice(device.address)
        if (bluetoothDevice == null) {
            Log.e("BleRepository", "Failed to create BluetoothDevice for ${device.address}")
            return false
        }

        if (bluetoothDevice.bondState == BluetoothDevice.BOND_NONE) {
            Log.i("BleRepository", "Initiating bonding for ${device.address}")
            val bondResult = bluetoothDevice.createBond()
            if (!bondResult) {
                Log.e("BleRepository", "Failed to initiate bonding for ${device.address}")
            }
        }

        val success = bleDeviceManager.connectToDevice(bluetoothDevice)
        if (success) {
            Log.i("BleRepository", "Direct connection successful for ${device.address}")
            return true
        }

        Log.w("BleRepository", "Direct connection failed, falling back to scanning")
        return try {
            val scannedDevices = scanDevices(ScanMode.AGGRESSIVE)
            val targetDevice: BleDevice? = withTimeoutOrNull(10_000) {
                var foundDevice: BleDevice? = null
                while (foundDevice == null) {
                    foundDevice = scannedDevices.value.find { it.address == device.address }
                    if (foundDevice == null) {
                        delay(300)
                    }
                }
                foundDevice
            }

            if (targetDevice == null) {
                Log.e("BleRepository", "Device ${device.address} not found during scan")
                false
            } else {
                val targetBluetoothDevice = bluetoothAdapter?.getRemoteDevice(targetDevice.address)
                if (targetBluetoothDevice == null) {
                    false
                } else {
                    if (targetBluetoothDevice.bondState == BluetoothDevice.BOND_NONE) {
                        targetBluetoothDevice.createBond()
                    }
                    bleDeviceManager.connectToDevice(targetBluetoothDevice)
                }

            }
        } finally {
            stopScanDevices()
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED
    }

    override suspend fun sendCommand(command: String): ByteArray {
        return bleDeviceManager.sendCommand(command)
    }

    override suspend fun disconnectFromDevice(): Boolean {
        return bleDeviceManager.disconnectToDevice()
    }

    override suspend fun isConnected(): Boolean {
        return bleDeviceManager.isConnected
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

                    val firmwareData = binData ?: throw IllegalStateException("BIN file missing in ZIP")
                    val metadataData = datData ?: throw IllegalStateException("DAT file missing in ZIP")
                    Pair(firmwareData, metadataData)
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
