package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import javax.inject.Inject

class StopScanDeviceUseCase @Inject constructor(private val repository: BleRepository)  {
    suspend operator fun invoke() {
        repository.stopScanDevices()
    }

}
