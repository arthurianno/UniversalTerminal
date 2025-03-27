package com.example.universalterminal.domain.useCase

import com.example.universalterminal.domain.entities.BleDevice
import com.example.universalterminal.domain.repository.DeviceWorkingRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDeviceInformationUseCase @Inject constructor(private val deviceRepository: DeviceWorkingRepository)  {
    suspend operator fun invoke(bleDevice: BleDevice): Flow<BleDevice?> {
        return deviceRepository.getDeviceInformation(bleDevice)
    }

}