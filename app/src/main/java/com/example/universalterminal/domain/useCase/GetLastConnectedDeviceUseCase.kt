package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetLastConnectedDeviceUseCase @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(): Flow<BleDevice?>  {
        return deviceRepository.getLastConnectedDevice()

    }
}