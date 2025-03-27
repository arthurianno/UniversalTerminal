package com.example.universalterminal.domain.repository

import android.bluetooth.BluetoothDevice
import com.example.universalterminal.domain.entities.BleDevice
import kotlinx.coroutines.flow.Flow

interface BleRepository {
    suspend fun scanDevices(): Flow<Set<BleDevice>>
    suspend fun stopScanDevices(): Flow<Boolean>
    suspend fun connectToDevice(device: BleDevice): Flow<Boolean>
    suspend fun sendCommand(command: String): Flow<ByteArray>
    suspend fun disconnectFromDevice(): Flow<Boolean>
    suspend fun isConnected(): Flow<Boolean>
    suspend fun loadFirmware(command: ByteArray, fileSize: Int): Flow<Boolean>
    suspend fun loadConfiguration(command: ByteArray): Flow<Boolean>
    suspend fun processZipFiles(zipPath: String): Result<Pair<ByteArray, ByteArray>>
    suspend fun loadDfuFirmware(address: String, filePath: String): Flow<Boolean>
}