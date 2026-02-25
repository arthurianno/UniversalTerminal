package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.entities.ScanMode
import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

class ScanDevicesUseCase @Inject constructor(private val repository: BleRepository) {
    suspend operator fun invoke(scanMode: ScanMode): StateFlow<Set<BleDevice>> {
        return repository.scanDevices(scanMode)
    }
}
