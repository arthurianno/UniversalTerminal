package com.example.universalterminal.domain.repository

import android.bluetooth.BluetoothDevice
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.ScanMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BleRepository {
    suspend fun scanDevices(scanMode: ScanMode): StateFlow<Set<BleDevice>>
    suspend fun stopScanDevices()
    suspend fun connectToDevice(device: BleDevice): Boolean
    suspend fun sendCommand(command: String): ByteArray
    suspend fun disconnectFromDevice(): Boolean
    suspend fun isConnected(): Boolean
    suspend fun loadFirmware(command: ByteArray, fileSize: Int): Flow<Boolean>
    suspend fun loadConfiguration(command: ByteArray): Flow<Boolean>
    suspend fun processZipFiles(zipPath: String): Result<Pair<ByteArray, ByteArray>>
    suspend fun loadDfuFirmware(address: String, filePath: String): Flow<Boolean>
}
