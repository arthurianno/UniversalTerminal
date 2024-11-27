package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ScanDevicesUseCase @Inject constructor(private val repository: BleRepository) {
    suspend operator fun invoke(): Flow<List<BleDevice>> {
        return repository.scanDevices()
    }
}