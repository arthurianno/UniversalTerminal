package com.example.universalterminal.domain.repository

import com.example.universalterminal.domain.entities.BleDevice
import kotlinx.coroutines.flow.Flow

interface BleRepository {
    suspend fun scanDevices(): Flow<List<BleDevice>>
}