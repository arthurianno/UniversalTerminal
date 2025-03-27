package com.example.universalterminal.domain.repository

import com.example.universalterminal.domain.entities.BleDevice
import kotlinx.coroutines.flow.Flow

interface DeviceWorkingRepository {

    suspend fun saveLastConnectedDevice(device: BleDevice)
    suspend fun getLastConnectedDevice(): Flow<BleDevice?>
    suspend fun saveDevicePassword(deviceAddress: String, password: String)
    suspend fun getDevicePassword(deviceAddress: String): Flow<String?>
    suspend fun getDeviceInformation(device: BleDevice):Flow<BleDevice?>
    suspend fun saveDeviceInfo(device: BleDevice)
}