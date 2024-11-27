package com.example.universalterminal.data.managers

import android.annotation.SuppressLint
import android.util.Log
import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class BleRepositoryImpl @Inject constructor (private val bleScanner: BleScanner): BleRepository {
    @SuppressLint("MissingPermission")
    override suspend fun scanDevices(): Flow<List<BleDevice>> {
        bleScanner.startScan()
        Log.i("BleRepository", "Scanning started")
        Log.i("BleRepository", "Device to rep: ${bleScanner.devicesFlow.value}")
        return bleScanner.devicesFlow

    }

}