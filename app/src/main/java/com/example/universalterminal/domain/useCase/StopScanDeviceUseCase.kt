package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.repository.BleRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class StopScanDeviceUseCase @Inject constructor(private val repository: BleRepository)  {
    suspend operator fun invoke() : Flow<Boolean> {
        return repository.stopScanDevices()
    }

}